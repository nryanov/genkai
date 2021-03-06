# genkai
[![GitHub license](https://img.shields.io/github/license/nryanov/genkai)](https://github.com/nryanov/genkai/blob/master/LICENSE.txt)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.nryanov.genkai/genkai-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.nryanov.genkai/genkai-core_2.13)
[![Codecov](https://img.shields.io/codecov/c/github/nryanov/genkai)](https://img.shields.io/codecov/c/github/nryanov/genkai)
[![genkai CI](https://github.com/nryanov/genkai/actions/workflows/scala.yml/badge.svg?branch=master)](https://github.com/nryanov/genkai/actions/workflows/scala.yml)

Genkai (*jp. 限界, limit*) is a small library which allows you to limit requests or function calls.

## Installation
```scala
libraryDependencies ++= Seq(
  "com.nryanov.genkai" %% "<genkai-module>" % "[version]"
)
```

## Supported strategies
- Token bucket
- Fixed window
- Sliding window

Description of each of them can be found here: https://cloud.google.com/solutions/rate-limiting-strategies-techniques

All strategies are cost-based.

## Backends
The main structure and some abstractions are similar to [sttp](https://github.com/softwaremill/sttp). If you familiar with it then you should be comfortable with this library too :) 

There are integrations for [Monix](https://monix.io), [cats-effect](https://github.com/typelevel/cats-effect) and [ZIO](https://github.com/zio/zio).

It is not necessary to use these integrations. There are also built-in `Identity`, `Try`, `Either` and `Future` wrappers.

Backend | Client | 
------------ | ------------- 
Aerospike | [aerospike-client-java](https://github.com/aerospike/aerospike-client-java) 
Redis | [jedis](https://github.com/redis/jedis) <br> [lettuce](https://github.com/lettuce-io/lettuce-core) <br> [redisson](https://github.com/redisson/redisson)

**RateLimiter**

Class | Effect | 
------------ | ------------- 
`TryRateLimiter` | `scala.util.Try`
`EitherRateLimiter` | `Either` 
`AerospikeSyncRateLimiter` | None (`Identity`) 
`AerospikeCatsRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`AerospikeZioRateLimiter` | `zio.Task`
`JedisClusterSyncRateLimiter` | None (`Identity`)  
`JedisClusterCatsRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`JedisClusterCats3RateLimiter` | `F[_]: cats.effect.Sync` 
`JedisClusterZioRateLimiter` | `zio.Task` 
`JedisSyncRateLimiter (sentinel/pool)` | None (`Identity`)  
`JedisCatsRateLimiter (sentinel/pool)` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`JedisCats3RateLimiter (sentinel/pool)` | `F[_]: cats.effect.Sync` 
`JedisZioRateLimiter (sentinel/pool)` | `zio.Task` 
`LettuceSyncRateLimiter` | None (`Identity`)  
`LettuceAsyncRateLimiter` | `scala.concurrent.Future` 
`LettuceCatsRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`LettuceCatsAsyncRateLimiter` | `F[_]: cats.effect.Concurrent`
`LettuceCats3RateLimiter` | `F[_]: cats.effect.Sync` 
`LettuceCats3AsyncRateLimiter` | `F[_]: cats.effect.Async` 
`LettuceMonixAsyncRateLimiter` | `monix.eval.Task` 
`LettuceZioRateLimiter` | `zio.Task` 
`LettuceZioAsyncRateLimiter` | `zio.Task` 
`RedissonSyncRateLimiter` | None (`Identity`)  
`RedissonAsyncRateLimiter` | `scala.concurrent.Future` 
`RedissonCatsRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`RedissonCatsAsyncRateLimiter` | `F[_]: cats.effect.Concurrent`
`RedissonCats3RateLimiter` | `F[_]: cats.effect.Sync` 
`RedissonCats3AsyncRateLimiter` | `F[_]: cats.effect.Async` 
`RedissonMonixAsyncRateLimiter` | `monix.eval.Task` 
`RedissonZioRateLimiter` | `zio.Task` 
`RedissonZioAsyncRateLimiter` | `zio.Task`   

**ConcurrentRateLimiter**

Class | Effect | 
------------ | ------------- 
`TryConcurrentRateLimiter` | `scala.util.Try`
`EitherConcurrentRateLimiter` | `Either`
`JedisClusterSyncConcurrentRateLimiter` | None (`Identity`)  
`JedisClusterCatsConcurrentRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`JedisClusterCats3ConcurrentRateLimiter` | `F[_]: cats.effect.Sync` 
`JedisClusterZioConcurrentRateLimiter` | `zio.Task` 
`JedisSyncConcurrentRateLimiter (sentinel/pool)` | None (`Identity`)  
`JedisCatsConcurrentRateLimiter (sentinel/pool)` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`JedisCats3ConcurrentRateLimiter (sentinel/pool)` | `F[_]: cats.effect.Sync` 
`JedisZioConcurrentRateLimiter (sentinel/pool)` | `zio.Task` 
`LettuceSyncConcurrentRateLimiter` | None (`Identity`)  
`LettuceAsyncConcurrentRateLimiter` | `scala.concurrent.Future` 
`LettuceCatsConcurrentRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`LettuceCatsAsyncConcurrentRateLimiter` | `F[_]: cats.effect.Concurrent`
`LettuceCats3ConcurrentRateLimiter` | `F[_]: cats.effect.Sync` 
`LettuceCats3AsyncConcurrentRateLimiter` | `F[_]: cats.effect.Async` 
`LettuceMonixAsyncConcurrentRateLimiter` | `monix.eval.Task` 
`LettuceZioConcurrentRateLimiter` | `zio.Task` 
`LettuceZioAsyncConcurrentRateLimiter` | `zio.Task` 
`RedissonSyncConcurrentRateLimiter` | None (`Identity`)  
`RedissonAsyncConcurrentRateLimiter` | `scala.concurrent.Future` 
`RedissonCatsConcurrentRateLimiter` | `F[_]: cats.effect.Sync: cats.effect.ContextShift` 
`RedissonCatsAsyncConcurrentRateLimiter` | `F[_]: cats.effect.Concurrent`
`RedissonCats3ConcurrentRateLimiter` | `F[_]: cats.effect.Sync` 
`RedissonCats3AsyncConcurrentRateLimiter` | `F[_]: cats.effect.Async` 
`RedissonMonixAsyncConcurrentRateLimiter` | `monix.eval.Task` 
`RedissonZioConcurrentRateLimiter` | `zio.Task` 
`RedissonZioAsyncConcurrentRateLimiter` | `zio.Task`   

## Usage
```scala
import genkai._
import genkai.redis.jedis._
import redis.clients.jedis.JedisPool

object Main {
 def main(args: Array[String]): Unit = {
    val jedisPool = new JedisPool()
    val strategy = FixedWindow(10, Window.Minute) // 10 requests per 1 minute
    val rateLimiter: RateLimiter[Identity] = JedisSyncRateLimiter(jedisPool, strategy)
    
    val key: String = "my key"
    
    if (rateLimiter.acquire(key)) {
      // some logic
    } else {
      // handle `too many requests` 
    }
  }
}
```

More examples can be found in the [examples](examples/) folder.


Some methods of `RateLimiter[F[_]]` trait require implicit `Key` instance for your key object.
```scala
trait Key[A] {
  /**
   * @param value - value which will be used as unique (or not) identifier
   * @return - string representation of id
   */
  def convert(value: A): String
}
```

`Key[A]` allows you to control locks. For example, if you define custom `Key[String]` which always returns constant id
then it will work as a global rate limiter.   

There are a number of default implementation for basic types, but it is possible to define a new one if necessary.

