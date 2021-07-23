package genkai.redis.jedis.cats3

import cats.effect.IO
import genkai.effect.cats3.Cats3BaseSpec
import genkai.{RateLimiter, Strategy}
import genkai.redis.jedis.JedisRateLimiterSpec

import scala.concurrent.Future

class JedisCats3RateLimiterSpec extends JedisRateLimiterSpec[IO] with Cats3BaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    JedisCats3RateLimiter.useClient[IO](jedisPool, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
