// utlities/MiscUtilities.scala

package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import ch.wrangel.toolbox.Constants.ExifToolWebsite
import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Paths
import org.htmlcleaner.{HtmlCleaner, TagNode}
import scala.collection.mutable.ListBuffer
import scala.io.StdIn
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Try, Success, Failure}
import wvlet.log.LogSupport

/** Utilities for miscellaneous functionality */
object MiscUtilities extends LogSupport {

  /** Splits a string into multiple subsets by provided indices (tail-recursive) */
  def splitCollection(splitPoints: Seq[Int], s: String, result: ListBuffer[String]): Unit = {
    val (element, rest) = s.splitAt(splitPoints.head)
    if (rest.nonEmpty) splitCollection(splitPoints.tail, rest, result)
    element +=: result
  }

  /** Executes a shell command and returns optional stdout */
  def getProcessOutput(command: String): Option[String] = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val logger = ProcessLogger(
      (o: String) => stdout.append(o + "\n"),
      (e: String) => stderr.append(e + "\n")
    )
    val exitCode = Process(command.stripMargin).!(logger)
    if (exitCode == 0) Some(stdout.toString.trim)
    else {
      info(stderr.toString.trim)
      None
    }
  }

  /** Requests correct user input from valid options (tail-recursive) */
  @scala.annotation.tailrec
  def getFeedback(message: String = "", validRange: Seq[String]): String = {
    val addendum = s"Please select one of (${validRange.mkString(", ")})\n"
    val feedback = StdIn.readLine(message.trim + "\n" + addendum)
    if (validRange.contains(feedback)) feedback else getFeedback(validRange = validRange)
  }

  /** Returns current installed ExifTool version as Double */
  def getPresentExifToolVersion: Double = getProcessOutput(
    s"${Constants.ExifToolBaseCommand.split(Constants.BlankSplitter).head.trim} -ver"
  ).getOrElse("-1").toDouble

  /** Checks and installs or updates ExifTool if newer version available */
  def handleExifTool(): Unit = {
    try {
      val url = URI.create(Constants.ExifToolWebsite).toURL()
      val cleaner = new HtmlCleaner()
      val rootNode: TagNode = try {
        val inputStream: InputStream = url.openStream()
        try {
          cleaner.clean(inputStream)
        } finally {
          inputStream.close()
        }
      } catch {
        case e: IOException =>
          error(s"Error reading from URL: ${e.getMessage}")
          return
      }

      val aElements = rootNode.getElementsByName("a", true)
      val macPkgVersion = aElements.flatMap { element =>
        val href = element.getAttributeByName("href")
        if (href != null && href.endsWith(".pkg")) {
          val text = element.getText.toString
          val versionRegex = """ExifTool-(\d+\.\d+)\.pkg""".r
          versionRegex.findFirstMatchIn(text).map(_.group(1))
        } else None
      }.headOption

      macPkgVersion match {
        case Some(version) =>
          val newestVersion = version.toDouble
          val presentVersion = getPresentExifToolVersion
          if (presentVersion < newestVersion) {
            val pkgName = s"ExifTool-$version.pkg"
            val downloadPath = Paths.get(Constants.DownloadFolder, pkgName).toString

            Try {
              FileUtilities.download(s"${Constants.ExifToolWebsite}/$pkgName", downloadPath)
            } match {
              case Success(_) => info(s"Newest ExifTool version ($newestVersion) downloaded")
              case Failure(e) => error(s"Failed to download ExifTool: ${e.getMessage}")
            }
          } else {
            info(s"Current ExifTool version ($presentVersion) is up to date")
          }
        case None =>
          warn("No Mac pkg version found on the ExifTool website")
      }
    } catch {
      case _: java.net.UnknownHostException =>
        warn("You are offline. No attempt to install newest ExifTool version")
      case _: java.io.FileNotFoundException =>
        warn(s"$ExifToolWebsite is offline")
      case e: Exception =>
        error(s"Unexpected error: ${e.getMessage}")
    }
  }
}
