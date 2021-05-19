package genkai.redis

import java.time.Instant

import genkai.{ConcurrentStrategy, Key}

sealed trait RedisConcurrentStrategy {
  def underlying: ConcurrentStrategy

  /**
   * Lua script which will be loaded once per ConcurrentRateLimiter.
   * Used for acquiring slots.
   * For more details see [[genkai.redis.LuaScript]]
   */
  def acquireLuaScript: String

  /**
   * Lua script which will be loaded once per ConcurrentRateLimiter.
   * Used for releasing slots.
   * For more details see [[genkai.redis.LuaScript]]
   */
  def releaseLuaScript: String

  /**
   * Lua script which will be loaded once per ConcurrentRateLimiter.
   * Used for getting unused permissions.
   * For more details see [[genkai.redis.LuaScript]]
   */
  def permissionsLuaScript: String

  /**
   * @param value - key
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - list of script keys
   */
  def keys[A: Key](value: A, instant: Instant): List[String]

  /**
   * @param instant - request time
   * @return - list of script args
   */
  def permissionsArgs(instant: Instant): List[String]

  /**
   * @param instant - request time
   * @return
   */
  def acquireArgs(instant: Instant): List[String]

  /**
   * @param instant - request time
   * @return
   */
  def releaseArgs(instant: Instant): List[String]

  /**
   * @param value - returned value after [[genkai.ConcurrentRateLimiter.acquire()]]
   * @return - true if token was acquired otherwise false
   */
  def isAllowed(value: Long): Boolean

  /**
   * @param value - returned value after [[genkai.ConcurrentRateLimiter.release()]]
   * @return - true if token was acquired otherwise false
   */
  def isReleased(value: Long): Boolean

  /**
   * @param value - returned value after [[genkai.ConcurrentRateLimiter.permissions()]]
   * @return - unused permissions
   */
  def toPermissions(value: Long): Long
}

object RedisConcurrentStrategy {
  def apply(underlying: ConcurrentStrategy): RedisConcurrentStrategy = underlying match {
    case s: ConcurrentStrategy.Default => RedisDefault(s)
  }

  final case class RedisDefault(underlying: ConcurrentStrategy.Default)
      extends RedisConcurrentStrategy {
    private val argsPart = List(
      underlying.slots.toString,
      underlying.ttl.toMillis.toString
    )

    private val releaseArgsPart = List(
      underlying.ttl.toMillis.toString
    )

    override def acquireLuaScript: String = LuaScript.concurrentRateLimiterAcquire

    override def releaseLuaScript: String = LuaScript.concurrentRateLimiterRelease

    override def permissionsLuaScript: String = LuaScript.concurrentRateLimiterPermissions

    override def keys[A: Key](value: A, instant: Instant): List[String] =
      List(Key[A].convert(value))

    override def permissionsArgs(instant: Instant): List[String] =
      instant.toEpochMilli.toString :: argsPart

    override def acquireArgs(instant: Instant): List[String] =
      instant.toEpochMilli.toString :: argsPart

    override def releaseArgs(instant: Instant): List[String] =
      instant.toEpochMilli.toString :: releaseArgsPart

    override def isAllowed(value: Long): Boolean = value == 1L

    override def isReleased(value: Long): Boolean = value == 1L

    override def toPermissions(value: Long): Long = value
  }
}
