// utlities/MiscUtilities.scala

package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.io.InputStream
import java.io.IOException
import java.net.{URI, URL}
import java.nio.file.Paths
import org.htmlcleaner.{HtmlCleaner, TagNode}
import scala.collection.mutable.ListBuffer
import scala.io.StdIn
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Try, Success, Failure}
import wvlet.log.LogSupport
import scala.math.Ordering.Implicits._

// Utilities for miscellaneous functionality
object MiscUtilities extends LogSupport {

  /** Recursively splits a string into multiple subsets based on indices. */
  def splitCollection(splitPoints: Seq[Int], s: String, result: ListBuffer[String]): Unit = {
    if (splitPoints.nonEmpty) {
      val (element, rest) = s.splitAt(splitPoints.head)
      if (rest.nonEmpty) splitCollection(splitPoints.tail, rest, result)
      element +=: result
    } else {
      if (s.nonEmpty) s +=: result
    }
  }

  /** Executes a shell command capturing standard output if successful. */
  def getProcessOutput(command: String): Option[String] = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val logger = ProcessLogger(
      (o: String) => stdout.append(o + "\n"),
      (e: String) => stderr.append(e + "\n")
    )
    val exitCode = Process(command.stripMargin).!(logger)
    if(exitCode == 0) Some(stdout.toString.trim)
    else {
      info(stderr.toString.trim)
      None
    }
  }

  /** Recursive prompt for valid user feedback from options. */
  @scala.annotation.tailrec
  def getFeedback(message: String = "", validRange: Seq[String]): String = {
    val addendum = s"Please select one of (${validRange.mkString(", ")})\n"
    val feedback = StdIn.readLine(message.trim + "\n" + addendum)
    if (validRange.contains(feedback)) feedback else getFeedback(validRange = validRange)
  }

  /** Retrieves the installed ExifTool version as Double, or -1 if unavailable. */
  def getPresentExifToolVersion: Double = getProcessOutput(
    s"${Constants.ExifToolBaseCommand.split(Constants.BlankSplitter).head.trim} -ver"
  ).getOrElse("-1").toDouble

  // Implicit ordering to compare arrays of ints element-wise for version comparison
  implicit val arrayOrdering: Ordering[Array[Int]] = new Ordering[Array[Int]] {
    def compare(a: Array[Int], b: Array[Int]): Int = {
      val lengthCompare = a.length.compareTo(b.length)
      if(lengthCompare != 0) lengthCompare
      else {
        a.zip(b).collectFirst {
          case (x, y) if x != y => x.compareTo(y)
        }.getOrElse(0)
      }
    }
  }

  /**
   * Fetches SourceForge ExifTool files page, parses all Mac .pkg versions,
   * extracts versions, and returns the highest version and download URL.
   *
   * @return Some(version, downloadURL) if found, else None.
   */
    def fetchLatestExifToolMacVersion(): Option[(String, String)] = {
    var inputStream: InputStream = null

    try {
      val cleaner = new HtmlCleaner()
      val url = new URI(Constants.macPkgUrl).toURL()
      inputStream = url.openStream()
      val rootNode = cleaner.clean(inputStream)

      val links = rootNode.getElementsByName("a", true).toSeq
      val pkgRegex = """ExifTool-(\d+\.\d+)\.pkg""".r

      val versionedLinks = links.flatMap { element =>
        val href = element.getAttributeByName("href")
        if (href != null) {
          pkgRegex.findFirstMatchIn(href).map { m =>
            val version = m.group(1)
            val fullUrl = href   // Use href directly, no concatenation
            (version, fullUrl)
          }
        } else None
      }

      if (versionedLinks.nonEmpty) {
        implicit val arrayOrdering: Ordering[Array[Int]] = new Ordering[Array[Int]] {
          def compare(a: Array[Int], b: Array[Int]): Int = {
            val lengthCompare = a.length.compareTo(b.length)
            if (lengthCompare != 0) lengthCompare
            else {
              a.zip(b).collectFirst {
                case (x, y) if x != y => x.compareTo(y)
              }.getOrElse(0)
            }
          }
        }
        val highest = versionedLinks.maxBy { case (version, _) =>
          version.split('.').map(_.toInt)
        }
        Some(highest)
      } else {
        warn("No Mac .pkg files found on SourceForge ExifTool directory")
        None
      }
    } catch {
      case e: Exception =>
        error(s"Failed to fetch and parse SourceForge page: ${e.getMessage}")
        None
    } finally {
      if (inputStream != null) try inputStream.close() catch {
        case _: Exception => warn("Exception closing input stream")
      }
    }
  }

  /**
   * Checks installed ExifTool version against latest on SourceForge.
   * Downloads newest Mac .pkg if installed version is outdated.
   * Handles network errors and logs status info.
   */
  def handleExifTool(): Unit = {
    fetchLatestExifToolMacVersion() match {
      case Some((version, downloadUrl)) =>
        val pkgName = s"ExifTool-$version.pkg"
        val downloadPath = Paths.get(Constants.DownloadFolder, pkgName).toString

        try {
          val presentVersion = getPresentExifToolVersion
          val newestVersion = version.toDouble

          if(presentVersion < newestVersion) {
            info(s"Current ExifTool version ($presentVersion) is older than $newestVersion. Downloading latest package...")

            Try {
              FileUtilities.download(downloadUrl, downloadPath)
            } match {
              case Success(_) => info(s"Newest ExifTool version ($newestVersion) downloaded from SourceForge")
              case Failure(e) => error(s"Failed to download ExifTool: ${e.getMessage}")
            }
          } else {
            info(s"Current ExifTool version ($presentVersion) is up to date")
          }
        } catch {
          case _: java.net.UnknownHostException =>
            warn("You are offline. No attempt to install newest ExifTool version")
          case _: java.io.FileNotFoundException =>
            warn(s"Download URL is not reachable")
          case e: Exception =>
            error(s"Unexpected error: ${e.getMessage}")
        }
      case None =>
        warn("Could not determine latest ExifTool Mac package version. Skipping download.")
    }
  }

}
