package genkai.monad

package object syntax {
  implicit final class MonadErrorOps[F[_], A](fa: => F[A]) {
    def map[B](f: A => B)(implicit F: MonadError[F]): F[B] = F.map(fa)(f)

    def flatMap[B](f: A => F[B])(implicit F: MonadError[F]): F[B] = F.flatMap(fa)(f)

    def >>[B](f: A => F[B])(implicit F: MonadError[F]): F[B] = F.flatMap(fa)(f)

    // zipRight
    def *>[B](f: => F[B])(implicit F: MonadError[F]): F[B] = F.flatMap(fa)(_ => f)

    def tap[B](f: A => F[B])(implicit F: MonadError[F]): F[A] = F.tap(fa)(f)

    def handleError(pf: PartialFunction[Throwable, A])(implicit F: MonadError[F]): F[A] =
      F.handleError(fa)(pf)

    def handleErrorWith(pf: PartialFunction[Throwable, F[A]])(implicit F: MonadError[F]): F[A] =
      F.handleErrorWith(fa)(pf)

    def adaptError(pf: PartialFunction[Throwable, Throwable])(implicit F: MonadError[F]): F[A] =
      F.adaptError(fa)(pf)

    def mapError(f: Throwable => Throwable)(implicit F: MonadError[F]): F[A] =
      F.mapError(fa)(f)

    def guarantee(g: => F[Unit])(implicit F: MonadError[F]): F[A] = F.guarantee(fa)(g)

    def void(implicit F: MonadError[F]): F[Unit] = F.void(fa)
  }

  implicit final class MonadErrorValueOps[F[_], A](private val v: A) extends AnyVal {
    def pure(implicit F: MonadError[F]): F[A] = F.pure(v)
  }
}
