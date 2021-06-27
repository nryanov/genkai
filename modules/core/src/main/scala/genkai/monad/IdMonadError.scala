package genkai.monad

import genkai.Id

object IdMonadError extends MonadError[Id] {
  override def pure[A](value: A): Id[A] = value

  override def map[A, B](fa: Id[A])(f: A => B): Id[B] = f(fa)

  override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] = f(fa)

  override def tap[A, B](fa: Id[A])(f: A => Id[B]): Id[A] = {
    val r = fa
    f(r)
    r
  }

  override def raiseError[A](error: Throwable): Id[A] = throw error

  override def adaptError[A](fa: => Id[A])(
    pf: PartialFunction[Throwable, Throwable]
  ): Id[A] =
    try fa
    catch {
      case e: Throwable if pf.isDefinedAt(e) => raiseError(pf(e))
      case e: Throwable                      => raiseError(e)
    }

  override def mapError[A](fa: => Id[A])(f: Throwable => Throwable): Id[A] =
    try fa
    catch {
      case e: Throwable => raiseError(f(e))
    }

  override def handleError[A](fa: => Id[A])(pf: PartialFunction[Throwable, A]): Id[A] =
    try fa
    catch {
      case e: Throwable if pf.isDefinedAt(e) => pf(e)
      case e: Throwable                      => raiseError(pure(e))
    }

  override def handleErrorWith[A](fa: => Id[A])(
    pf: PartialFunction[Throwable, Id[A]]
  ): Id[A] =
    try fa
    catch {
      case e: Throwable if pf.isDefinedAt(e) => pf(e)
      case e: Throwable                      => raiseError(pure(e))
    }

  override def ifM[A](
    fcond: Id[Boolean]
  )(ifTrue: => Id[A], ifFalse: => Id[A]): Id[A] =
    if (fcond) ifTrue
    else ifFalse

  override def whenA[A](cond: Boolean)(f: => Id[A]): Id[Unit] =
    if (cond) f
    else unit

  override def void[A](fa: Id[A]): Id[Unit] = unit

  override def eval[A](f: => A): Id[A] = f

  override def guarantee[A](f: => Id[A])(g: => Id[Unit]): Id[A] =
    try f
    finally g

  override def bracket[A, B](acquire: => Id[A])(use: A => Id[B])(release: A => Id[Unit]): Id[B] = {
    var a = null.asInstanceOf[A]
    try {
      a = acquire
      use(a)
    } finally if (a != null) {
      release(a)
    }
  }
}
