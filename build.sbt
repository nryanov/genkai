lazy val kindProjectorVersion = "0.11.3"
// backends
lazy val lettuceVersion = "6.1.2.RELEASE"
lazy val jedisVersion = "3.6.0"
lazy val redissonVersion = "3.15.4"
lazy val aerospikeClientVersion = "5.1.0"
// effects
lazy val catsVersion = "2.5.0"
lazy val zioVersion = "1.0.7"
lazy val monixVersion = "3.3.0"
// logging
lazy val slf4jApiVersion = "1.7.30"
// test
lazy val scalatestVersion = "3.2.8"
lazy val scalamockVersion = "5.1.0"
lazy val scalacheckPlusVersion = "3.2.2.0"
lazy val scalacheckVersion = "1.14.3"
lazy val testContainersVersion = "0.39.3"
lazy val logbackVersion = "1.2.3"

val scala2_12 = "2.12.13"
val scala2_13 = "2.13.5"

val compileAndTest = "compile->compile;test->test"

parallelExecution in Global := false

lazy val buildSettings = Seq(
  sonatypeProfileName := "com.nryanov",
  organization := "com.nryanov.genkai",
  homepage := Some(url("https://github.com/nryanov/genkai")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "nryanov",
      "Nikita Ryanov",
      "ryanov.nikita@gmail.com",
      url("https://nryanov.com")
    )
  ),
  scalaVersion := scala2_13,
  crossScalaVersions := Seq(scala2_12, scala2_13)
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

def compilerOptions(scalaVersion: String) = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xlint",
  "-language:existentials",
  "-language:postfixOps"
) ++ (CrossVersion.partialVersion(scalaVersion) match {
  case Some((2, scalaMajor)) if scalaMajor == 12 => scala212CompilerOptions
  case Some((2, scalaMajor)) if scalaMajor == 13 => scala213CompilerOptions
})

lazy val scala212CompilerOptions = Seq(
  "-Yno-adapted-args",
  "-Ywarn-unused-import",
  "-Xfuture"
)

lazy val scala213CompilerOptions = Seq(
  "-Wunused:imports"
)

lazy val allSettings = commonSettings ++ buildSettings

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions(scalaVersion.value),
  addCompilerPlugin(
    ("org.typelevel" %% "kind-projector" % kindProjectorVersion).cross(CrossVersion.full)
  ),
  Test / parallelExecution := false
)

lazy val genkai =
  project
    .in(file("."))
    .settings(moduleName := "genkai")
    .settings(allSettings)
    .settings(noPublish)
    .aggregate(
      core,
      cats,
      zio,
      monix,
      redisCommon,
      jedis,
      jedisCats,
      jedisZio,
      lettuce,
      lettuceCats,
      lettuceZio,
      lettuceMonix,
      redisson,
      redissonCats,
      redissonZio,
      redissonMonix,
      aerospike,
      aerospikeCats,
      aerospikeZio
    )

lazy val core = project
  .in(file("modules/core"))
  .settings(allSettings)
  .settings(moduleName := "genkai-core")
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jApiVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "org.scalatestplus" %% "scalacheck-1-14" % scalacheckPlusVersion % Test,
      "org.scalamock" %% "scalamock" % scalamockVersion % Test,
      "ch.qos.logback" % "logback-classic" % logbackVersion % Test,
      "com.dimafeng" %% "testcontainers-scala" % testContainersVersion % Test
    )
  )

lazy val cats = project
  .in(file("modules/effects/cats"))
  .settings(allSettings)
  .settings(moduleName := "genkai-cats")
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsVersion
    )
  )
  .dependsOn(core % compileAndTest)

lazy val zio = project
  .in(file("modules/effects/zio"))
  .settings(allSettings)
  .settings(moduleName := "genkai-zio")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion
    )
  )
  .dependsOn(core % compileAndTest)

lazy val monix = project
  .in(file("modules/effects/monix"))
  .settings(allSettings)
  .settings(moduleName := "genkai-monix")
  .settings(
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % monixVersion
    )
  )
  .dependsOn(core % compileAndTest)

