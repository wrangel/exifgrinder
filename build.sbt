import sbtassembly.AssemblyPlugin.autoImport._

scalaVersion := "3.3.5"

name := "exifgrinder"
organization := "ch.wrangel.toolbox"
version := "3.0"

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

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
  case x => 
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
