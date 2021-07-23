package genkai.redis.jedis.cats3

import cats.effect.Sync
import genkai.Strategy
import genkai.effect.cats3.Cats3BlockingMonadError
import genkai.redis.RedisStrategy
import genkai.monad.syntax._
import genkai.redis.jedis.JedisClusterRateLimiter
import redis.clients.jedis.JedisCluster

class JedisClusterCats3RateLimiter[F[_]: Sync] private (
  cluster: JedisCluster,
  strategy: RedisStrategy,
  closeClient: Boolean,
  acquireSha: String,
  permissionsSha: String,
  monad: Cats3BlockingMonadError[F]
) extends JedisClusterRateLimiter[F](
      cluster,
      monad,
      strategy,
      closeClient,
      acquireSha,
      permissionsSha
    ) {}

object JedisClusterCats3RateLimiter {
  def useClient[F[_]: Sync](
    cluster: JedisCluster,
    strategy: Strategy
  ): F[JedisClusterCats3RateLimiter[F]] = {
    implicit val monad: Cats3BlockingMonadError[F] = new Cats3BlockingMonadError[F]()

    val redisStrategy = RedisStrategy(strategy)

    monad.eval {
      (
        cluster.scriptLoad(redisStrategy.acquireLuaScript, "script"),
        cluster.scriptLoad(redisStrategy.permissionsLuaScript, "script")
      )
    }.map { case (acquireSha, permissionsSha) =>
      new JedisClusterCats3RateLimiter(
        cluster = cluster,
        strategy = redisStrategy,
        closeClient = false,
        acquireSha = acquireSha,
        permissionsSha = permissionsSha,
        monad = monad
      )
    }
  }
}
