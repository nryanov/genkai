package genkai.effect.cats3

import cats.effect.unsafe.IORuntime

trait Cats3BaseSpec {
  implicit val runtime: IORuntime = IORuntime.global
}
