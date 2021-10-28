package genkai.aerospike.cats3

import cats.effect.IO
import genkai.{RateLimiter, Strategy}
import genkai.aerospike.AerospikeSpecForAll
import genkai.effect.cats3.Cats3BaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class AerospikeCats3RateLimiterSpec extends AerospikeSpecForAll[IO] with Cats3BaseSpec {
  override def rateLimiter(strategy: Strategy): RateLimiter[IO] =
    AerospikeCats3RateLimiter.useClient[IO](aerospikeClient, "test", strategy, 100 millis).unsafeRunSync()

  override def toFuture[A](v: IO[A]): Future[A] = v.unsafeToFuture()
}
