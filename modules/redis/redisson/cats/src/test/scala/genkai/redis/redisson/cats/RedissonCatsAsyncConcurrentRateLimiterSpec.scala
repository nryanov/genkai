package genkai.redis.redisson.cats

import cats.effect.IO
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.cats.CatsBaseSpec
import genkai.redis.redisson.RedissonConcurrentRateLimiterSpec

import scala.concurrent.Future

class RedissonCatsAsyncConcurrentRateLimiterSpec
    extends RedissonConcurrentRateLimiterSpec[IO]
    with CatsBaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    RedissonCatsAsyncConcurrentRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
