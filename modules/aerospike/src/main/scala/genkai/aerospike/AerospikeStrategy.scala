package genkai.aerospike

import java.time.Instant

import genkai.{Key, Strategy, Window}
import com.aerospike.client.{Value, Key => AKey}

/**
 * Aerospike specific wrapper for [[genkai.Strategy]]
 */
sealed trait AerospikeStrategy {
  def underlying: Strategy

  /**
   * @return - seconds record will live before being removed by the server.
   */
  def expiration: Int

  /**
   * Lua script which will be loaded once per RateLimiter.
   * For more details see [[genkai.aerospike.LuaScript]]
   */
  def luaScript: String

  /**
   * @return - path to store user defined functions on the server, relative to configured script directory.
   */
  def serverPath: String

  /**
   * @return - server package name where user defined function resides
   */
  def packageName: String

  /**
   * @return - name of acquire function in lua package
   */
  def acquireFunction: String

  /**
   * @return - name of permissions function in lua package
   */
  def permissionsFunction: String

  /**
   * @param namespace - aerospike namespace
   * @param value - key
   * @param instant - request time
   * @tparam A - key type with implicit [[genkai.Key]] type class instance
   * @return - Aerospike key
   */
  def key[A: Key](namespace: String, value: A, instant: Instant): AKey

  /**
   * @param instant - request time
   * @return - list of script args
   */
  def permissionsArgs(instant: Instant): List[Value]

  /**
   * @param instant - request time
   * @param cost - request cost
   * @return
   */
  def acquireArgs(instant: Instant, cost: Long): List[Value]

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

object AerospikeStrategy {
  def apply(strategy: Strategy): AerospikeStrategy = strategy match {
    case inst: Strategy.TokenBucket   => AerospikeTokenBucket(inst)
    case inst: Strategy.FixedWindow   => AerospikeFixedWindow(inst)
    case inst: Strategy.SlidingWindow => AerospikeSlidingWindow(inst)
  }

  final case class AerospikeTokenBucket(underlying: Strategy.TokenBucket)
      extends AerospikeStrategy {
    private val setName: String = "token_bucket_set"

    private val argsPart = List(
      Value.get(underlying.tokens),
      Value.get(underlying.refillAmount),
      Value.get(underlying.refillDelay.toSeconds)
    )

    // never expires
    override val expiration: Int = -1

    override val luaScript: String = LuaScript.tokenBucket

    override val serverPath: String = "token_bucket.lua"

    override val packageName: String = "token_bucket"

    override val acquireFunction: String = "acquire"

    override val permissionsFunction: String = "permissions"

    override def key[A: Key](namespace: String, value: A, instant: Instant): AKey =
      new AKey(namespace, setName, Key[A].convert(value))

    // for token bucket strategy we always need a current timestamp
    override def permissionsArgs(instant: Instant): List[Value] =
      Value.get(Instant.now().getEpochSecond) :: argsPart

    // for token bucket strategy we always need a current timestamp
    override def acquireArgs(instant: Instant, cost: Long): List[Value] =
      Value.get(Instant.now().getEpochSecond) :: Value.get(cost) :: argsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }

  final case class AerospikeFixedWindow(underlying: Strategy.FixedWindow)
      extends AerospikeStrategy {
    private val setName: String = "fixed_window_set"

    private val permissionArgsPart =
      List(Value.get(underlying.tokens))
    private val acquireArgsPart =
      List(
        Value.get(underlying.tokens),
        Value.get(underlying.window.size)
      )

    override val expiration: Int = underlying.window.size.toInt

    override val luaScript: String = LuaScript.fixedWindow

    override val serverPath: String = "fixed_window.lua"

    override val packageName: String = "fixed_window"

    override val acquireFunction: String = "acquire"

    override val permissionsFunction: String = "permissions"

    override def key[A: Key](namespace: String, value: A, instant: Instant): AKey =
      new AKey(namespace, setName, Key[A].convert(value))

    override def permissionsArgs(instant: Instant): List[Value] = {
      val windowStartTs = instant.truncatedTo(underlying.window.unit).getEpochSecond
      Value.get(windowStartTs) :: permissionArgsPart
    }

    override def acquireArgs(instant: Instant, cost: Long): List[Value] = {
      val windowStartTs = instant.truncatedTo(underlying.window.unit).getEpochSecond
      Value.get(windowStartTs) :: Value.get(cost) :: acquireArgsPart
    }

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }

  final case class AerospikeSlidingWindow(underlying: Strategy.SlidingWindow)
      extends AerospikeStrategy {
    private val setName: String = "sliding_window_set"
    private val precision = underlying.window match {
      case Window.Second => 1
      case Window.Minute => 1 // 1 minute -> 60 buckets (~ seconds)
      case Window.Hour   => 60 // 1 hour -> 60 buckets (~ minutes)
      case Window.Day    => 3600 // 1 day -> 24 buckets (~ hours)
    }

    private val argsPart =
      List(
        Value.get(underlying.tokens),
        Value.get(underlying.window.size),
        Value.get(precision)
      )

    override val expiration: Int = underlying.window.size.toInt

    override val luaScript: String = LuaScript.slidingWindow

    override val serverPath: String = "sliding_window.lua"

    override val packageName: String = "sliding_window"

    override val acquireFunction: String = "acquire"

    override val permissionsFunction: String = "permissions"

    override def key[A: Key](namespace: String, value: A, instant: Instant): AKey =
      new AKey(namespace, setName, Key[A].convert(value))

    override def permissionsArgs(instant: Instant): List[Value] =
      Value.get(instant.getEpochSecond) :: argsPart

    override def acquireArgs(instant: Instant, cost: Long): List[Value] =
      Value.get(instant.getEpochSecond) :: Value.get(cost) :: argsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }
}
