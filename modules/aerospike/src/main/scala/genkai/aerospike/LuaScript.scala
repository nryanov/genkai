package genkai.aerospike

// to be able to set ttl on records
// nsup-period must be set to non-zero value in namespace config
object LuaScript {
  val tokenBucket: String =
    """
      |local currentTokensBin = 'tokens'
      |local lastRefillTimeBin = 'refillTime'
      |
      |local function createIfNotExists(r, currentTimestamp, maxTokens)
      |  if not aerospike:exists(r) then 
      |    aerospike:create(r)
      |    r[currentTokensBin] = maxTokens
      |    r[lastRefillTimeBin] = currentTimestamp    
      |  end
      |end
      |
      |local function refill(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  local current = r[currentTokensBin]
      |  local lastRefillTime = r[lastRefillTimeBin]
      |  
      |  if currentTimestamp - lastRefillTime >= refillTime then
      |    local refillTimes = math.floor((currentTimestamp - lastRefillTime) / refillTime)
      |    local refill = math.min(maxTokens, current + refillAmount * refillTimes)
      |    r[currentTokensBin] = refill
      |    r[lastRefillTimeBin] = currentTimestamp
      |  end
      |end
      |
      |local function prepareResponse(r, response, instant, remaining, isAllowed)
      |  response.ts = r[lastRefillTimeBin] or instant
      |  response.remaining = remaining
      |  response.isAllowed = isAllowed
      |end
      |
      |function acquire(r, currentTimestamp, cost, maxTokens, refillAmount, refillTime)
      |  createIfNotExists(r, currentTimestamp, maxTokens)
      |  refill(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  
      |  local current = r[currentTokensBin]
      |  local remaining = current - cost
      |  
      |  local response = map()
      |  
      |  if remaining >= 0 then
      |    r[currentTokensBin] = remaining
      |    prepareResponse(r, response, currentTimestamp, remaining, 1) 
      |    aerospike:update(r)
      |    return response
      |  else 
      |    prepareResponse(r, response, currentTimestamp, current, 0)
      |    aerospike:update(r)
      |    return response
      |  end
      |end
      |
      |function permissions(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  if not aerospike:exists(r) then
      |    -- record does not exists yet, so permissions are not used
      |    return maxTokens
      |  else
      |    refill(r, currentTimestamp, maxTokens, refillAmount, refillTime)  
      |    aerospike:update(r)
      |    return r[currentTokensBin]
      |  end
      |end
      |""".stripMargin

  val fixedWindow: String =
    """
      |local usedTokensBin = 'ut'
      |local highWatermarkBin = 'hw'
      |
      |local function prepareResponse(r, response, instant, maxTokens, isAllowed)
      |  response.ts = r[highWatermarkBin] or instant
      |  response.remaining = maxTokens - r[usedTokensBin]
      |  response.isAllowed = isAllowed
      |end
      |
      |local function createIfNotExists(r, windowStartTs)
      |  if not aerospike:exists(r) then
      |    r[highWatermarkBin] = windowStartTs
      |    r[usedTokensBin] = 0
      |    aerospike:create(r) 
      |  end
      |end
      |
      |function acquire(r, windowStartTs, cost, maxTokens, windowSize)
      |  createIfNotExists(r, windowStartTs)
      |  local response = map()
      |  local hw = r[highWatermarkBin]
      |  
      |  if hw > windowStartTs then
      |    prepareResponse(r, response, windowStartTs, maxTokens, 0)
      |    aerospike:update(r)
      |    return response
      |  end
      |   
      |  if windowStartTs - hw >= windowSize then
      |    r[highWatermarkBin] = windowStartTs
      |    r[usedTokensBin] = 0
      |  end
      |  
      |  local usedTokens = r[usedTokensBin] or 0
      |  if maxTokens - usedTokens - cost >= 0 then
      |    r[usedTokensBin] = usedTokens + cost
      |    prepareResponse(r, response, windowStartTs, maxTokens, 1)
      |    aerospike:update(r)
      |    return response
      |  else
      |    prepareResponse(r, response, windowStartTs, maxTokens, 0)
      |    aerospike:update(r)
      |    return response
      |  end
      |end
      |
      |function permissions(r, windowStartTs, maxTokens, windowSize)
      |  if not aerospike:exists(r) then 
      |    return maxTokens
      |  else
      |    local hw = r[highWatermarkBin]
      |  
      |    -- request in the past has no permissions
      |    if hw > windowStartTs then
      |     return 0
      |    end
      |    
      |    if windowStartTs - hw >= windowSize then
      |      r[usedTokensBin] = 0
      |    end
      |    
      |    return math.max(0, maxTokens - r[usedTokensBin])
      |  end
      |end
      |""".stripMargin

