lazy val jedisVersion = "3.5.2"
lazy val scalatestVersion = "3.2.0"
lazy val scalacheckPlusVersion = "3.2.0.0"
lazy val scalamockVersion = "5.0.0"
lazy val scalacheckVersion = "1.14.3"
lazy val testContainersVersion = "0.39.1"
lazy val logbackVersion = "1.2.3"
lazy val kindProjectorVersion = "0.11.3"
lazy val slf4jApiVersion = "1.7.30"

val scala2_12 = "2.12.13"
val scala2_13 = "2.13.5"

lazy val buildSettings = Seq(
  organization := "com.nryanov.genkai",
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
  addCompilerPlugin(("org.typelevel" %% "kind-projector" % kindProjectorVersion).cross(CrossVersion.full)),
  Test / parallelExecution := false
)

lazy val genkai =
  project.in(file(".")).settings(moduleName := "genkai").settings(allSettings).settings(noPublish).aggregate(core)

lazy val core = project
  .in(file("modules/core"))
  .settings(allSettings)
  .settings(moduleName := "genkai-core")
  .settings(
    libraryDependencies ++= Seq(
      "redis.clients" % "jedis" % jedisVersion,
      "org.slf4j" % "slf4j-api" % slf4jApiVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "org.scalatestplus" %% "scalacheck-1-14" % scalacheckPlusVersion % Test,
      "org.scalamock" %% "scalamock" % scalamockVersion % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test,
      "ch.qos.logback" % "logback-classic" % logbackVersion % Test,
      "com.dimafeng" %% "testcontainers-scala" % testContainersVersion % Test
    )
  )
