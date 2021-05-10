package genkai.redis

/*
Key updates should be executed as an atomic operation. Usually, it can be achieved using `multi` command.
The problem is that some redis client libraries does not natively support thread-safety for `multi` command
and force users to control it by their own.
In order to simplify the situation and also to be able to use all client libraries' features we can use lua scripts.
Scripts will be loaded only once per RateLimiter instance and then executed as an atomic operation.
 */
object LuaScript {

  /**
   * args: key, current_timestamp, cost, maxTokens, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - 1 if token acquired, 0 - otherwise
   */
  val tokenBucketAcquire: String =
    """
      |local currentTimestamp = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxAmount = tonumber(ARGV[3])
      |local refillAmount = tonumber(ARGV[4])
      |local refillTime = tonumber(ARGV[5])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp)
      |end
      |
      |local hw = tonumber(redis.call('HGET', KEYS[1], 'hw') or currentTimestamp)
      |if currentTimestamp < hw then
      |  return 0
      |end
      |
      |redis.call('HSET', KEYS[1], 'hw', currentTimestamp)
      |
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime')
      |local lastRefillTime = tonumber(current[2])
      |
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime)
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes)
      |    redis.call('HSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp)
      |end
      |
      |local refilled = redis.call('HGET', KEYS[1], 'tokens')
      |local remaining = refilled - cost
      |if remaining >= 0 then
      |    redis.call('HSET', KEYS[1], 'tokens', remaining)
      |    return 1
      |else 
      |    return 0
      |end
      |""".stripMargin

  /**
   * args: key, current_timestamp, maxTokens, refillAmount, refillTime
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - unused tokens
   */
  val tokenBucketPermissions: String =
    """
      |local currentTimestamp = tonumber(ARGV[1])
      |local maxAmount = tonumber(ARGV[2])
      |local refillAmount = tonumber(ARGV[3])
      |local refillTime = tonumber(ARGV[4])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  redis.call('HMSET', KEYS[1], 'tokens', maxAmount, 'lastRefillTime', currentTimestamp) 
      |end
      |
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime')
      |local lastRefillTime = tonumber(current[2])
      |
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime)
      |    local refill = math.min(maxAmount, current[1] + refillAmount * refillTimes)
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp)
      |end
      |
      |return tonumber(redis.call('HGET', KEYS[1], 'tokens'))
      |""".stripMargin

  /**
   * args: key, windowTs, cost, maxTokens, ttl
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - 1 if token acquired, 0 - otherwise
   */
  val fixedWindowAcquire: String =
    """
      |local windowStartTs = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxTokens = tonumber(ARGV[3])
      |local ttl = tonumber(ARGV[4])
      |
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  redis.call('HMSET', KEYS[1], 'usedTokens', 0, 'hw', windowStartTs)
      |end
      |
      |local hw = tonumber(redis.call('HGET', KEYS[1], 'hw'))
      |
      |if windowStartTs < hw then
      |  return 0
      |end
      |
      |local current = redis.call('HGET', KEYS[1], 'usedTokens')
      |
      |if maxTokens - current - cost >= 0 then
      |    redis.call('HINCRBY', KEYS[1], 'usedTokens', cost)
      |    redis.call('EXPIRE', KEYS[1], ttl)
      |    return 1
      |else
      |    redis.call('EXPIRE', KEYS[1], ttl)
      |    return 0
      |end
      |""".stripMargin

  /**
   * args: key, maxTokens
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - permissions
   */
  val fixedWindowPermissions: String =
    """
      |local maxTokens = tonumber(ARGV[1])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  return maxTokens
      |else
      |  local used = redis.call('HGET', KEYS[1], 'usedTokens')
      |  used = used and tonumber(used) or 0
      |  return math.max(0, maxTokens - used)
      |end
      |""".stripMargin

