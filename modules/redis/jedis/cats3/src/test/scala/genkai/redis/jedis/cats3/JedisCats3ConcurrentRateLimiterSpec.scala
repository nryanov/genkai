package genkai.redis.jedis.cats3

import cats.effect.IO
import genkai.effect.cats3.Cats3BaseSpec
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.redis.jedis.JedisConcurrentRateLimiterSpec

import scala.concurrent.Future

class JedisCats3ConcurrentRateLimiterSpec
    extends JedisConcurrentRateLimiterSpec[IO]
    with Cats3BaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    JedisCats3ConcurrentRateLimiter.useClient[IO](jedisPool, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
