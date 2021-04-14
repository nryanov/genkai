package genkai

import scala.concurrent.duration.Duration

sealed trait Strategy

object Strategy {

  /**
   * @param tokens - max tokens
   * @param refillAmount - token which will be refilled after <refillDelay>
   * @param refillDelay - refill delay
   */
  final case class TokenBucket(tokens: Long, refillAmount: Long, refillDelay: Duration)
      extends Strategy

  /**
   * @param tokens - max tokens per window
   * @param window - window size
   */
  final case class FixedWindow(tokens: Long, window: Window) extends Strategy

  /**
   * @param tokens - max tokens per window
   * @param window - window size
   */
  final case class SlidingWindow(tokens: Long, window: Window) extends Strategy
}
