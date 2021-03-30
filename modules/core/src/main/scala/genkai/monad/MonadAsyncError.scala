package genkai.monad

trait MonadAsyncError[F[_]] extends MonadError[F] {
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]

  def cancelable[A](k: (Either[Throwable, A] => Unit) => (() => F[Unit])): F[A]
}
