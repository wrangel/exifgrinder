// utlities/FileUtilities.scala

package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.io.{BufferedWriter, FileWriter, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, Paths, NoSuchFileException}
import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.{Using, Try, Failure, Success}
import scala.util.control.NonFatal
import wvlet.log.LogSupport

/** Utilities for file manipulation */
object FileUtilities extends LogSupport {

  /** Iterates through a directory and returns parallel collection of file Paths.
   * Closes underlying streams safely.
   */
  @throws[NoSuchFileException]
  def iterateFiles(directory: String, walk: Boolean = false): collection.parallel.ParSeq[Path] = {
    val stream =
      if (walk) Files.walk(Paths.get(directory))
      else Files.list(Paths.get(directory))
    try {
      stream.iterator().asScala.toSeq.par
        .filter(Files.isRegularFile(_))
        .filter { filePath =>
          val pathStr = filePath.toString
          !Files.isHidden(filePath) && Constants.ExcludedFileTypes.forall(!pathStr.endsWith(_))
        }
    } finally {
      stream.close()
    }
  }

  /** Splits file path into base name and extension */
  def splitExtension(filePath: Path, isPathNeeded: Boolean): Seq[String] = {
    val relevant = if (isPathNeeded) filePath.toString else filePath.getFileName.toString
    val dotPos = relevant.lastIndexOf('.')
    if (dotPos > 0) Seq(relevant.substring(0, dotPos), relevant.substring(dotPos)) else Seq(relevant, "")
  }

  /** Moves provided files list to target directory with error handling */
  def moveFiles(files: ListBuffer[Path], fileParentPath: Path): Unit = {
    if (files.nonEmpty) {
      Try(Files.createDirectories(fileParentPath)) match {
        case Failure(e) => error(s"Failed to create directory $fileParentPath: $e")
        case _ => ()
      }
      files.foreach { filePath =>
        Try(Files.move(filePath, fileParentPath.resolve(filePath.getFileName))) match {
          case Failure(e: java.nio.file.NoSuchFileException) =>
            error(s"File does not exist: $e")
          case Failure(e) =>
            error(s"Error moving file $filePath: $e")
          case Success(_) => ()
        }
      }
    }
  }

  /** Creates or appends to the ExifTool config file safely */
  def createOrAdaptExifConfigFile(): Unit = {
    val configPath = Constants.ExifToolConfigFilePath
    if (Files.notExists(configPath)) {
      writeToFile(Constants.ExifToolConfigFileContent)
    } else {
      val content = Using.resource(Source.fromFile(configPath.toFile))(_.mkString)
      if (!content.contains(Constants.ExifToolConfigFileContent)) {
        writeToFile(content + "\n\n" + Constants.ExifToolConfigFileContent)
      }
    }
  }

  /** Writes string content to the file with resource safety */
  def writeToFile(content: String): Unit = {
    val result: Try[Unit] = Using(new BufferedWriter(new FileWriter(Constants.ExifToolConfigFilePath.toFile))) { bw =>
      bw.write(content)
    }
    result.fold(
      e => error(s"Failed to write to file: $e"),
      _ => ()  // success case, do nothing
    )
  }

  /** Processes zero-byte files by moving them to a specified folder */
  def handleZeroByteLengthFiles(directory: String): Unit = {
    val zeroFiles = iterateFiles(directory).filter(Files.size(_) == 0).seq.toList
    zeroFiles.foreach(fp => warn(s"$fp byte size is 0"))
    moveFiles(ListBuffer(zeroFiles: _*), Paths.get(directory, Constants.ZeroByteFolder))
  }

  /** Prepares a file for further processing, optionally renaming */
  def prepareFile(filePath: Path, ldt: LocalDateTime, needsRenaming: Boolean): (Path, LocalDateTime) = {
    val path = if (needsRenaming) TimestampUtilities.writeTimestampInFilename(filePath, ldt) else filePath
    (path, ldt)
  }

  /** Downloads a resource from URL to target file path with error handling */
  @throws[NoSuchFileException]
  def download(sourceUrl: String, targetFileName: String): Long = {
    val result: Try[Long] = Try {
      Using(new URI(sourceUrl).toURL().openStream()) { in =>
        Files.copy(in, Paths.get(targetFileName))
      }
    }.flatten

    result match {
      case Failure(NonFatal(e)) =>
        error(s"Error downloading file from $sourceUrl: ${e.getMessage}")
        throw new NoSuchFileException(s"Failed to download from $sourceUrl")
      case Failure(e) => throw e
      case Success(bytes) => bytes
    }
  }

}
