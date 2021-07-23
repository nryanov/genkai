package genkai.effect.cats

import cats.effect.Concurrent
import genkai.monad.MonadAsyncError

final class CatsMonadAsyncError[F[_]](implicit F: Concurrent[F]) extends MonadAsyncError[F] {
  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] =
    F.async_(k)

  override def cancelable[A](k: (Either[Throwable, A] => Unit) => () => F[Unit]): F[A] =
    F.cancelable(k.andThen(_.apply()))

  override def pure[A](value: A): F[A] = F.pure(value)

  override def map[A, B](fa: => F[A])(f: A => B): F[B] = F.map(fa)(f)

  override def flatMap[A, B](fa: => F[A])(f: A => F[B]): F[B] = F.flatMap(fa)(f)

  override def tap[A, B](fa: => F[A])(f: A => F[B]): F[A] = F.flatTap(fa)(f)

  override def raiseError[A](error: Throwable): F[A] = F.raiseError(error)

  override def adaptError[A](fa: => F[A])(pf: PartialFunction[Throwable, Throwable]): F[A] =
    F.adaptError(fa)(pf)

  override def mapError[A](fa: => F[A])(f: Throwable => Throwable): F[A] =
    F.adaptError(fa) { case err: Throwable =>
      f(err)
    }

  override def handleError[A](fa: => F[A])(pf: PartialFunction[Throwable, A]): F[A] =
    F.handleError(fa)(pf)

  override def handleErrorWith[A](fa: => F[A])(pf: PartialFunction[Throwable, F[A]]): F[A] =
    F.handleErrorWith(fa)(pf)

  override def ifM[A](fcond: => F[Boolean])(ifTrue: => F[A], ifFalse: => F[A]): F[A] =
    F.ifM(fcond)(ifTrue, ifFalse)

  override def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] =
    F.whenA(cond)(f)

  override def void[A](fa: => F[A]): F[Unit] = F.void(fa)

  override def eval[A](f: => A): F[A] = F.delay(f)

  override def unit: F[Unit] = F.unit

  override def suspend[A](fa: => F[A]): F[A] = F.defer(fa)

  override def flatten[A](fa: => F[F[A]]): F[A] = F.flatten(fa)

  override def guarantee[A](f: => F[A])(g: => F[Unit]): F[A] = F.guarantee(f)(g)

  override def bracket[A, B](acquire: => F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
    F.bracket(acquire)(use)(release)
}