lazy val redisCommon = project
  .in(file("modules/redis/common"))
  .settings(allSettings)
  .settings(moduleName := "genkai-redis-common")
  .dependsOn(core % compileAndTest)

lazy val jedis = project
  .in(file("modules/redis/jedis"))
  .settings(allSettings)
  .settings(moduleName := "genkai-jedis")
  .settings(
    libraryDependencies ++= Seq(
      "redis.clients" % "jedis" % jedisVersion
    )
  )
  .dependsOn(redisCommon % compileAndTest)

lazy val jedisCats = project
  .in(file("modules/redis/jedis/cats"))
  .settings(allSettings)
  .settings(moduleName := "genkai-jedis-cats")
  .dependsOn(jedis % compileAndTest)
  .dependsOn(cats % compileAndTest)

lazy val jedisZio = project
  .in(file("modules/redis/jedis/zio"))
  .settings(allSettings)
  .settings(moduleName := "genkai-jedis-zio")
  .dependsOn(jedis % compileAndTest)
  .dependsOn(zio % compileAndTest)

lazy val lettuce = project
  .in(file("modules/redis/lettuce"))
  .settings(allSettings)
  .settings(moduleName := "genkai-lettuce")
  .settings(
    libraryDependencies ++= Seq(
      "io.lettuce" % "lettuce-core" % lettuceVersion
    )
  )
  .dependsOn(redisCommon % compileAndTest)

lazy val lettuceCats = project
  .in(file("modules/redis/lettuce/cats"))
  .settings(allSettings)
  .settings(moduleName := "genkai-lettuce-cats")
  .dependsOn(lettuce % compileAndTest)
  .dependsOn(cats % compileAndTest)

lazy val lettuceZio = project
  .in(file("modules/redis/lettuce/zio"))
  .settings(allSettings)
  .settings(moduleName := "genkai-lettuce-zio")
  .dependsOn(lettuce % compileAndTest)
  .dependsOn(zio % compileAndTest)

lazy val lettuceMonix = project
  .in(file("modules/redis/lettuce/monix"))
  .settings(allSettings)
  .settings(moduleName := "genkai-lettuce-monix")
  .dependsOn(lettuce % compileAndTest)
  .dependsOn(monix % compileAndTest)

lazy val redisson = project
  .in(file("modules/redis/redisson"))
  .settings(allSettings)
  .settings(moduleName := "genkai-redisson")
  .settings(
    libraryDependencies ++= Seq(
      "org.redisson" % "redisson" % redissonVersion
    )
  )
  .dependsOn(redisCommon % compileAndTest)

lazy val redissonCats = project
  .in(file("modules/redis/redisson/cats"))
  .settings(allSettings)
  .settings(moduleName := "genkai-redisson-cats")
  .dependsOn(redisson % compileAndTest)
  .dependsOn(cats % compileAndTest)

lazy val redissonZio = project
  .in(file("modules/redis/redisson/zio"))
  .settings(allSettings)
  .settings(moduleName := "genkai-redisson-zio")
  .dependsOn(redisson % compileAndTest)
  .dependsOn(zio % compileAndTest)

lazy val redissonMonix = project
  .in(file("modules/redis/redisson/monix"))
  .settings(allSettings)
  .settings(moduleName := "genkai-redisson-monix")
  .dependsOn(redisson % compileAndTest)
  .dependsOn(monix % compileAndTest)

lazy val aerospike = project
  .in(file("modules/aerospike"))
  .settings(allSettings)
  .settings(moduleName := "genkai-aerospike")
  .settings(
    libraryDependencies ++= Seq(
      "com.aerospike" % "aerospike-client" % aerospikeClientVersion
    )
  )
  .dependsOn(core % compileAndTest)

lazy val aerospikeCats = project
  .in(file("modules/aerospike/cats"))
  .settings(allSettings)
  .settings(moduleName := "genkai-aerospike-cats")
  .dependsOn(aerospike % compileAndTest)
  .dependsOn(cats % compileAndTest)

lazy val aerospikeZio = project
  .in(file("modules/aerospike/zio"))
  .settings(allSettings)
  .settings(moduleName := "genkai-aerospike-zio")
  .dependsOn(aerospike % compileAndTest)
  .dependsOn(zio % compileAndTest)
