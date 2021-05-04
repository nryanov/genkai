package genkai.aerospike.zio

import genkai.{RateLimiter, Strategy}
import genkai.aerospike.AerospikeSpecForAll
import genkai.effect.zio.ZioBaseSpec
import zio.Task

import scala.concurrent.Future
import scala.concurrent.duration._

class AerospikeZioRateLimiterSpec extends AerospikeSpecForAll[Task] with ZioBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[Task] =
    runtime.unsafeRun(
      AerospikeZioRateLimiter.useClient(aerospikeClient, "test", strategy, 100 millis)
    )

  override def toFuture[A](v: Task[A]): Future[A] = runtime.unsafeRunToFuture(v)
}
