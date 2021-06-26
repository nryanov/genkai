package genkai.monad

import scala.util.{Failure, Success, Try}

object EitherMonadError extends MonadError[Either[Throwable, *]] {
  override def pure[A](value: A): Either[Throwable, A] = Right(value)

  override def map[A, B](fa: Either[Throwable, A])(f: A => B): Either[Throwable, B] = fa.map(f)

  override def flatMap[A, B](fa: Either[Throwable, A])(
    f: A => Either[Throwable, B]
  ): Either[Throwable, B] = fa.flatMap(f)

  override def tap[A, B](fa: Either[Throwable, A])(
    f: A => Either[Throwable, B]
  ): Either[Throwable, A] = fa.flatMap(r => f(r).map(_ => r))

  override def raiseError[A](error: Throwable): Either[Throwable, A] = Left(error)

  override def adaptError[A](
    fa: => Either[Throwable, A]
  )(pf: PartialFunction[Throwable, Throwable]): Either[Throwable, A] =
    fa match {
      case Left(value) if pf.isDefinedAt(value) => raiseError(pf(value))
      case _                                    => fa
    }

  override def mapError[A](
    fa: => Either[Throwable, A]
  )(f: Throwable => Throwable): Either[Throwable, A] =
    fa match {
      case Left(value) => raiseError(f(value))
      case _           => fa
    }

  override def handleError[A](
    fa: => Either[Throwable, A]
  )(pf: PartialFunction[Throwable, A]): Either[Throwable, A] =
    fa match {
      case Left(value) if pf.isDefinedAt(value) => eval(pf(value))
      case _                                    => fa
    }

  override def handleErrorWith[A](
    fa: => Either[Throwable, A]
  )(pf: PartialFunction[Throwable, Either[Throwable, A]]): Either[Throwable, A] =
    fa match {
      case Left(value) if pf.isDefinedAt(value) => suspend(pf(value))
      case _                                    => fa
    }

  override def ifM[A](
    fcond: Either[Throwable, Boolean]
  )(ifTrue: => Either[Throwable, A], ifFalse: => Either[Throwable, A]): Either[Throwable, A] =
    fcond.flatMap { flag =>
      if (flag) ifTrue
      else ifFalse
    }

  override def whenA[A](cond: Boolean)(f: => Either[Throwable, A]): Either[Throwable, Unit] =
    if (cond) f.map(_ => ())
    else unit

  override def void[A](fa: Either[Throwable, A]): Either[Throwable, Unit] = fa.map(_ => ())

  override def eval[A](f: => A): Either[Throwable, A] = Try(f).toEither

  override def guarantee[A](
    f: => Either[Throwable, A]
  )(g: => Either[Throwable, Unit]): Either[Throwable, A] = {
    def tryE = Try(g) match {
      case Failure(exception) => Left(exception)
      case Success(value)     => value
    }

    // older scala versions are not supported, so we can assume that either is right-biased
    f match {
      case Left(value)  => tryE.flatMap(_ => Left(value))
      case Right(value) => tryE.map(_ => value)
    }
  }

  override def bracket[A, B](acquire: => Either[Throwable, A])(use: A => Either[Throwable, B])(
    release: A => Either[Throwable, Unit]
  ): Either[Throwable, B] = {
    def tryUse(a: A): Either[Throwable, B] = Try(use(a)) match {
      case Failure(exception) => Left(exception)
      case Success(value)     => value
    }

    def tryRelease(a: A): Either[Throwable, Unit] = Try(release(a)) match {
      case Failure(exception) => Left(exception)
      case Success(value)     => value
    }

    acquire.flatMap { resource =>
      tryUse(resource) match {
        case Left(error)   => tryRelease(resource).flatMap(_ => Left(error))
        case Right(result) => tryRelease(resource).map(_ => result)
      }
    }
  }
}
