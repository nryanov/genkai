package genkai.aerospike

import genkai.{Id, RateLimiter, Strategy}

import scala.concurrent.Future
import scala.concurrent.duration._

class AerospikeSyncRateLimiterSpec extends AerospikeSpecForAll[Id] {
  override def rateLimiter(strategy: Strategy): RateLimiter[Id] = {
    aerospikeStrategy = AerospikeStrategy(strategy)
    AerospikeSyncRateLimiter(aerospikeClient, "test", strategy, 100 millis)
  }

  override def toFuture[A](v: Id[A]): Future[A] = Future.successful(v)
}
