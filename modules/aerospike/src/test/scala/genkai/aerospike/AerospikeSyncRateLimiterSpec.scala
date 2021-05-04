package genkai.aerospike

import genkai.{Identity, RateLimiter, Strategy}

import scala.concurrent.Future
import scala.concurrent.duration._

class AerospikeSyncRateLimiterSpec extends AerospikeSpecForAll[Identity] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Identity] = {
    aerospikeStrategy = AerospikeStrategy(strategy)
    AerospikeSyncRateLimiter(aerospikeClient, "test", strategy, 100 millis)
  }

  override def toFuture[A](v: Identity[A]): Future[A] = Future.successful(v)
}
