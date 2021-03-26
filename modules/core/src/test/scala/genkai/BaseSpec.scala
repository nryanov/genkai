package genkai

import java.time.Instant

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait BaseSpec[F[_]]
    extends AsyncFunSuite
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def rateLimiter(strategy: Strategy): RateLimiter[F]

  def toFuture[A](v: F[A]): Future[A]

  test("fixed window token acquiring") {
    val limiter = rateLimiter(Strategy.FixedWindow(10, Window.Hour))

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
    } yield {
      assert(r1)
      r1 shouldBe true
      r2 shouldBe 9L
    }
  }

  test("sliding window token acquiring") {
    val limiter = rateLimiter(Strategy.SlidingWindow(5, Window.Hour))

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 4L
    }
  }

  test("token bucket token acquiring") {
    val limiter = rateLimiter(Strategy.TokenBucket(3, 1, 10 minutes))

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 2L
    }
  }

  test("token bucket token refreshing") {
    val limiter = rateLimiter(Strategy.TokenBucket(3, 1, 10 seconds))

    val instant = Instant.now()

    for {
      r1 <- toFuture(limiter.acquire("key", instant))
      r2 <- toFuture(limiter.acquire("key", instant.plusSeconds(1)))
      r3 <- toFuture(limiter.acquire("key", instant.plusSeconds(2)))
      r4 <- toFuture(limiter.acquire("key", instant.plusSeconds(3)))
      r5 <- toFuture(limiter.permissions("key"))
      // should refresh bucket and return maxToken - 1
      r6 <- toFuture(limiter.acquire("key", instant.plusSeconds(30)))
      r7 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe true
      r3 shouldBe true
      r4 shouldBe false
      r5 shouldBe 0L
      r6 shouldBe true
      r7 shouldBe 2L
    }
  }
}
