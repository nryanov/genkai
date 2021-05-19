package genkai

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait ConcurrentRateLimiterBaseSpec[F[_]]
    extends AsyncFunSuite
    with Matchers
    with EitherValues
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def concurrentRateLimiter(strategy: ConcurrentStrategy): ConcurrentRateLimiter[F]

  def toFuture[A](v: F[A]): Future[A]

  test("acquire and automatically release slot") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(10, 5.minutes))
    val instant = Instant.now()

    for {
      result <- toFuture(
        limiter.use("key", instant)(
          limiter.monadError.pure(true)
        )
      )
      permissions <- toFuture(limiter.permissions("key", instant))
    } yield {
      result.value shouldBe true
      permissions shouldBe 10L
    }
  }

  test("return ConcurrentLimitExhausted when there is no more available slots") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(1, 5.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      result <- toFuture(
        limiter.use("key", instant)(
          limiter.monadError.pure(true)
        )
      )
      permissions <- toFuture(limiter.permissions("key", instant))
    } yield {
      a1 shouldBe true
      result.left.value shouldBe ConcurrentLimitExhausted("key")
      permissions shouldBe 0L
    }
  }

  test("release without acquiring") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(10, 5.minutes))
    val instant = Instant.now()

    for {
      p1 <- toFuture(limiter.permissions("key", instant))
      r1 <- toFuture(limiter.release("key", instant))
      p2 <- toFuture(limiter.permissions("key", instant))
    } yield {
      p1 shouldBe 10L
      r1 shouldBe false
      p2 shouldBe 10L
    }
  }

  test("manual acquire and manual release slot") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(10, 5.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      p1 <- toFuture(limiter.permissions("key", instant))
      r1 <- toFuture(limiter.release("key", instant))
      p2 <- toFuture(limiter.permissions("key", instant))
    } yield {
      a1 shouldBe true
      p1 shouldBe 9L
      r1 shouldBe true
      p2 shouldBe 10L
    }
  }

  test("return false if there is no available slot") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(1, 5.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      a2 <- toFuture(limiter.acquire("key", instant))
    } yield {
      a1 shouldBe true
      a2 shouldBe false
    }
  }

  test("automatically release expired slots when acquire a new one") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(1, 1.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      p1 <- toFuture(limiter.permissions("key", instant))
      a2 <- toFuture(limiter.acquire("key", instant.plus(2, ChronoUnit.MINUTES)))
    } yield {
      a1 shouldBe true
      p1 shouldBe 0L
      a2 shouldBe true
    }
  }

  test("automatically release expired slots when release") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(3, 5.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      a2 <- toFuture(limiter.acquire("key", instant.plus(3, ChronoUnit.MINUTES)))
      a3 <- toFuture(limiter.acquire("key", instant.plus(4, ChronoUnit.MINUTES)))
      p1 <- toFuture(limiter.permissions("key", instant.plus(3, ChronoUnit.MINUTES)))
      r1 <- toFuture(limiter.release("key", instant.plus(10, ChronoUnit.MINUTES)))
      p2 <- toFuture(limiter.permissions("key", instant.plus(10, ChronoUnit.MINUTES)))
    } yield {
      a1 shouldBe true
      a2 shouldBe true
      a3 shouldBe true
      p1 shouldBe 0L
      r1 shouldBe true
      p2 shouldBe 3L
    }
  }

  test("automatically release expired slots when get available permissions") {
    val limiter = concurrentRateLimiter(ConcurrentStrategy.Default(3, 5.minutes))
    val instant = Instant.now()

    for {
      a1 <- toFuture(limiter.acquire("key", instant))
      a2 <- toFuture(limiter.acquire("key", instant.plus(3, ChronoUnit.MINUTES)))
      a3 <- toFuture(limiter.acquire("key", instant.plus(4, ChronoUnit.MINUTES)))
      p1 <- toFuture(limiter.permissions("key", instant.plus(3, ChronoUnit.MINUTES)))
      p2 <- toFuture(limiter.permissions("key", instant.plus(10, ChronoUnit.MINUTES)))
    } yield {
      a1 shouldBe true
      a2 shouldBe true
      a3 shouldBe true
      p1 shouldBe 0L
      p2 shouldBe 3L
    }
  }
}
