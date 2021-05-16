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
      |local maxTokens = tonumber(ARGV[3])
      |local refillAmount = tonumber(ARGV[4])
      |local refillTime = tonumber(ARGV[5])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  redis.call('HMSET', KEYS[1], 'tokens', maxTokens, 'lastRefillTime', currentTimestamp, 'hw', currentTimestamp)
      |end
      |
      |local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime')
      |local lastRefillTime = tonumber(current[2])
      |
      |if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime)
      |    local refill = math.min(maxTokens, current[1] + refillAmount * refillTimes)
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
      |local maxTokens = tonumber(ARGV[2])
      |local refillAmount = tonumber(ARGV[3])
      |local refillTime = tonumber(ARGV[4])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then
      |  -- record does not exists yet, so permissions are not used
      |  return maxTokens
      |else
      |  local current = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime')
      |  local lastRefillTime = tonumber(current[2])
      |  
      |  -- refill
      |  if currentTimestamp - lastRefillTime >= refillTime then 
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime)
      |    local refill = math.min(maxTokens, current[1] + refillAmount * refillTimes)
      |    redis.call('HMSET', KEYS[1], 'tokens', refill, 'lastRefillTime', currentTimestamp)
      |  end
      |
      |  return tonumber(redis.call('HGET', KEYS[1], 'tokens'))
      |end
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
      |local hw = redis.call('HGET', KEYS[1], 'hw')
      |hw = hw and tonumber(hw) or windowStartTs 
      |
      |if hw > windowStartTs then
      |  return 0
      |end
      |
      |redis.call('HSET', KEYS[1], 'hw', windowStartTs)
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
   * args: key, windowTs, maxTokens
   * key format: fixed_window:<key>:<timestamp> where <timestamp> is truncated to the beginning of the window
   * @return - permissions
   */
  val fixedWindowPermissions: String =
    """
      |local windowTs = tonumber(ARGV[1])
      |local maxTokens = tonumber(ARGV[2])
      |local isExists = redis.call('EXISTS', KEYS[1])
      |
      |if isExists == 0 then 
      |  return maxTokens
      |else
      |  local hw = redis.call('HGET', KEYS[1], 'hw')
      |  hw = hw and tonumber(hw) or windowTs 
      |
      |  -- request in the past has no permissions
      |  if hw > windowTs then
      |    return 0
      |  end
      |
      |  local used = redis.call('HGET', KEYS[1], 'usedTokens')
      |  used = used and tonumber(used) or 0
      |  return math.max(0, maxTokens - used)
      |end
      |""".stripMargin

  /**
   * input: key, instant, cost, maxTokens, windowSize, precision, ttl
   * key format: sliding_window:<key>
   * @return - 1 if token acquired, 0 - otherwise
   */
  // ref: https://www.dr-josiah.com/2014/11/introduction-to-rate-limiting-with_26.html
  val slidingWindowAcquire: String =
    """
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
      |local usedTokensKey = 'ut'
      |local oldestBlockKey = 'ob'
      |
      |local oldestBlock = redis.call('HGET', key, oldestBlockKey)
      |oldestBlock = oldestBlock and tonumber(oldestBlock) or trimBefore
      |if oldestBlock > currentBlock then
      |  return 0
      |end
      |
      |local decrement = 0
      |local deletion = {}
      |local trim = math.min(trimBefore, oldestBlock + blocks)
      |
      |for oldBlock = oldestBlock, trim - 1 do
      |  local bKey = usedTokensKey .. oldBlock
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
      |  cur = redis.call('HINCRBY', key, usedTokensKey, -decrement)
      |else
      |  cur = redis.call('HGET', key, usedTokensKey)
      |end
      |
      |if tonumber(cur or '0') + cost > maxTokens then
      |  return 0
      |end
      |
      |redis.call('HSET', key, oldestBlockKey, trimBefore)
      |redis.call('HINCRBY', key, usedTokensKey, cost)
      |redis.call('HINCRBY', key, usedTokensKey .. currentBlock, cost)
      |
      |redis.call('EXPIRE', key, ttl)
      |
      |return 1
      |""".stripMargin

  /**
   * input: key, instant, maxTokens, windowSize, precision
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
      |local trimBefore = currentBlock - blocks + 1
      |local usedTokensKey = 'ut'
      |local oldestBlockKey = 'ob'
      |
      |local oldestBlock = redis.call('HGET', key, oldestBlockKey)
      |oldestBlock = oldestBlock and tonumber(oldestBlock) or trimBefore
      |if oldestBlock > currentBlock then
      |  -- request in the past has no permissions
      |  return 0
      |end
      |
      |local decrement = 0
      |local deletion = {}
      |local trim = math.min(trimBefore, oldestBlock + blocks)
      |
      |for block = oldestBlock, trim - 1 do
      |  local bCount = redis.call('HGET', key, block)
      |  if bCount then
      |    decrement = decrement + tonumber(bCount)
      |    table.insert(deletion, block)
      |  end
      |end
      |
      |local cur
      |if #deletion > 0 then
      |  redis.call('HDEL', key, unpack(deletion))
      |  cur = redis.call('HINCRBY', key, usedTokensKey, -decrement)
      |else
      |  cur = redis.call('HGET', key, usedTokensKey)
      |end
      |
      |redis.call('HSET', key, oldestBlockKey, trimBefore)
      |
      |return math.max(0, maxTokens - tonumber(cur or '0'));
      |""".stripMargin
}
