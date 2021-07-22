package genkai.redis.redisson.cats3

import cats.effect.IO
import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.cats3.Cats3BaseSpec
import genkai.redis.redisson.RedissonConcurrentRateLimiterSpec

import scala.concurrent.Future

class RedissonCats3AsyncConcurrentRateLimiterSpec
    extends RedissonConcurrentRateLimiterSpec[IO]
    with Cats3BaseSpec {
  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[IO] =
    RedissonCats3AsyncConcurrentRateLimiter.useClient[IO](redisClient, strategy).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
