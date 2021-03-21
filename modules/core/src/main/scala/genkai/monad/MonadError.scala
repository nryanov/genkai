package genkai.monad

trait MonadError[F[_]] {
  def unit: F[Unit] = pure(())

  def pure[A](value: A): F[A]

  def map[A, B](fa: F[A])(f: A => B): F[B]

  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def raiseError[A](error: Throwable): F[A]

  def adaptError[A](fa: F[A])(pf: PartialFunction[Throwable, Throwable]): F[A]

  def handleError[A](fa: F[A])(pf: PartialFunction[Throwable, A]): F[A]

  def handleErrorWith[A](fa: F[A])(pf: PartialFunction[Throwable, F[A]]): F[A]

  def ifA[A](fcond: F[Boolean])(ifTrue: => F[A], ifFalse: => F[A]): F[A]

  def whenA[A](cond: Boolean)(f: => F[A]): F[Unit]

  def void[A](fa: F[A]): F[Unit]

  def eval[A](f: => A): F[A]

  def suspend[A](fa: => F[A]): F[A] = flatten(eval(fa))

  def flatten[A](fa: F[F[A]]): F[A] = flatMap(fa)(v => identity(v))

  def guarantee[A](f: F[A])(g: => F[Unit]): F[A]
}
