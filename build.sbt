import sbtassembly.AssemblyPlugin.autoImport._
import sbt.Keys._

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "ch.wrangel.toolbox"
ThisBuild / version := "4.0"

name := "exifgrinder"

libraryDependencies ++= Seq(
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.29",
  "org.wvlet.airframe" %% "airframe-log" % "2026.1.6",
  "org.apache.commons" % "commons-text" % "1.15.0",
  "org.scalatest" %% "scalatest" % "3.2.20" % Test
)

scalacOptions ++= Seq(
  "-explain",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xmax-inlines:64",
  "-Xsemanticdb" // Explicitly forces Metals to generate index files
)

// Keeps your main source strict, but prevents warnings in tests from breaking the IDE
Compile / scalacOptions += "-Werror"
Test / scalacOptions -= "-Werror"

Test / parallelExecution := true

assembly / assemblyJarName := s"${name.value}-assembly-${version.value}.jar"

assembly / mainClass := Some("ch.wrangel.toolbox.Main")

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat // Preserves library plugins
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case x if x.endsWith(".html")                  => MergeStrategy.discard
  case _                                         => MergeStrategy.first
}
