package genkai.redis.jedis.cats3

import cats.effect.Sync
import genkai.ConcurrentStrategy
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.monad.syntax._
import genkai.redis.RedisConcurrentStrategy
import genkai.redis.jedis.JedisClusterConcurrentRateLimiter
import redis.clients.jedis.JedisCluster

class JedisClusterCats3ConcurrentRateLimiter[F[_]: Sync] private (
  cluster: JedisCluster,
  strategy: RedisConcurrentStrategy,
  closeClient: Boolean,
  acquireSha: String,
  releaseSha: String,
  permissionsSha: String,
  monad: Cats3BlockingMonadError[F]
) extends JedisClusterConcurrentRateLimiter[F](
      cluster = cluster,
      monad = monad,
      strategy = strategy,
      closeClient = closeClient,
      acquireSha = acquireSha,
      releaseSha = releaseSha,
      permissionsSha = permissionsSha
    ) {}

object JedisClusterCats3ConcurrentRateLimiter {
  def useClient[F[_]: Sync](
    cluster: JedisCluster,
    strategy: ConcurrentStrategy
  ): F[JedisClusterCats3ConcurrentRateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisConcurrentStrategy(strategy)

    monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.releaseLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }.map { case (acquireSha, releaseSha, permissionsSha) =>
      new JedisClusterCats3ConcurrentRateLimiter(
        cluster = cluster,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = acquireSha,
        releaseSha = releaseSha,
        permissionsSha = permissionsSha,
        monad = monad
      )
    }
  }
}
