package genkai.redis.jedis.zio

import genkai.effect.zio.ZioBaseSpec
import genkai.{RateLimiter, Strategy}
import zio._
import genkai.redis.jedis.JedisRateLimiterSpec

import scala.concurrent.Future

class JedisZioRateLimiterSpec extends JedisRateLimiterSpec[Task] with ZioBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    runtime.unsafeRun(JedisZioRateLimiter.useClient(jedisPool, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
