package genkai

import java.time.temporal.{ChronoUnit, TemporalUnit}

sealed trait Window {
  def unit: TemporalUnit

  /** used as window size and ttl (seconds) */
  def size: Long
}

object Window {
  case object Second extends Window {
    override val unit: TemporalUnit = ChronoUnit.SECONDS

    override val size: Long = 1
  }

  case object Minute extends Window {
    override val unit: TemporalUnit = ChronoUnit.MINUTES

    override val size: Long = 60
  }

  case object Hour extends Window {
    override val unit: TemporalUnit = ChronoUnit.HOURS

    override val size: Long = 60 * 60
  }

  case object Day extends Window {
    override val unit: TemporalUnit = ChronoUnit.DAYS

    override val size: Long = 24 * 60 * 60
  }

}
