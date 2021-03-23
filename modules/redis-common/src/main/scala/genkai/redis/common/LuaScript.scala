package genkai.redis.common

/*
Key updates should be executed as an atomic operation. Usually, it can be achieved using `multi` command.
The problem is that some redis client libraries does not natively support thread-safety for `multi` command
and force users to control it by their own.
In order to simplify the situation and also to be able to use all client libraries' features we can use lua scripts.
Scripts will be loaded only once per RateLimiter instance and then executed as an atomic operation.
 */
object LuaScript {

  /**
   * args: key, maxTokens, current_timestamp, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - unused tokens
   */
  val tokenBucketAcquire: String =
    """
      |local maxAmount = tonumber(ARGV[1]);
      |local currentTimestamp = tonumber(ARGV[2]);
      |local refillAmount = tonumber(ARGV[3]);
      |local refillTime = tonumber(ARGV[4]);
      |local isExists = redis.call('EXISTS', KEYS[1]);
      |if isExists == 0 then redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp); end;
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime');
      |local lastRefillTime = tonumber(current[2]);
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime);
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes);
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp);
      |end;
      |local refilled = redis.call('HGET', KEYS[1], 'tokens');
      |local value = math.max(0, refilled - 1);
      |redis.call('HSET', KEYS[1], 'tokens', value);
      |return refilled;     
      |""".stripMargin

  /**
   * args: key, maxTokens, current_timestamp, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - unused tokens
   */
  val tokenBucketPermissions: String =
    """
      |local maxAmount = tonumber(ARGV[1]);
      |local currentTimestamp = tonumber(ARGV[2]);
      |local refillAmount = tonumber(ARGV[3]);
      |local refillTime = tonumber(ARGV[4]);
      |local isExists = redis.call('EXISTS', KEYS[1]);
      |if isExists == 0 then redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp); end;
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime');
      |local lastRefillTime = tonumber(current[2]);
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime);
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes);
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp);
      |end;
      |return redis.call('HGET', KEYS[1], 'tokens');     
      |""".stripMargin

  /**
   * args: key, ttl
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - acquired permissions
   */
  val fixedWindowAcquire: String =
    """
      |local counter = redis.call('INCR', KEYS[1]);
      |redis.call('EXPIRE', KEYS[1], ARGV[1]);
      |return counter;
      |""".stripMargin

  /**
   * args: key
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - acquired permissions
   */
  val fixedWindowPermissions: String =
    """
      |return redis.call('GET', KEYS[1]);
      |""".stripMargin

  /**
   * input: key, current_timestamp, window, ttl
   * key format: sliding_window:<key>
   * @return - acquired permissions
   */
  val slidingWindowAcquire: String =
    """
      |local expiredValues = ARGV[1] - ARGV[2];
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, expiredValues);
      |local counter = redis.call('ZCARD', KEYS[1]);
      |redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1]);
      |redis.call('EXPIRE', KEYS[1], ARGV[3]);
      |return counter;
      |""".stripMargin

  /**
   * input: key, current_timestamp, window
   * key format: sliding_window:<key>
   * @return - acquired permissions
   */
  val slidingWindowPermissions: String =
    """
      |local expiredValues = ARGV[1] - ARGV[2];
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, expiredValues);
      |return redis.call('ZCARD', KEYS[1]);
      |""".stripMargin
}
