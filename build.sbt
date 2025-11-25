import sbtassembly.AssemblyPlugin.autoImport._
import sbt.Keys._

ThisBuild / scalaVersion := "3.3.5"
ThisBuild / organization := "ch.wrangel.toolbox"
ThisBuild / version := "4.0"

name := "exifgrinder"

libraryDependencies ++= Seq(
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.29",
  "org.wvlet.airframe" %% "airframe-log" % "2025.1.10",
  "org.apache.commons" % "commons-text" % "1.13.1",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

scalacOptions ++= Seq(
  "-explain",
  "-Xfatal-warnings",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xmax-inlines:64"
)

// Enable parallel test execution
Test / parallelExecution := true

// Assembly merge strategy for handling duplicate files in the fat jar
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x if x.endsWith(".html") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
