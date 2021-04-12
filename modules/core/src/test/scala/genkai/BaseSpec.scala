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

  for (
    strategy <- Seq(
      Strategy.TokenBucket(10, 1, 10 minutes),
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should return not used permissions: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      permissions <- toFuture(limiter.permissions("key"))
    } yield permissions shouldBe 10L
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(10, 1, 10 minutes),
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should acquire single token: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 9L
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(1, 1, 10 minutes),
      Strategy.FixedWindow(1, Window.Hour),
      Strategy.SlidingWindow(1, Window.Hour)
    )
  ) yield test(s"should not acquire token if limit is exhausted: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
      r3 <- toFuture(limiter.acquire("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 0L
      r3 shouldBe false
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(10, 1, 10 minutes),
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should reset tokens: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
      _ <- toFuture(limiter.reset("key"))
      r3 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 9L
      r3 shouldBe 10L
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(1, 1, 10 minutes),
      Strategy.FixedWindow(1, Window.Hour),
      Strategy.SlidingWindow(1, Window.Hour)
    )
  ) yield test(s"should acquire last token: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      r1 <- toFuture(limiter.acquire("key"))
      r2 <- toFuture(limiter.permissions("key"))
      r3 <- toFuture(limiter.acquire("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 0L
      r3 shouldBe false
    }
  }

  test("[SlidingWindow] should not add extra acquiring if limit is reached") {
    val limiter = rateLimiter(Strategy.SlidingWindow(1, Window.Minute))
    val instant = Instant.now()

    for {
      r1 <- toFuture(limiter.acquire("key", instant))
      r2 <- toFuture(limiter.permissions("key"))
      r3 <- toFuture(limiter.acquire("key", instant))
      r4 <- toFuture(limiter.acquire("key", instant.plusSeconds(61)))
    } yield {
      r1 shouldBe true
      r2 shouldBe 0L
      r3 shouldBe false
      r4 shouldBe true
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(3, 1, 10 minutes),
      Strategy.FixedWindow(3, Window.Hour),
      Strategy.SlidingWindow(3, Window.Hour)
    )
  ) yield test(s"should acquire token if cost <= maxTokens: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      r1 <- toFuture(limiter.acquire("key", 3))
      r2 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 0L
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(3, 1, 10 minutes),
      Strategy.FixedWindow(3, Window.Hour),
      Strategy.SlidingWindow(3, Window.Hour)
    )
  )
    yield test(
      s"should not acquire token if cost > maxTokens and should not reduce remaining tokens: $strategy"
    ) {
      val limiter = rateLimiter(strategy)

      for {
        r1 <- toFuture(limiter.acquire("key", 4))
        r2 <- toFuture(limiter.permissions("key"))
      } yield {
        r1 shouldBe false
        r2 shouldBe 3L
      }
    }

  test("[TokenBucket] should refresh tokens after delay") {
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
