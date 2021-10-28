package genkai.aerospike.cats

import cats.effect.IO
import genkai.{RateLimiter, Strategy}
import genkai.aerospike.AerospikeSpecForAll
import genkai.effect.cats.CatsBaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class AerospikeCatsRateLimiterSpec extends AerospikeSpecForAll[IO] with CatsBaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    AerospikeCatsRateLimiter.useClient[IO](aerospikeClient, "test", strategy, blocker, 100 millis).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
