package genkai

import genkai.monad.MonadError

trait MonadErrorAware[F[_]] {
  def monadError: MonadError[F]
}
