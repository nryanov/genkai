package genkai.monad

import scala.util.Failure
import scala.concurrent.{ExecutionContext, Future, Promise}

class FutureMonad(implicit ex: ExecutionContext) extends MonadAsyncError[Future] {
  override def pure[A](value: A): Future[A] = Future.successful(value)

  override def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)

  override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)

  override def raiseError[A](error: Throwable): Future[A] = Future.failed(error)

  override def adaptError[A](fa: Future[A])(pf: PartialFunction[Throwable, Throwable]): Future[A] =
    fa.transformWith {
      case Failure(exception) if pf.isDefinedAt(exception) => raiseError(pf(exception))
      case _                                               => fa
    }

  override def handleError[A](fa: Future[A])(pf: PartialFunction[Throwable, A]): Future[A] =
    fa.recover(pf)

  override def handleErrorWith[A](fa: Future[A])(pf: PartialFunction[Throwable, Future[A]]): Future[A] =
    fa.recoverWith(pf)

  override def ifA[A](fcond: Future[Boolean])(ifTrue: => Future[A], ifFalse: => Future[A]): Future[A] =
    fcond.flatMap { flag =>
      if (flag) ifTrue
      else ifFalse
    }

  override def whenA[A](cond: Boolean)(f: => Future[A]): Future[Unit] =
    if (cond) f.map(_ => ())
    else unit

  override def void[A](fa: Future[A]): Future[Unit] = fa.map(_ => ())

  override def eval[A](f: => A): Future[A] = Future(f)

  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
    val p = Promise[A]()

    k {
      case Left(value)  => p.failure(value)
      case Right(value) => p.success(value)
    }

    p.future
  }
}
