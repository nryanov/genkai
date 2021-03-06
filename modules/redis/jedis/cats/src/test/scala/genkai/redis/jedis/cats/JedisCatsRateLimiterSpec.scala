package genkai.redis.jedis.cats

import cats.effect.IO
import genkai.effect.cats.CatsBaseSpec
import genkai.{RateLimiter, Strategy}
import genkai.redis.jedis.JedisRateLimiterSpec

import scala.concurrent.Future

class JedisCatsRateLimiterSpec extends JedisRateLimiterSpec[IO] with CatsBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    JedisCatsRateLimiter.useClient[IO](jedisPool, strategy, blocker).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
