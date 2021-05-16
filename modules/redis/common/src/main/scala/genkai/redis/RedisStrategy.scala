package genkai.redis

import java.time.Instant

import genkai.{Key, Strategy, Window}

/**
 * Redis specific wrapper for [[genkai.Strategy]]
 */
sealed trait RedisStrategy {
  def underlying: Strategy

  /**
   * Lua script which will be loaded once per RateLimiter.
   * Used for acquiring tokens.
   * For more details see [[genkai.redis.LuaScript]]
   */
  def acquireLuaScript: String

  /**
   * Lua script which will be loaded once per RateLimiter.
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
   * @param cost - request cost
   * @return
   */
  def acquireArgs(instant: Instant, cost: Long): List[String]

  /**
   * @param value - returned value after [[genkai.RateLimiter.acquire()]]
   * @return - true if token was acquired otherwise false
   */
  def isAllowed(value: Long): Boolean

  /**
   * @param value - returned value after [[genkai.RateLimiter.permissions()]]
   * @return - unused permissions
   */
  def toPermissions(value: Long): Long
}

object RedisStrategy {
  def apply(underlying: Strategy): RedisStrategy = underlying match {
    case s: Strategy.TokenBucket   => RedisTokenBucket(s)
    case s: Strategy.FixedWindow   => RedisFixedWindow(s)
    case s: Strategy.SlidingWindow => RedisSlidingWindow(s)
  }

  final case class RedisTokenBucket(underlying: Strategy.TokenBucket) extends RedisStrategy {
    private val argsPart = List(
      underlying.tokens.toString,
      underlying.refillAmount.toString,
      underlying.refillDelay.toSeconds.toString
    )

    override val acquireLuaScript: String = LuaScript.tokenBucketAcquire

    override val permissionsLuaScript: String = LuaScript.tokenBucketPermissions

    override def keys[A: Key](value: A, instant: Instant): List[String] =
      List(s"token_bucket:${Key[A].convert(value)}")

    // for token bucket strategy we always need a current timestamp
    override def permissionsArgs(instant: Instant): List[String] =
      Instant.now().getEpochSecond.toString :: argsPart

    // for token bucket strategy we always need a current timestamp
    override def acquireArgs(instant: Instant, cost: Long): List[String] =
      Instant.now().getEpochSecond.toString :: cost.toString :: argsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }

  final case class RedisFixedWindow(underlying: Strategy.FixedWindow) extends RedisStrategy {
    private val permissionArgsPart =
      List(underlying.tokens.toString)
    private val acquireArgsPart =
      List(
        underlying.tokens.toString,
        underlying.window.size.toString
      )

    override val acquireLuaScript: String = LuaScript.fixedWindowAcquire

    override val permissionsLuaScript: String = LuaScript.fixedWindowPermissions

    override def keys[A: Key](value: A, instant: Instant): List[String] =
      List(s"fixed_window:${Key[A].convert(value)}")

    override def permissionsArgs(instant: Instant): List[String] = permissionArgsPart

    override def acquireArgs(instant: Instant, cost: Long): List[String] = {
      val windowStartTs = instant.truncatedTo(underlying.window.unit).getEpochSecond.toString
      windowStartTs :: cost.toString :: acquireArgsPart
    }

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }

  final case class RedisSlidingWindow(underlying: Strategy.SlidingWindow) extends RedisStrategy {
    private val precision = underlying.window match {
      case Window.Second => 1
      case Window.Minute => 1 // 1 minute -> 60 buckets (~ seconds)
      case Window.Hour   => 60 // 1 hour -> 60 buckets (~ minutes)
      case Window.Day    => 3600 // 1 day -> 24 buckets (~ hours)
    }

    private val permissionArgsPart =
      List(
        underlying.tokens.toString, // maxTokens
        underlying.window.size.toString, // windowSize
        precision.toString // precision
      )
    private val acquireArgsPart =
      List(
        underlying.tokens.toString, // maxTokens
        underlying.window.size.toString, // windowSize
        precision.toString, // precision
        underlying.window.size.toString // ttl
      )

    override val acquireLuaScript: String = LuaScript.slidingWindowAcquire

    override val permissionsLuaScript: String = LuaScript.slidingWindowPermissions

    override def keys[A: Key](value: A, instant: Instant): List[String] =
      List(s"sliding_window:${Key[A].convert(value)}")

    override def permissionsArgs(instant: Instant): List[String] =
      instant.getEpochSecond.toString :: permissionArgsPart

    override def acquireArgs(instant: Instant, cost: Long): List[String] =
      instant.getEpochSecond.toString :: cost.toString :: acquireArgsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }
}
