package genkai.redis

import java.time.Instant

import genkai.{Key, Strategy}

sealed trait RedisStrategy {
  def underlying: Strategy

  def acquireLuaScript: String

  def permissionsLuaScript: String

  def key[A: Key](value: A, instant: Instant): String

  def permissionsArgs(instant: Instant): List[String]

  def acquireArgs(instant: Instant, cost: Long): List[String]

  def isAllowed(value: Long): Boolean

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
      underlying.refillDelay.toMillis.toString
    )

    override val acquireLuaScript: String = LuaScript.tokenBucketAcquire

    override val permissionsLuaScript: String = LuaScript.tokenBucketPermissions

    override def key[A: Key](value: A, instant: Instant): String =
      s"token_bucket:${Key[A].convert(value)}"

    override def permissionsArgs(instant: Instant): List[String] =
      instant.toEpochMilli.toString :: argsPart

    override def acquireArgs(instant: Instant, cost: Long): List[String] =
      instant.toEpochMilli.toString :: cost.toString :: argsPart

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

    override def key[A: Key](value: A, instant: Instant): String = {

      val ts = instant.truncatedTo(underlying.window.unit).toEpochMilli
      s"fixed_window:${Key[A].convert(value)}:$ts"
    }

    override def permissionsArgs(instant: Instant): List[String] = permissionArgsPart

    override def acquireArgs(instant: Instant, cost: Long): List[String] = acquireArgsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }

  final case class RedisSlidingWindow(underlying: Strategy.SlidingWindow) extends RedisStrategy {
    private val permissionArgsPart =
      List(underlying.tokens.toString, underlying.window.size.toString)
    private val acquireArgsPart =
      List(
        underlying.tokens.toString,
        underlying.window.size.toString,
        underlying.window.size.toString
      )

    override val acquireLuaScript: String = LuaScript.slidingWindowAcquire

    override val permissionsLuaScript: String = LuaScript.slidingWindowPermissions

    override def key[A: Key](value: A, instant: Instant): String =
      s"sliding_window:${Key[A].convert(value)}"

    override def permissionsArgs(instant: Instant): List[String] =
      instant.toEpochMilli.toString :: permissionArgsPart

    override def acquireArgs(instant: Instant, cost: Long): List[String] =
      instant.toEpochMilli.toString :: acquireArgsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }
}
