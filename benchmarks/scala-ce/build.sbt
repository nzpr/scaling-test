import scala.scalanative.sbtplugin.ScalaNativePlugin
import pl.project13.scala.sbt.JmhPlugin

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "bench"
ThisBuild / version := "0.1.0"

lazy val sharedSrc = (file(".") / "shared" / "src" / "main" / "scala").getCanonicalFile
lazy val ce3SharedSrc = (file(".") / "ce370shared" / "src" / "main" / "scala").getCanonicalFile

lazy val commonSettings = Seq(
  Compile / run / fork := true,
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  Compile / unmanagedSourceDirectories += sharedSrc
)

lazy val ce255jvm =
  Project("ce255jvm", file("ce255jvm"))
    .settings(commonSettings)
    .settings(
      name := "ce255jvm",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-effect" % "2.5.5",
        "org.scalameta" %% "munit" % "1.2.1" % Test
      )
    )

lazy val ce370jvm =
  Project("ce370jvm", file("ce370jvm"))
    .settings(commonSettings)
    .settings(
      Compile / unmanagedSourceDirectories += ce3SharedSrc,
      name := "ce370jvm",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-effect" % "3.7.0",
        "org.scalameta" %% "munit" % "1.2.1" % Test
      ),
      allowUnsafeScalaLibUpgrade := true
    )

lazy val ce255jmh =
  Project("ce255jmh", file("ce255jmh"))
    .enablePlugins(JmhPlugin)
    .settings(commonSettings)
    .settings(
      name := "ce255jmh",
      libraryDependencies += "org.typelevel" %% "cats-effect" % "2.5.5",
      Jmh / fork := true
    )

lazy val ce370jmh =
  Project("ce370jmh", file("ce370jmh"))
    .enablePlugins(JmhPlugin)
    .settings(commonSettings)
    .settings(
      name := "ce370jmh",
      libraryDependencies += "org.typelevel" %% "cats-effect" % "3.7.0",
      allowUnsafeScalaLibUpgrade := true,
      Jmh / fork := true
    )

lazy val ce370native =
  Project("ce370native", file("ce370native"))
    .enablePlugins(ScalaNativePlugin)
    .settings(commonSettings)
    .settings(
      Compile / unmanagedSourceDirectories += ce3SharedSrc,
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
