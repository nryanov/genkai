package genkai.redis.jedis

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy, TryConcurrentRateLimiter}

import scala.concurrent.Future
import scala.util.Try

class JedisSyncConcurrentRateLimiterSpec extends JedisConcurrentRateLimiterSpec[Try] {
  override def concurrentRateLimiter(
    strategy: ConcurrentStrategy
  ): ConcurrentRateLimiter[Try] =
    new TryConcurrentRateLimiter(JedisSyncConcurrentRateLimiter(jedisPool, strategy))

  override def toFuture[A](v: Try[A]): Future[A] = Future.fromTry(v)
}
