import scala.scalanative.sbtplugin.ScalaNativePlugin

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "bench"
ThisBuild / version := "0.1.0"

lazy val commonSettings = Seq(
  Compile / run / fork := true,
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")
)

lazy val ce255jvm =
  Project("ce255jvm", file("ce255jvm"))
    .settings(commonSettings)
    .settings(
      name := "ce255jvm",
      libraryDependencies += "org.typelevel" %% "cats-effect" % "2.5.5"
    )

lazy val ce370jvm =
  Project("ce370jvm", file("ce370jvm"))
    .settings(commonSettings)
    .settings(
      name := "ce370jvm",
      libraryDependencies += "org.typelevel" %% "cats-effect" % "3.7.0",
      allowUnsafeScalaLibUpgrade := true
    )

lazy val ce370native =
  Project("ce370native", file("ce370native"))
    .enablePlugins(ScalaNativePlugin)
    .settings(commonSettings)
    .settings(
      name := "ce370native",
      libraryDependencies += "org.typelevel" %%% "cats-effect" % "3.7.0",
      allowUnsafeScalaLibUpgrade := true,
      nativeConfig ~= { cfg =>
        cfg
          .withMode(scala.scalanative.build.Mode.releaseFast)
          .withGC(scala.scalanative.build.GC.immix)
          .withLTO(scala.scalanative.build.LTO.none)
      }
    )
