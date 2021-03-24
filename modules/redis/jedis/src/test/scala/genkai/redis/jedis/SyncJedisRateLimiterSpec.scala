package genkai.redis.jedis

import java.time.Instant

import genkai.{Strategy, Window}

import scala.concurrent.duration._

class SyncJedisRateLimiterSpec extends JedisSpec {
  test("fixed window token acquiring") {
    withContainers { _ =>
      val limiter = SyncJedisRateLimiter(jedisPool, Strategy.FixedWindow(10, Window.Hour))

      assert(limiter.acquire("key"))
      assertEquals(limiter.permissions("key"), 9L)
    }
  }

  test("sliding window token acquiring") {
    withContainers { _ =>
      val limiter = SyncJedisRateLimiter(jedisPool, Strategy.SlidingWindow(5, Window.Hour))

      assert(limiter.acquire("key"))
      assertEquals(limiter.permissions("key"), 4L)
    }
  }

  test("token bucket token acquiring") {
    withContainers { _ =>
      val limiter = SyncJedisRateLimiter(jedisPool, Strategy.TokenBucket(3, 1, 10 minutes))

      assert(limiter.acquire("key"))
      assertEquals(limiter.permissions("key"), 2L)
    }
  }

  test("token bucket token refreshing") {
    withContainers { _ =>
      val limiter = SyncJedisRateLimiter(jedisPool, Strategy.TokenBucket(3, 1, 10 seconds))

      val instant = Instant.now()

      assert(limiter.acquire("key", instant))
      assert(limiter.acquire("key", instant.plusSeconds(1)))
      assert(limiter.acquire("key", instant.plusSeconds(2)))
      assert(!limiter.acquire("key", instant.plusSeconds(3)))
      assertEquals(limiter.permissions("key"), 0L)

      // should refresh bucket and return maxToken - 1
      assert(limiter.acquire("key", instant.plusSeconds(30)))
      assertEquals(limiter.permissions("key"), 2L)
    }
  }
}
