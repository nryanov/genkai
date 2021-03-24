package genkai.redis

import java.time.Instant

import genkai.{Key, Strategy}

sealed trait RedisStrategy {
  def underlying: Strategy

  def acquireLuaScript: String

  def permissionsLuaScript: String

  def key[A: Key](value: A, instant: Instant): String

  def args(instant: Instant): List[String]

  def argsWithTtl(instant: Instant): List[String] = args(instant)

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
    override val acquireLuaScript: String = LuaScript.tokenBucketAcquire

    override val permissionsLuaScript: String = LuaScript.tokenBucketPermissions

    override def key[A: Key](value: A, instant: Instant): String =
      s"token_bucket:${Key[A].convert(value)}"

    override def args(instant: Instant): List[String] = List(
      underlying.tokens.toString,
      instant.toEpochMilli.toString,
      underlying.refillAmount.toString,
      underlying.refillDelay.toMillis.toString
    )

    override def isAllowed(value: Long): Boolean = value > 0

    override def toPermissions(value: Long): Long = value
  }

  final case class RedisFixedWindow(underlying: Strategy.FixedWindow) extends RedisStrategy {
    private val _argsWithTtl = List(underlying.window.size.toString)

    override val acquireLuaScript: String = LuaScript.fixedWindowAcquire

    override val permissionsLuaScript: String = LuaScript.fixedWindowPermissions

    override def key[A: Key](value: A, instant: Instant): String = {

      val ts = instant.truncatedTo(underlying.window.unit).toEpochMilli
      s"fixed_window:${Key[A].convert(value)}:$ts"
    }

    override def args(instant: Instant): List[String] = List.empty

    override def argsWithTtl(instant: Instant): List[String] = _argsWithTtl

    override def isAllowed(value: Long): Boolean = underlying.tokens - value > 0

    override def toPermissions(value: Long): Long = Math.max(0, underlying.tokens - value)
  }

  final case class RedisSlidingWindow(underlying: Strategy.SlidingWindow) extends RedisStrategy {
    override val acquireLuaScript: String = LuaScript.slidingWindowAcquire

    override val permissionsLuaScript: String = LuaScript.slidingWindowPermissions

    override def key[A: Key](value: A, instant: Instant): String =
      s"sliding_window:${Key[A].convert(value)}"

    override def args(instant: Instant): List[String] = List(
      instant.toEpochMilli.toString,
      underlying.window.size.toString
    )

    override def argsWithTtl(instant: Instant): List[String] = List(
      instant.toEpochMilli.toString,
      underlying.window.size.toString,
      underlying.window.size.toString
    )

    override def isAllowed(value: Long): Boolean = underlying.tokens - value > 0

    override def toPermissions(value: Long): Long = Math.max(0, underlying.tokens - value)
  }
}
