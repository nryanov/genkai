package genkai.aerospike

// to be able to set ttl on records
// nsup-period must be set to non-zero value in namespace config
object LuaScript {
  // todo: optional ttl
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
      |function acquire(r, currentTimestamp, cost, maxTokens, refillAmount, refillTime)
      |  createIfNotExists(r, currentTimestamp, maxTokens)
      |  refill(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  
      |  local current = r[currentTokensBin]
      |  local remaining = current - cost
      |  if remaining >= 0 then
      |    r[currentTokensBin] = remaining
      |    aerospike:update(r)
      |    return 1
      |  else 
      |    aerospike:update(r)
      |    return 0
      |  end
      |end
      |
      |function permissions(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  createIfNotExists(r, currentTimestamp, maxTokens)
      |  refill(r, currentTimestamp, maxTokens, refillAmount, refillTime)
      |  
      |  aerospike:update(r)
      |  return r[currentTokensBin]
      |end
      |""".stripMargin

  // todo: optional ttl
  val fixedWindow: String =
    """
      |local usedTokensBin = 'ut'
      |local highWatermarkBin = 'hw'
      |
      |function acquire(r, windowStartTs, cost, maxTokens, windowSize)
      |  if not aerospike:exists(r) then
      |    r[highWatermarkBin] = windowStartTs
      |    r[usedTokensBin] = 0
      |    aerospike:create(r) 
      |  end
      |  
      |  local hw = r[highWatermarkBin]
      |  
      |  if hw > windowStartTs then
      |   aerospike:update(r)
      |   return 0
      |  end
      |   
      |   if windowStartTs - hw >= windowSize then
      |     r[usedTokensBin] = 0
      |     r[highWatermarkBin] = windowStartTs
      |   end
      |  
      |  local usedTokens = r[usedTokensBin] or 0
      |  if maxTokens - usedTokens - cost >= 0 then
      |    r[usedTokensBin] = usedTokens + cost
      |    aerospike:update(r)
      |    return 1
      |  else
      |    aerospike:update(r)
      |    return 0
      |  end
      |end
      |
      |function permissions(r, maxTokens)
      |  if not aerospike:exists(r) then 
      |    return maxTokens
      |  else
      |    return math.max(0, maxTokens - r[usedTokensBin])
      |  end
      |end
      |""".stripMargin

  // todo: optional ttl
  // ref: https://www.dr-josiah.com/2014/11/introduction-to-rate-limiting-with_26.html
  val slidingWindow: String =
    """
      |function acquire(r, currentTimestamp, cost, maxTokens, windowSize, precision)
      |  local blocks = math.ceil(windowSize / precision)
      |  local saved = {}
      | 
      |  saved.blockId = math.floor(currentTimestamp / precision)
      |  saved.trimBefore = saved.blockId - blocks + 1
      |  saved.countKey = windowSize .. ':' .. precision .. ':'
      |  saved.tsKey = saved.countKey .. 'o'
      |  
      |  local oldTs = r[saved.tsKey]
      |  oldTs = oldTs and tonumber(oldTs) or saved.trimBefore
      |  if oldTs > currentTimestamp then
      |    return 0
      |  end
      |  
      |  local decrement = 0
      |  local deletion = {}
      |  local trim = math.min(saved.trimBefore, oldTs + blocks)
      |  
      |  for oldBlock = oldTs, trim - 1 do
      |    local bKey = saved.countKey .. oldBlock
      |    local bCount = r[bKey]
      |    if bCount then
      |      decrement = decrement + tonumber(bCount)
      |      table.insert(deletion, bKey)
      |    end
      |  end
      |  
      |  local cur
      |  if #deletion > 0 then
      |    for key, _ in pairs(deletion) do
      |      r[key] = nil
      |    end
      |    r[saved.countKey] = r[saved.countKey] - decrement
      |    cur = r[saved.countKey]
      |  else
      |    cur = r[saved.countKey]
      |  end
      |  
      |  if tonumber(cur or 0) + cost > maxTokens then
      |    return 0
      |  end
      |  
      |  r[saved.tsKey] = saved.trimBefore
      |  r[saved.countKey] = r[saved.countKey] + cost
      |  r[saved.countKey .. saved.blockId] = r[saved.countKey .. saved.blockId] + cost
      |
      |  return 1
      |end
      |
      |function permissions(r, maxTokens, windowSize, precision)
      |  local countKey = windowSize .. ':' .. precision .. ':'
      |  return math.max(0, maxTokens - r[countKey])
      |end
      |""".stripMargin
}
