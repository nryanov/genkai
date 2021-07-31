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
   * args: key, current_timestamp (epoch seconds), cost, maxTokens, refillAmount, refillTime (seconds)
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - {lastRefillTime, remainingTokens, isAllowed}
   */
  val tokenBucketAcquire: String =
    """
      |local createIfNotExists = function(key, maxTokens, instant)
      |  if redis.call('EXISTS', key) == 0 then 
      |    redis.call('HMSET', key, 'tokens', maxTokens, 'lastRefillTime', instant, 'hw', instant)
      |  end
      |end
      |
      |local refill = function(key, instant, maxTokens, refillAmount, refillTime)
      |  local state = redis.call('HMGET', key, 'tokens', 'lastRefillTime')
      |  local remainingTokens = tonumber(state[1])
      |  local lastRefillTime = tonumber(state[2])
      |
      |  if instant - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((instant - lastRefillTime) / refillTime)
      |    local refilledTokens = math.min(maxTokens, remainingTokens + refillAmount * refillTimes)
      |    redis.call('HSET', key, 'tokens', refilledTokens, 'lastRefillTime', instant)
      |  end
      |end
      |
      |local prepareResponse = function(instant, remaining, isAllowed)
      |  return {tonumber(instant), tonumber(remaining), isAllowed}
      |end
      |
      |local currentTimestamp = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxTokens = tonumber(ARGV[3])
      |local refillAmount = tonumber(ARGV[4])
      |local refillTime = tonumber(ARGV[5])
      |
      |createIfNotExists(KEYS[1], maxTokens, currentTimestamp)
      |refill(KEYS[1], currentTimestamp, maxTokens, refillAmount, refillTime)
      |
      |local updatedLastRefillTime = redis.call('HGET', KEYS[1], 'lastRefillTime')
      |local currentTokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
      |local remaining = currentTokens - cost
      |
      |if remaining >= 0 then
      |    redis.call('HSET', KEYS[1], 'tokens', remaining)
      |    return prepareResponse(updatedLastRefillTime, remaining, 1)
      |else
      |    return prepareResponse(updatedLastRefillTime, currentTokens, 0)
      |end
      |""".stripMargin

  /**
   * args: key, current_timestamp (epoch seconds), maxTokens, refillAmount, refillTime (seconds)
   * key format: token_bucket:<key>
   * hash structure: f1: value, f2: lastRefillTime
   * @return - unused tokens
   */
  val tokenBucketPermissions: String =
    """
      |local refill = function(key, instant, maxTokens, refillAmount, refillTime)
      |  local state = redis.call('HMGET', key, 'tokens', 'lastRefillTime')
      |  local remainingTokens = tonumber(state[1])
      |  local lastRefillTime = tonumber(state[2])
      |
      |  if instant - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((instant - lastRefillTime) / refillTime)
      |    local refilledTokens = math.min(maxTokens, remainingTokens + refillAmount * refillTimes)
      |    redis.call('HSET', key, 'tokens', refilledTokens, 'lastRefillTime', instant)
      |  end
      |end
      |
      |local currentTimestamp = tonumber(ARGV[1])
      |local maxTokens = tonumber(ARGV[2])
      |local refillAmount = tonumber(ARGV[3])
      |local refillTime = tonumber(ARGV[4])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then
      |  -- record does not exists yet, so permissions are not used
      |  return maxTokens
      |else
      |  refill(KEYS[1], currentTimestamp, maxTokens, refillAmount, refillTime)
      |  return tonumber(redis.call('HGET', KEYS[1], 'tokens'))
      |end
      |""".stripMargin

  /**
   * args: key, windowStartTs (epoch seconds), cost, maxTokens, ttl (windowSize, seconds)
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - {highWatermarkTs, remainingTokens, isAllowed}
   */
  val fixedWindowAcquire: String =
    """
      |local createIfNotExist = function(key, instant)
      |  if redis.call('EXISTS', key) == 0 then 
      |    redis.call('HMSET', key, 'usedTokens', 0, 'hw', instant)
      |  end
      |end
      |
      |local prepareResponse = function(key, maxTokens, usedTokens, isAllowed)
      |  local hw = tonumber(redis.call('HGET', key, 'hw'))
      |  local remaining = maxTokens - tonumber(usedTokens)
      |  return {hw, remaining, isAllowed}
      |end
      |
      |local windowStartTs = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxTokens = tonumber(ARGV[3])
      |local ttl = tonumber(ARGV[4])
      |
      |createIfNotExist(KEYS[1], windowStartTs)
      |
      |local hw = tonumber(redis.call('HGET', KEYS[1], 'hw')) 
      |
      |if hw > windowStartTs then
      |  local used = tonumber(redis.call('HGET', KEYS[1], 'usedTokens')) or 0
      |  return prepareResponse(KEYS[1], maxTokens, used, 0)
      |end
      |
      |if windowStartTs - hw >= ttl then
      |  redis.call('HSET', KEYS[1], 'usedTokens', 0)
      |  redis.call('HSET', KEYS[1], 'hw', windowStartTs)
      |end
      |
      |local current = redis.call('HGET', KEYS[1], 'usedTokens')
      |
      |if maxTokens - current - cost >= 0 then
      |    redis.call('HINCRBY', KEYS[1], 'usedTokens', cost)
      |    redis.call('EXPIRE', KEYS[1], ttl)
      |    return prepareResponse(KEYS[1], maxTokens, current + cost, 1)
      |else
      |    redis.call('EXPIRE', KEYS[1], ttl)
      |    return prepareResponse(KEYS[1], maxTokens, current, 0)
      |end
      |""".stripMargin

  /**
   * args: key, windowStartTs (epoch seconds), maxTokens, ttl (seconds)
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - permissions
   */
  val fixedWindowPermissions: String =
    """
      |local windowStartTs = tonumber(ARGV[1])
      |local maxTokens = tonumber(ARGV[2])
      |local ttl = tonumber(ARGV[3])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  return maxTokens
      |else
      |  local hw = redis.call('HGET', KEYS[1], 'hw')
      |  hw = hw and tonumber(hw) or windowStartTs 
      |
      |  -- request in the past has no permissions
      |  if hw > windowStartTs then
      |    return 0
      |  end
      |  
      |  -- if request in the next window => return maxTokens
      |  if windowStartTs - hw >= ttl then
      |    return maxTokens
      |  end
      |
      |  local used = redis.call('HGET', KEYS[1], 'usedTokens')
      |  used = used and tonumber(used) or 0
      |  return math.max(0, maxTokens - used)
      |end
      |""".stripMargin

  /**
   * input: key, instant (epoch seconds), cost, maxTokens, windowSize (seconds), precision, ttl (seconds)
   * key format: sliding_window:<key>
   * @return - {highWatermarkTs, remainingTokens, isAllowed}
   */
  // ref: https://www.dr-josiah.com/2014/11/introduction-to-rate-limiting-with_26.html
  val slidingWindowAcquire: String =
    """
      |local usedTokensKey = 'ut'
      |local oldestBlockKey = 'ob'
      |local hw = 'hw'
      |
      |local removeOld = function(key, trimBefore, oldestBlock, blocks)
      |  local decrement = 0
      |  local deletion = {}
      |  local trim = math.min(trimBefore, oldestBlock + blocks)
      |
      |  for oldBlock = oldestBlock, trim - 1 do
      |    local bKey = usedTokensKey .. oldBlock
      |    local bCount = redis.call('HGET', key, bKey)
      |    if bCount then
      |      decrement = decrement + tonumber(bCount)
      |      table.insert(deletion, bKey)
      |    end
      |  end
      |
      |  local used = 0
      |  if #deletion > 0 then
      |    redis.call('HDEL', key, unpack(deletion))
      |    used = tonumber(redis.call('HINCRBY', key, usedTokensKey, -decrement))
      |  else
      |    used = tonumber(redis.call('HGET', key, usedTokensKey) or '0')
      |  end
      |  
      |  return used
      |end
      |
      |local prepareResponse = function(key, instant, maxTokens, isAllowed)
      |  local used = tonumber(redis.call('HGET', key, usedTokensKey)) or 0
      |  local oldestTs = tonumber(redis.call('HGET', key, hw) or instant)
      |  return {oldestTs, maxTokens - used, isAllowed}
      |end
      |
      |local key = KEYS[1]
      |local instant = tonumber(ARGV[1])
      |local cost = tonumber(ARGV[2])
      |local maxTokens = tonumber(ARGV[3])
      |local windowSize = tonumber(ARGV[4])
      |local precision = tonumber(ARGV[5])
      |local ttl = tonumber(ARGV[6])
      |
      |local blocks = math.ceil(windowSize / precision)
      |
      |local currentBlock = math.floor(instant / precision)
      |local trimBefore = currentBlock - blocks + 1
      |
      |local oldestBlock = redis.call('HGET', key, oldestBlockKey)
      |oldestBlock = oldestBlock and tonumber(oldestBlock) or trimBefore
      |
      |if oldestBlock > currentBlock then
      |  return prepareResponse(key, instant, maxTokens, 0)
      |end
      |
      |local used = removeOld(key, trimBefore, oldestBlock, blocks)
      |
      |if used + cost > maxTokens then
      |  return prepareResponse(key, instant, maxTokens, 0)
      |end
      |
      |redis.call('HSET', key, oldestBlockKey, trimBefore)
      |redis.call('HSET', key, hw, instant)
      |redis.call('HINCRBY', key, usedTokensKey, cost)
      |redis.call('HINCRBY', key, usedTokensKey .. currentBlock, cost)
      |
      |redis.call('EXPIRE', key, ttl)
      |
      |return prepareResponse(key, instant, maxTokens, 1)
      |""".stripMargin

  /**
   * input: key, instant (epoch seconds), maxTokens, windowSize (seconds), precision
   * key format: sliding_window:<key>
   * @return - permissions
   */
  val slidingWindowPermissions: String =
    """
      |local key = KEYS[1]
      |local instant = tonumber(ARGV[1])
      |local maxTokens = tonumber(ARGV[2])
      |local windowSize = tonumber(ARGV[3])
      |local precision = tonumber(ARGV[4])
      |
      |local isExists = redis.call('EXISTS', KEYS[1])
      |if isExists == 0 then
      |  return maxTokens
      |end
      |
      |local blocks = math.ceil(windowSize / precision)
      |
      |local currentBlock = math.floor(instant / precision)
      |local lastBlock = currentBlock - blocks + 1
      |local usedTokensKey = 'ut'
      |local oldestBlockKey = 'ob'
      |
      |local oldestBlock = redis.call('HGET', key, oldestBlockKey)
      |oldestBlock = oldestBlock and tonumber(oldestBlock) or lastBlock
      |if oldestBlock > currentBlock then
      |  -- request in the past has no permissions
      |  return 0
      |end
      |
      |-- count actual used tokens in previous reachable blocks
      |local current = 0 
      |for block = lastBlock, currentBlock do
      |  local bKey = usedTokensKey .. block
      |  local bCount = redis.call('HGET', key, bKey)
      |  if bCount then
      |    current = current + tonumber(bCount)
      |  end
      |end
      |
      |return math.max(0, maxTokens - current)
      |""".stripMargin

  /**
   * input: key, instant (epoch millis), maxSlots, ttl (millis)
   * key format: concurrent_limiter:<key>
   * @return - 0 if no slot was acquired, 1 otherwise.
   */
  val concurrentRateLimiterAcquire: String =
    """
      |local instant = tonumber(ARGV[1])
      |local maxSlots = tonumber(ARGV[2])
      |local ttl = tonumber(ARGV[3])
      |
      |local expiredSlots = instant - ttl
      |-- remove expired records (-inf, timestamp)
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', expiredSlots)
      |
      |local current = redis.call('ZCARD', KEYS[1])
      |
      |if current + 1 <= maxSlots then
      |  redis.call('ZADD', KEYS[1], instant, instant)
      |  return 1
      |else
      |  return 0
      |end
      |""".stripMargin

  /**
   * input: key, instant (epoch millis), ttl (millis)
   * key format: concurrent_limiter:<key>
   * @return - 0 if no slot was released, 1 otherwise.
   */
  val concurrentRateLimiterRelease: String =
    """
      |local instant = tonumber(ARGV[1])
      |local ttl = tonumber(ARGV[2])
      |
      |local expiredSlots = instant - ttl
      |-- remove expired records (-inf, timestamp)
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', expiredSlots)
      |local removed = redis.call('ZPOPMIN', KEYS[1])
      |local removed = removed and #removed or 0
      |
      |if removed > 0 then
      |  return 1
      |else
      |  return 0
      |end
      |""".stripMargin

  /**
   * input: key, instant (epoch millis), maxSlots, ttl (millis)
   * key format: concurrent_limiter:<key>
   * @return - permissions
   */
  val concurrentRateLimiterPermissions: String =
    """
      |local instant = tonumber(ARGV[1])
      |local maxSlots = tonumber(ARGV[2])
      |local ttl = tonumber(ARGV[3])
      |
      |local expiredSlots = instant - ttl
      |-- remove expired records (-inf, timestamp)
      |redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', expiredSlots)
      |
      |return math.max(0, maxSlots - redis.call('ZCARD', KEYS[1]))
      |""".stripMargin
}
