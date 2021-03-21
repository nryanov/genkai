package genkai

import java.time.Instant
import java.time.temporal.ChronoUnit

sealed trait WindowUnit {
  def current: Long
}

object WindowUnit {
  case object Second extends WindowUnit {
    System.currentTimeMillis()
    override def current: Long =
      Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli
  }

  case object Minute extends WindowUnit {
    override def current: Long = Instant.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli
  }

  case object Hour extends WindowUnit {
    override def current: Long = Instant.now().truncatedTo(ChronoUnit.HOURS).toEpochMilli
  }

  case object Day extends WindowUnit {
    override def current: Long = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli
  }

}
