package genkai

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait RateLimiterBaseSpec[F[_]] extends AsyncFunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def rateLimiter(strategy: Strategy): RateLimiter[F]

  def toFuture[A](v: F[A]): Future[A]

  for (
    strategy <- Seq(
      Strategy.TokenBucket(10, 1, 10 minutes),
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should return not used permissions for not existing records: $strategy") {
    val limiter = rateLimiter(strategy)

    for {
      permissions <- toFuture(limiter.permissions("key"))
    } yield permissions shouldBe 10L
  }

  for (
    strategy <- Seq(
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should return 0 permissions for request in the past: $strategy") {
    val limiter = rateLimiter(strategy)
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    for {
      _ <- toFuture(limiter.acquire("key", instant))
      permissions <- toFuture(limiter.permissions("key", instant.minus(2, ChronoUnit.HOURS)))
    } yield permissions shouldBe 0L
  }

  for (
    strategy <- Seq(
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should return max permissions for request in the next window: $strategy") {
    val limiter = rateLimiter(strategy)
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    for {
      _ <- toFuture(limiter.acquire("key", instant))
      permissions <- toFuture(limiter.permissions("key", instant.plus(2, ChronoUnit.HOURS)))
    } yield permissions shouldBe 10L
  }

  for (
    strategy <- Seq(
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should not acquire token if current timestamp is in the past: $strategy") {
    val limiter = rateLimiter(strategy)
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    for {
      acquire <- toFuture(limiter.acquire("key"))
      acquirePast <- toFuture(limiter.acquire("key", instant.minus(2, ChronoUnit.HOURS)))
      permissions <- toFuture(limiter.permissions("key"))
    } yield {
      acquire shouldBe true
      acquirePast shouldBe false
      permissions shouldBe 9L
    }
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
      r3 <- toFuture(limiter.acquire("key"))
      r4 <- toFuture(limiter.permissions("key"))
    } yield {
      r1 shouldBe true
      r2 shouldBe 9L
      r3 shouldBe true
      r4 shouldBe 8L
    }
  }

  for (
    strategy <- Seq(
      Strategy.TokenBucket(10, 1, 1.hour),
      Strategy.FixedWindow(10, Window.Hour),
      Strategy.SlidingWindow(10, Window.Hour)
    )
  ) yield test(s"should acquire (return state) single token: $strategy") {
    val limiter = rateLimiter(strategy)
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS) // start of the day for consistency

    val (resetFirst, resetSecond) = strategy match {
      case Strategy.TokenBucket(_, _, refillDelay) =>
        (
          instant.getEpochSecond + refillDelay.toSeconds,
          instant.getEpochSecond + refillDelay.toSeconds
        )
      case Strategy.FixedWindow(_, window) =>
        (instant.getEpochSecond + window.size, instant.getEpochSecond + window.size)
      case Strategy.SlidingWindow(_, window) =>
        (
          instant.getEpochSecond + window.size,
          // window slide
          instant.plus(30, ChronoUnit.MINUTES).getEpochSecond + window.size
        )
    }

    val expectedState = RateLimiter.State(
      limit = 10L,
      remaining = 9L,
      isAllowed = true,
      reset = resetFirst,
      resetAfter = resetFirst - instant.getEpochSecond,
      key = "key"
    )

    val secondExpectedState = expectedState.copy(
      remaining = 8L,
      reset = resetSecond,
      resetAfter = resetSecond - instant.plus(30, ChronoUnit.MINUTES).getEpochSecond
    )

    for {
      state <- toFuture(limiter.acquireS("key", instant))
      permissions <- toFuture(limiter.permissions("key", instant))
      secondState <- toFuture(limiter.acquireS("key", instant.plus(30, ChronoUnit.MINUTES)))
      secondPermissions <- toFuture(
        limiter.permissions("key", instant.plus(30, ChronoUnit.MINUTES))
      )
    } yield {
      state shouldBe expectedState
      permissions shouldBe 9L
      secondState shouldBe secondExpectedState
      secondPermissions shouldBe 8L
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

  for {
    window <- Seq(Window.Second, Window.Minute, Window.Hour, Window.Day)
  } yield test(
    s"[FixedWindow][${window.unit.toString}] should correctly starts next window with fresh tokens"
  ) {
    val limiter = rateLimiter(Strategy.FixedWindow(10, window))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val chronoUnit = window match {
      case Window.Second => ChronoUnit.SECONDS
      case Window.Minute => ChronoUnit.MINUTES
      case Window.Hour   => ChronoUnit.HOURS
      case Window.Day    => ChronoUnit.DAYS
    }

    for {
      p1 <- toFuture(limiter.permissions("key", instant))
      a1 <- toFuture(limiter.acquire("key", instant))
      a2 <- toFuture(limiter.acquire("key", instant))
      p2 <- toFuture(limiter.permissions("key", instant))
      // begin next window
      a3 <- toFuture(limiter.acquire("key", instant.plus(1, chronoUnit)))
      p3 <- toFuture(limiter.permissions("key", instant.plus(1, chronoUnit)))
    } yield {
      p1 shouldBe 10L
      a1 shouldBe true
      a2 shouldBe true
      p2 shouldBe 8L
      a3 shouldBe true
      p3 shouldBe 9L
    }
  }

  for {
    window <- Seq(Window.Minute, Window.Hour, Window.Day)
  } yield test(
    s"[SlidingWindow][${window.unit.toString}] should clean up old buckets and correctly count used tokens"
  ) {
    val limiter = rateLimiter(Strategy.SlidingWindow(10, window))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val (chronoUnit, firstStep, secondStep, thirdStep) = window match {
      case Window.Second => (ChronoUnit.SECONDS, 1, 1, 1) // not used
      case Window.Minute => (ChronoUnit.SECONDS, 10, 20, 70)
      case Window.Hour   => (ChronoUnit.MINUTES, 10, 20, 70)
      case Window.Day    => (ChronoUnit.HOURS, 1, 2, 25)
    }

    for {
      a1 <- toFuture(limiter.acquire("key", instant.plus(firstStep, chronoUnit)))
      a2 <- toFuture(limiter.acquire("key", instant.plus(secondStep, chronoUnit)))
      p1 <- toFuture(limiter.permissions("key", instant.plus(secondStep, chronoUnit)))
      a3 <- toFuture(limiter.acquire("key", instant.plus(thirdStep, chronoUnit)))
      p2 <- toFuture(limiter.permissions("key", instant.plus(thirdStep, chronoUnit)))
    } yield {
      a1 shouldBe true
      a2 shouldBe true
      p1 shouldBe 8L
      a3 shouldBe true
      p2 shouldBe 8L
    }
  }

  test("[SlidingWindow] should not add extra acquiring if limit is reached") {
    val limiter = rateLimiter(Strategy.SlidingWindow(1, Window.Minute))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    for {
      r1 <- toFuture(limiter.acquire("key", instant))
      r2 <- toFuture(limiter.permissions("key", instant))
      r3 <- toFuture(limiter.acquire("key", instant))
      r4 <- toFuture(limiter.acquire("key", instant.plusSeconds(61)))
    } yield {
      r1 shouldBe true
      r2 shouldBe 0L
      r3 shouldBe false
      r4 shouldBe true
    }
  }

  test("[TokenBucket] should refresh tokens after delay") {
    val limiter = rateLimiter(Strategy.TokenBucket(3, 1, 10 seconds))

    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    for {
      r1 <- toFuture(limiter.acquire("key", instant))
      r2 <- toFuture(limiter.acquire("key", instant.plusSeconds(1)))
      r3 <- toFuture(limiter.acquire("key", instant.plusSeconds(2)))
      r4 <- toFuture(limiter.acquire("key", instant.plusSeconds(3)))
      r5 <- toFuture(limiter.permissions("key", instant.plusSeconds(3)))
      // should refresh bucket and return maxToken - 1
      r6 <- toFuture(limiter.acquire("key", instant.plusSeconds(30)))
      r7 <- toFuture(limiter.permissions("key", instant.plusSeconds(30)))
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

  test(
    s"[SlidingWindow] should not acquire token if request come from the past and return correct state"
  ) {
    val limiter = rateLimiter(Strategy.SlidingWindow(10, Window.Hour))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val expectedState = RateLimiter.State(
      limit = 10,
      remaining = 9,
      isAllowed = true,
      reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
      resetAfter = 3600,
      key = "key"
    )

    for {
      state1 <- toFuture(limiter.acquireS("key", instant))
      state2 <- toFuture(limiter.acquireS("key", instant.minus(2, ChronoUnit.HOURS)))
    } yield {
      state1 shouldBe expectedState
      state2 shouldBe expectedState.copy(
        // instant -- is the lowest correct timestamp
        // reset = instant + 1 hour
        // reset after -- is relative to passed timestamp which is (instant - 2 hours) =>
        // resetAfter = (instant + 1 hour) - (instant - 2 hours)
        resetAfter =
          instant.plus(1, ChronoUnit.HOURS).getEpochSecond - instant.minus(2, ChronoUnit.HOURS).getEpochSecond,
        isAllowed = false
      )
    }
  }

  test(
    s"[FixedWindow] should not acquire token if request come from the past and return correct state"
  ) {
    val limiter = rateLimiter(Strategy.FixedWindow(10, Window.Hour))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val expectedState = RateLimiter.State(
      limit = 10,
      remaining = 9,
      isAllowed = true,
      reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
      resetAfter = 3600,
      key = "key"
    )

    for {
      state1 <- toFuture(limiter.acquireS("key", instant))
      state2 <- toFuture(limiter.acquireS("key", instant.minus(2, ChronoUnit.HOURS)))
    } yield {
      state1 shouldBe expectedState
      state2 shouldBe expectedState.copy(
        reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
        resetAfter =
          instant.plus(1, ChronoUnit.HOURS).getEpochSecond - instant.minus(2, ChronoUnit.HOURS).getEpochSecond,
        isAllowed = false
      )
    }
  }

  test("[TokenBucket] should return correct remaining count if cost > limit") {
    val limiter = rateLimiter(Strategy.TokenBucket(1, 1, 1.hour))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val expectedState = RateLimiter.State(
      limit = 1,
      remaining = 1,
      isAllowed = false,
      reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
      resetAfter = 3600,
      key = "key"
    )

    for {
      state <- toFuture(limiter.acquireS("key", instant, 2))
    } yield state shouldBe expectedState
  }

  test(
    s"[SlidingWindow] should return correct remaining count if cost > limit"
  ) {
    val limiter = rateLimiter(Strategy.SlidingWindow(1, Window.Hour))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val expectedState = RateLimiter.State(
      limit = 1,
      remaining = 1,
      isAllowed = false,
      reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
      resetAfter = 3600,
      key = "key"
    )

    for {
      state <- toFuture(limiter.acquireS("key", instant, 2))
    } yield state shouldBe expectedState
  }

  test(
    s"[FixedWindow] should return correct remaining count if cost > limit"
  ) {
    val limiter = rateLimiter(Strategy.FixedWindow(1, Window.Hour))
    val instant = Instant.now().truncatedTo(ChronoUnit.DAYS)

    val expectedState = RateLimiter.State(
      limit = 1,
      remaining = 1,
      isAllowed = false,
      reset = instant.plus(1, ChronoUnit.HOURS).getEpochSecond,
      resetAfter = 3600,
      key = "key"
    )

    for {
      state <- toFuture(limiter.acquireS("key", instant, 2))
    } yield state shouldBe expectedState
  }
}