  // ref: https://www.dr-josiah.com/2014/11/introduction-to-rate-limiting-with_26.html
  val slidingWindow: String =
    """
      |local usedTokensBin = 'ut'
      |local oldestBlockBin = 'ob' 
      |local hw = 'hw'
      |
      |local function cleanup(r, trimBefore, oldestBlock, blocks)
      |  local decrement = 0
      |  local trim = math.min(trimBefore, oldestBlock + blocks)
      |  for block = oldestBlock, trim - 1 do
      |    local blockCount = r[block]
      |    
      |    if blockCount then
      |      decrement = decrement + tonumber(blockCount)
      |      r[block] = nil
      |    end
      |  end
      |  
      |  r[usedTokensBin] = r[usedTokensBin] - decrement
      |  r[oldestBlockBin] = trimBefore
      |  
      |  aerospike:update(r)
      |end
      |
      |local function createIfNotExists(r)
      |  if not aerospike:exists(r) then
      |    r[usedTokensBin] = 0
      |    aerospike:create(r) 
      |  end
      |end
      |
      |local function prepareResponse(r, response, instant, maxTokens, isAllowed)
      |  response.ts = r[hw] or instant
      |  response.remaining = maxTokens - r[usedTokensBin]
      |  response.isAllowed = isAllowed
      |end
      |
      |function acquire(r, instant, cost, maxTokens, windowSize, precision)
      |  createIfNotExists(r)
      |
      |  local blocks = math.ceil(windowSize / precision)
      |  local currentBlock = math.floor(instant / precision)
      |  
      |  local trimBefore = currentBlock - blocks + 1  
      |  local oldestBlock = r[oldestBlockBin]
      |  oldestBlock = oldestBlock and tonumber(oldestBlock) or trimBefore
      |  
      |  local response = map()
      |  
      |  -- attempt to write in the past
      |  if oldestBlock > currentBlock then
      |    prepareResponse(r, response, instant, maxTokens, 0)
      |    return response
      |  end
      |  
      |  cleanup(r, trimBefore, oldestBlock, blocks)
      |  
      |  if r[usedTokensBin] + cost > maxTokens then
      |    prepareResponse(r, response, instant, maxTokens, 0)
      |    return response
      |  end
      |  
      |  r[usedTokensBin] = r[usedTokensBin] + cost
      |  r[currentBlock] = tonumber(r[currentBlock] or 0) + cost
      |  r[hw] = instant
      |  
      |  prepareResponse(r, response, instant, maxTokens, 1)
      |  
      |  aerospike:update(r)
      |
      |  return response
      |end
      |
      |function permissions(r, instant, maxTokens, windowSize, precision)
      |  if not aerospike:exists(r) then
      |    return maxTokens
      |  else
      |    local blocks = math.ceil(windowSize / precision)
      |    local currentBlock = math.floor(instant / precision)
      |  
      |    local lastBlock = currentBlock - blocks + 1  
      |    local oldestBlock = r[oldestBlockBin]
      |    oldestBlock = oldestBlock and tonumber(oldestBlock) or lastBlock
      |    
      |    if oldestBlock > currentBlock then
      |      -- request in the past has no permissions
      |      return 0
      |    end
      |    
      |    -- count actual used tokens in previous reachable blocks
      |    local current = 0 
      |    for block = lastBlock, currentBlock do
      |      local blockCount = r[block]
      |    
      |      if blockCount then
      |        current = current + tonumber(blockCount)
      |      end
      |    end
      |      
      |    return math.max(0, maxTokens - current)
      |  end
      |end
      |""".stripMargin
}
