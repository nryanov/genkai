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

  val fixedWindow: String =
    """
      |local currentTokensBin = 'tokens'
      |
      |function acquire(r, cost, maxTokens, ttl)
      |  if not aerospike:exists(r) then aerospike:create(r) end
      |  local current = r[currentTokensBin] or 0
      |  if maxTokens - current - cost >= 0 then
      |    r[currentTokensBin] = current + cost
      |    record.set_ttl(r, ttl)
      |    aerospike:update(r)
      |    return 1
      |  else
      |    return 0
      |  end
      |end
      |
      |function permissions(r, maxTokens)
      |  if not aerospike:exists(r) then 
      |    return maxTokens
      |  else
      |    return math.max(0, maxTokens - r[currentTokensBin])
      |  end
      |end
      |""".stripMargin

  val slidingWindow: String =
    """
      |
      |""".stripMargin
}
