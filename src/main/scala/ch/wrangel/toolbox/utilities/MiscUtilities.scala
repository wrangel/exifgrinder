package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import ch.wrangel.toolbox.Constants.ExifToolWebsite
import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Paths
import org.htmlcleaner.{HtmlCleaner, TagNode}
import scala.collection.mutable.{ListBuffer}
import scala.io.StdIn
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Try, Success, Failure}
import wvlet.log.LogSupport

/* Utilities for miscellaneous functionality */
object MiscUtilities extends LogSupport {

  /** Splits a collection into multiple subsets, according to the [[Seq]] of indices provided
    *
    * @param splitPoints [[Seq]] of indices
    * @param s [[String]] to split
    * @param result [[ListBuffer]] containing the subsets
    */
  def splitCollection(splitPoints: Seq[Int],
                      s: String,
                      result: ListBuffer[String]): Unit = {
    val (element, rest) = s.splitAt(splitPoints.head)
    if (rest.nonEmpty)
      splitCollection(splitPoints.tail, rest, result)
    element +=: result
  }

  /** Handles shell processes
    * https://stackoverflow.com/questions/29935873/how-to-run-external-process-in-scala-and-get-both-exit-code-and-output
    *
    * @param command [[String]] representing shell command
    * @return Optional Stdout
    */
  def getProcessOutput(command: String): Option[String] = {
    val stdout: StringBuilder = new StringBuilder
    val stderr: StringBuilder = new StringBuilder
    val procLogger: ProcessLogger = ProcessLogger { 
      line => 
        stdout.append(line + "\n")
        stderr.append(line + "\n")
    }
    Process(command.stripMargin).!(procLogger) match {
      case 0 =>
        Some(stdout.toString.trim)
      case _ =>
        info(stderr.toString.trim)
        None
    }
  }

  /** Requests correct user input
    *
    * @param message Message to the user
    * @param validRange [[Seq]] of valid values
    * @return Correct user input
    */
  @scala.annotation.tailrec
  def getFeedback(message: String = "", validRange: Seq[String]): String = {
    val addendum: String =
      s"Please select one of (${validRange.mkString(", ")})\n"
    val feedback: String = StdIn.readLine(message.trim + "\n" + addendum)
    if (validRange.contains(feedback))
      feedback
    else
      getFeedback(validRange = validRange)
  }

  /** Currying function returning the ExifTool version present on the local computer
   *
   * @return Current ExifTool version in Double format
   */
  def getPresentExifToolVersion: Double = getProcessOutput(
    s"${Constants.ExifToolBaseCommand.split(Constants.BlankSplitter).head.trim} -ver"
  ).getOrElse("-1").toDouble

  /** Installs a new or updated version of ExifTool */
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
        } else {
          None
        }
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
              case Success(_) =>
                info(s"Newest ExifTool version ($newestVersion) is now downloaded")
              case Failure(e) =>
                error(s"Failed to download ExifTool: ${e.getMessage}")
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
