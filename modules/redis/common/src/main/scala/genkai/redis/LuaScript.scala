package genkai.redis

/*
Key updates should be executed as an atomic operation. Usually, it can be achieved using `multi` command.
The problem is that some redis client libraries does not natively support thread-safety for `multi` command
and force users to control it by their own.
In order to simplify the situation and also to be able to use all client libraries' features we can use lua scripts.
Scripts will be loaded only once per RateLimiter instance and then executed as an atomic operation.
 */
object LuaScript {
  //todo: add cost based logic for fixed and sliding windows

  /**
   * args: key, current_timestamp, cost, maxTokens, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - 1 if token acquired, 0 - otherwise
   */
  val tokenBucketAcquire: String =
    """
      |local currentTimestamp = tonumber(ARGV[1]);
      |local cost = tonumber(ARGV[2]);
      |local maxAmount = tonumber(ARGV[3]);
      |local refillAmount = tonumber(ARGV[4]);
      |local refillTime = tonumber(ARGV[5]);
      |local isExists = redis.call('EXISTS', KEYS[1]);
      |
      |if isExists == 0 then redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp); end;
      |
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime');
      |local lastRefillTime = tonumber(current[2]);
      |
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime);
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes);
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp);
      |end;
      |
      |local refilled = redis.call('HGET', KEYS[1], 'tokens');
      |local remaining = refilled - cost
      |if remaining >= 0 then
      |    redis.call('HSET', KEYS[1], 'tokens', remaining);
      |    return 1;
      |else 
      |    return 0;
      |end;  
      |""".stripMargin

  /**
   * args: key, current_timestamp, maxTokens, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - unused tokens
   */
  val tokenBucketPermissions: String =
    """
      |local currentTimestamp = tonumber(ARGV[1]);
      |local maxAmount = tonumber(ARGV[2]);
      |local refillAmount = tonumber(ARGV[3]);
      |local refillTime = tonumber(ARGV[4]);
      |local isExists = redis.call('EXISTS', KEYS[1]);
      |
      |if isExists == 0 then redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp); end;
      |
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime');
      |local lastRefillTime = tonumber(current[2]);
      |
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime);
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes);
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp);
      |end;
      |
      |return tonumber(redis.call('HGET', KEYS[1], 'tokens'));     
      |""".stripMargin

  /**
   * args: key, cost, maxTokens, ttl
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - 1 if token acquired, 0 - otherwise
   */
  val fixedWindowAcquire: String =
    """
      |local cost = tonumber(ARGV[1]);
      |local maxTokens = tonumber(ARGV[2]);
      |local ttl = tonumber(ARGV[3]);
      |
      |local current = redis.call('GET', KEYS[1]) or 0;
      |
      |if maxTokens - current - cost >= 0 then
      |    redis.call('INCRBY', KEYS[1], cost);
      |    redis.call('EXPIRE', KEYS[1], ttl);
      |    return 1;
      |else
      |    redis.call('EXPIRE', KEYS[1], ttl);
      |    return 0;
      |end;
      |""".stripMargin

  /**
   * args: key, maxTokens
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - permissions
   */
  val fixedWindowPermissions: String =
    """
      |local maxTokens = tonumber(ARGV[1]);
      |local current = redis.call('GET', KEYS[1]);
      |local used = current and tonumber(current) or 0;
      |
      |return math.max(0, maxTokens - used);
      |""".stripMargin

  /**
   * input: key, current_timestamp, maxTokens, window, ttl
   * key format: sliding_window:<key>
   * @return - 1 if token acquired, 0 - otherwise
   */
  val slidingWindowAcquire: String =
    """
      |local currentTimestamp = tonumber(ARGV[1]);
      |local maxTokens = tonumber(ARGV[2]);
      |local window = tonumber(ARGV[3]);
      |local ttl = tonumber(ARGV[4]);
      |local expiredValues = currentTimestamp - window;
      |
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, expiredValues);
      |
      |local cost = redis.call('ZCARD', KEYS[1]) + 1;
      |local remaining = maxTokens - cost
      |if remaining >= 0 then
      |    redis.call('ZADD', KEYS[1], currentTimestamp, currentTimestamp);
      |    redis.call('EXPIRE', KEYS[1], ttl);
      |    return 1;
      |else 
      |    redis.call('EXPIRE', KEYS[1], ttl);
      |    return 0;
      |end;
      |""".stripMargin

  /**
   * input: key, current_timestamp, maxTokens, window
   * key format: sliding_window:<key>
   * @return - permissions
   */
  val slidingWindowPermissions: String =
    """
      |local currentTimestamp = tonumber(ARGV[1]);
      |local maxTokens = tonumber(ARGV[2]);
      |local window = tonumber(ARGV[3]);
      |local expiredValues = currentTimestamp - window;
      |
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, expiredValues);
      |
      |local used = redis.call('ZCARD', KEYS[1]);
      |
      |return math.max(0, maxTokens - used);
      |""".stripMargin
}
