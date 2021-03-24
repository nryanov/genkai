package genkai

import scala.concurrent.duration.Duration

sealed trait Strategy

object Strategy {
  final case class TokenBucket(tokens: Long, refillAmount: Long, refillDelay: Duration)
      extends Strategy

  final case class FixedWindow(tokens: Long, window: Window) extends Strategy

  final case class SlidingWindow(tokens: Long, window: Window) extends Strategy
}
