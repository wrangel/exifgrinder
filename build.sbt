import sbtassembly.AssemblyPlugin.autoImport._
import sbt.Keys._

ThisBuild / scalaVersion := "3.8.1"
ThisBuild / organization := "ch.wrangel.toolbox"
ThisBuild / version := "4.0"

name := "exifgrinder"

libraryDependencies ++= Seq(
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.29",
  "org.wvlet.airframe" %% "airframe-log" % "24.12.0",
  "org.apache.commons" % "commons-text" % "1.12.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

scalacOptions ++= Seq(
  "-explain",
  "-Werror",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xmax-inlines:64",
  "-rewrite"
)

Test / parallelExecution := true

ThisBuild / assemblyJarName := s"${name.value}-assembly-${version.value}.jar"

assembly / mainClass := Some("ch.wrangel.toolbox.Main")

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x if x.endsWith(".html") => MergeStrategy.discard
  case x => MergeStrategy.first
}
