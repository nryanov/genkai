package genkai

import genkai.monad.MonadError

trait RateLimiter[F[_]] {
  def permissions[A: Key](key: A): F[Int]

  def reset[A: Key](key: A): F[Unit]

  def isAllowed[A: Key](key: A): F[Boolean]

  def close(): F[Unit]

  def withPermission[A: Key, B](key: A)(task: => F[B]): F[B]

  protected def monadError: MonadError[F]
}
