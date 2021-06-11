package genkai.monad

import scala.util.{Failure, Success, Try}

object TryMonadError extends MonadError[Try] {
  override def pure[A](value: A): Try[A] = Try(value)

  override def map[A, B](fa: Try[A])(f: A => B): Try[B] = fa.map(f)

  override def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = fa.flatMap(f)

  override def tap[A, B](fa: Try[A])(f: A => Try[B]): Try[A] = fa.flatMap(r => f(r).map(_ => r))

  override def raiseError[A](error: Throwable): Try[A] = Failure(error)

  override def adaptError[A](fa: => Try[A])(pf: PartialFunction[Throwable, Throwable]): Try[A] =
    fa match {
      case Failure(exception) if pf.isDefinedAt(exception) => raiseError(pf(exception))
      case _                                               => fa
    }

  override def mapError[A](fa: => Try[A])(f: Throwable => Throwable): Try[A] = fa match {
    case Failure(exception) => raiseError(f(exception))
    case _                  => fa
  }

  override def handleError[A](fa: => Try[A])(pf: PartialFunction[Throwable, A]): Try[A] = fa match {
    case Failure(exception) if pf.isDefinedAt(exception) => eval(pf(exception))
    case _                                               => fa
  }

  override def handleErrorWith[A](fa: => Try[A])(pf: PartialFunction[Throwable, Try[A]]): Try[A] =
    fa match {
      case Failure(exception) if pf.isDefinedAt(exception) => suspend(pf(exception))
      case _                                               => fa
    }

  override def ifM[A](fcond: Try[Boolean])(ifTrue: => Try[A], ifFalse: => Try[A]): Try[A] =
    fcond.flatMap { flag =>
      if (flag) ifTrue
      else ifFalse
    }

  override def whenA[A](cond: Boolean)(f: => Try[A]): Try[Unit] =
    if (cond) f.map(_ => ())
    else unit

  override def void[A](fa: Try[A]): Try[Unit] = fa.map(_ => ())

  override def eval[A](f: => A): Try[A] = Try(f)

  override def guarantee[A](f: => Try[A])(g: => Try[Unit]): Try[A] =
    f match {
      case Failure(exception) => suspend(g).flatMap(_ => Failure(exception))
      case Success(value)     => suspend(g).map(_ => value)
    }
}
