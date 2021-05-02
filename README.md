# genkai
[![GitHub license](https://img.shields.io/github/license/nryanov/genkai)](https://github.com/nryanov/genkai/blob/master/LICENSE.txt)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.nryanov.genkai/genkai-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.nryanov.genkai/genkai-core_2.13)

Genkai (*jp. 限界, limit*) is a small library which allows you to limit requests or function calls.

## Supported strategies
- Token bucket
- Fixed window
- Sliding window

Description of each of them can be found here: https://cloud.google.com/solutions/rate-limiting-strategies-techniques

## Backends
Currently, only Redis is supported as a backend.
Available Redis clients:
- [Jedis](https://github.com/redis/jedis)  
- [Lettuce](https://github.com/lettuce-io/lettuce-core)  
- [Redisson](https://github.com/redisson/redisson)  

## Effects
The main structure and some abstractions are similar to [sttp](https://github.com/softwaremill/sttp). If you familiar with it then you should be comfortable with this library too :) 

There are integrations for [Monix](https://monix.io), [cats-effect](https://github.com/typelevel/cats-effect) and [ZIO](https://github.com/zio/zio).

It is not necessary to use these integrations. There are also built-in `Identity`, `Try`, `Either` and `Future` wrappers.

## Installation
Each module is published for Scala 2.12 and 2.13 to Maven Central, so just add the following to your build.sbt:
```scala
libraryDependencies ++= Seq(
  "com.nryanov.genkai" %% "genkai-core" % "[version]",
  "com.nryanov.genkai" %% "<genkai-module>" % "[version]"
)
```

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
