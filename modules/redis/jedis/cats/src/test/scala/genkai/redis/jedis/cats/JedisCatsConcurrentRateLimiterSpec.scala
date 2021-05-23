package genkai.redis.jedis.cats

import cats.effect.IO
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.cats.CatsBaseSpec
import genkai.redis.jedis.JedisConcurrentRateLimiterSpec

import scala.concurrent.Future

class JedisCatsConcurrentRateLimiterSpec
    extends JedisConcurrentRateLimiterSpec[IO]
    with CatsBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    JedisCatsConcurrentRateLimiter.useClient[IO](jedisPool, strategy, blocker).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
