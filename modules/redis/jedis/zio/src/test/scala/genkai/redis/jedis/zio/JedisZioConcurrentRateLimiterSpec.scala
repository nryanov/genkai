package genkai.redis.jedis.zio

import genkai.{ConcurrentRateLimiter, ConcurrentStrategy}
import genkai.effect.zio.ZioBaseSpec
import genkai.redis.jedis.JedisConcurrentRateLimiterSpec
import zio.Task

import scala.concurrent.Future

class JedisZioConcurrentRateLimiterSpec extends JedisConcurrentRateLimiterSpec[Task] with ZioBaseSpec {

  override def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[Task] =
    runtime.unsafeRun(JedisZioConcurrentRateLimiter.useClient(jedisPool, strategy))

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
