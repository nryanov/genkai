package genkai.aerospike

import java.time.Instant

import genkai.{Key, Strategy}
import com.aerospike.client.{Key => AKey, Value}

/**
 * Aerospike specific wrapper for [[genkai.Strategy]]
 */
sealed trait AerospikeStrategy {
  def underlying: Strategy

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
    case inst: Strategy.TokenBucket => AerospikeTokenBucket(inst)
    case inst: Strategy.FixedWindow => AerospikeFixedWindow(inst)
    case _: Strategy.SlidingWindow =>
      throw new NotImplementedError("Sliding window is not implemented yet for aerospike backend")
  }

  final case class AerospikeTokenBucket(underlying: Strategy.TokenBucket)
      extends AerospikeStrategy {
    private val setName: String = "token_bucket_set"

    private val argsPart = List(
      Value.get(underlying.tokens),
      Value.get(underlying.refillAmount),
      Value.get(underlying.refillDelay.toMillis)
    )

    override val luaScript: String = LuaScript.tokenBucket

    override val serverPath: String = "token_bucket.lua"

    override val packageName: String = "token_bucket"

    override val acquireFunction: String = "acquire"

    override val permissionsFunction: String = "permissions"

    override def key[A: Key](namespace: String, value: A, instant: Instant): AKey =
      new AKey(namespace, setName, Key[A].convert(value))

    override def permissionsArgs(instant: Instant): List[Value] =
      Value.get(instant.toEpochMilli) :: argsPart

    override def acquireArgs(instant: Instant, cost: Long): List[Value] =
      Value.get(instant.toEpochMilli) :: Value.get(cost) :: argsPart

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

    override val luaScript: String = LuaScript.fixedWindow

    override val serverPath: String = "fixed_window.lua"

    override val packageName: String = "fixed_window"

    override val acquireFunction: String = "acquire"

    override val permissionsFunction: String = "permissions"

    override def key[A: Key](namespace: String, value: A, instant: Instant): AKey = {
      val ts = instant.truncatedTo(underlying.window.unit).toEpochMilli
      val key = s"${Key[A].convert(value)}:$ts"
      new AKey(namespace, setName, key)
    }

    override def permissionsArgs(instant: Instant): List[Value] = permissionArgsPart

    override def acquireArgs(instant: Instant, cost: Long): List[Value] =
      Value.get(cost) :: acquireArgsPart

    override def isAllowed(value: Long): Boolean = value != 0

    override def toPermissions(value: Long): Long = value
  }
}