  /**
   * input: key, currentTimestamp, cost, maxTokens, windowSize, precision, ttl
   * key format: sliding_window:<key>
   * @return - 1 if token acquired, 0 - otherwise
   */
  // ref: https://www.dr-josiah.com/2014/11/introduction-to-rate-limiting-with_26.html
  val slidingWindowAcquire: String =
    """
      |local key = KEYS[1]
      |local currentTimestamp = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxTokens = tonumber(ARGV[3])
      |local windowSize = tonumber(ARGV[4])
      |local precision = tonumber(ARGV[5])
      |local ttl = tonumber(ARGV[6])
      |
      |local blocks = math.ceil(windowSize / precision)
      |local saved = {}
      |
      |saved.blockId = math.floor(currentTimestamp / precision)
      |saved.trimBefore = saved.blockId - blocks + 1
      |saved.countKey = windowSize .. ':' .. precision .. ':'
      |saved.tsKey = saved.countKey .. 'o'
      |
      |local oldTs = redis.call('HGET', key, saved.tsKey)
      |oldTs = oldTs and tonumber(oldTs) or saved.trimBefore
      |if oldTs > currentTimestamp then
      |  return 0
      |end
      |
      |local decrement = 0
      |local deletion = {}
      |local trim = math.min(saved.trimBefore, oldTs + blocks)
      |
      |for oldBlock = oldTs, trim - 1 do
      |  local bKey = saved.countKey .. oldBlock
      |  local bCount = redis.call('HGET', key, bKey)
      |  if bCount then
      |    decrement = decrement + tonumber(bCount)
      |    table.insert(deletion, bKey)
      |  end
      |end
      |
      |local cur
      |if #deletion > 0 then
      |  redis.call('HDEL', key, unpack(deletion))
      |  cur = redis.call('HINCRBY', key, saved.countKey, -decrement)
      |else
      |  cur = redis.call('HGET', key, saved.countKey)
      |end
      |
      |if tonumber(cur or '0') + cost > maxTokens then
      |  return 0
      |end
      |
      |redis.call('HSET', key, saved.tsKey, saved.trimBefore)
      |redis.call('HINCRBY', key, saved.countKey, cost)
      |redis.call('HINCRBY', key, saved.countKey .. saved.blockId, cost)
      |
      |redis.call('EXPIRE', key, ttl)
      |
      |return 1
      |""".stripMargin

  /**
   * input: key, currentTimestamp, maxTokens, windowSize, precision
   * key format: sliding_window:<key>
   * @return - permissions
   */
  val slidingWindowPermissions: String =
    """
      |local key = KEYS[1]
      |local currentTimestamp = tonumber(ARGV[1])
      |local maxTokens = tonumber(ARGV[2])
      |local windowSize = tonumber(ARGV[3])
      |local precision = tonumber(ARGV[4])
      |
      |local blocks = math.ceil(windowSize / precision)
      |local saved = {}
      |
      |saved.blockId = math.floor(currentTimestamp / precision)
      |saved.trimBefore = saved.blockId - blocks + 1
      |saved.countKey = windowSize .. ':' .. precision .. ':'
      |saved.tsKey = saved.countKey .. 'o'
      |
      |local oldTs = redis.call('HGET', key, saved.tsKey)
      |oldTs = oldTs and tonumber(oldTs) or saved.trimBefore
      |if oldTs > currentTimestamp then
      |  return 0
      |end
      |
      |local decrement = 0
      |local deletion = {}
      |local trim = math.min(saved.trimBefore, oldTs + blocks)
      |
      |for oldBlock = oldTs, trim - 1 do
      |  local bKey = saved.countKey .. oldBlock
      |  local bCount = redis.call('HGET', key, bKey)
      |  if bCount then
      |    decrement = decrement + tonumber(bCount)
      |    table.insert(deletion, bKey)
      |  end
      |end
      |
      |local cur
      |if #deletion > 0 then
      |  redis.call('HDEL', key, unpack(deletion))
      |  cur = redis.call('HINCRBY', key, saved.countKey, -decrement)
      |else
      |  cur = redis.call('HGET', key, saved.countKey)
      |end
      |
      |return math.max(0, maxTokens - tonumber(cur or '0'));
      |""".stripMargin
}
