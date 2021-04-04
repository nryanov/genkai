package genkai.monad

import genkai.Identity

object IdMonadError extends MonadError[Identity] {
  override def pure[A](value: A): Identity[A] = value

  override def map[A, B](fa: Identity[A])(f: A => B): Identity[B] = f(fa)

  override def flatMap[A, B](fa: Identity[A])(f: A => Identity[B]): Identity[B] = f(fa)

  override def tap[A, B](fa: Identity[A])(f: A => Identity[B]): Identity[A] = {
    val r = fa
    f(r)
    r
  }

  override def raiseError[A](error: Throwable): Identity[A] = throw error

  override def adaptError[A](fa: Identity[A])(
    pf: PartialFunction[Throwable, Throwable]
  ): Identity[A] = fa

  override def mapError[A](fa: Identity[A])(f: Throwable => Throwable): Identity[A] = fa

  override def handleError[A](fa: Identity[A])(pf: PartialFunction[Throwable, A]): Identity[A] = fa

  override def handleErrorWith[A](fa: Identity[A])(
    pf: PartialFunction[Throwable, Identity[A]]
  ): Identity[A] = fa

  override def ifA[A](
    fcond: Identity[Boolean]
  )(ifTrue: => Identity[A], ifFalse: => Identity[A]): Identity[A] =
    if (fcond) ifTrue
    else ifFalse

  override def whenA[A](cond: Boolean)(f: => Identity[A]): Identity[Unit] =
    if (cond) f
    else unit

  override def void[A](fa: Identity[A]): Identity[Unit] = unit

  override def eval[A](f: => A): Identity[A] = f

  override def guarantee[A](f: Identity[A])(g: => Identity[Unit]): Identity[A] =
    try f
    finally g
}
