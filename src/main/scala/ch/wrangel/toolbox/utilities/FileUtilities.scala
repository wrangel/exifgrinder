// utlities/FileUtilities.scala
package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.io.{BufferedWriter, File, FileWriter, InputStream}
import java.net.URI
import java.nio.file._
import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.io.{BufferedSource, Source}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import wvlet.log.LogSupport


/*Utilities for file manipulation */
object FileUtilities extends LogSupport {

  /** Iterates through a directory [[String]]
   *
   * @param directory             [[String]] representation of directory path
   * @param walk                  [[Boolean]] indicating whether the iteration will be recursive
   * @return [[Seq]] of file [[Path]]s within the directory
   * @throws NoSuchFileException: In case directory is not present
   */
  @throws[NoSuchFileException]
  def iterateFiles(directory: String, walk: Boolean = false): collection.parallel.ParSeq[Path] = {
  val paths = if (walk) {
    Files.walk(Paths.get(directory)).iterator().asScala
  } else {
    Files.list(Paths.get(directory)).iterator().asScala
  }

  paths.toSeq.par
    .filter(Files.isRegularFile(_))
    .filter { filePath =>
      val filePathString = filePath.toString
      !Files.isHidden(filePath) && 
      Constants.ExcludedFileTypes.forall(!filePathString.endsWith(_))
    }
}

  /** Splits a filename into body and extension
   *
   * @param filePath [[Path]] to the file whose trailer is to be removed
   * @return [[String]] with removed trailers
   */
  def splitExtension(filePath: Path, isPathNeeded: Boolean): Seq[String] = {
    val relevantPathPortion: String = if (isPathNeeded)
      filePath.toString
    else
      filePath.getFileName.toString
    val dotPosition: Int = relevantPathPortion.reverse.indexOf(".") + 1
    val length: Int = relevantPathPortion.length
    Seq(
      relevantPathPortion.substring(0, length - dotPosition),
      relevantPathPortion.substring(length - dotPosition, length)
    )
  }

  /** Moves files
   *
   * @param files          [[ListBuffer]] containing failed files
   * @param fileParentPath [[Path]] to destination folder
   */
  def moveFiles(files: ListBuffer[Path], fileParentPath: Path): Unit = {
    if (files.nonEmpty) {
      Files.createDirectories(fileParentPath)
      files.foreach {
        (filePath: Path) =>
          try {
            Files.move(filePath, Paths.get(fileParentPath.toString, filePath.getFileName.toString))
          } catch {
            case e: java.nio.file.NoSuchFileException =>
              error("File does not exist : " + e)
          }
      }
    }
  }

  /** Creates an exif config file, or adds content to it.
   *
   * - Support for large files
   */
  def createOrAdaptExifConfigFile(): Unit = {
    if(!Files.exists(Constants.ExifToolConfigFilePath))
      writeToFile(Constants.ExifToolConfigFileContent)
    else {
      val bufferedSource: BufferedSource = Source.fromFile(Constants.ExifToolConfigFilePath.toString)
      val content: String = bufferedSource.getLines.mkString("\n")
      bufferedSource.close
      if(!content.contains(Constants.ExifToolConfigFileContent)) {
        // Append content to existing content
        writeToFile(content + "\n\n" + Constants.ExifToolConfigFileContent)
      }
    }

    /** Writes content to a file
     *
     * @param content [[String]] representing content to be written to file
     */
    def writeToFile(content: String): Unit = {
      val bw: BufferedWriter = new BufferedWriter(new FileWriter(new File(Constants.ExifToolConfigFilePath.toString)))
      bw.write(content)
      bw.close()
    }
  }

  /** Checks for zero byte size files
    *
    * @param directory [[String]] representation of directory path
    */
  def handleZeroByteLengthFiles(directory: String): Unit = {
    val zeroByteFiles = FileUtilities
      .iterateFiles(directory)
      .filter(Files.size(_) == 0)
      .map { filePath =>
        warn(s"$filePath byte size is 0")
        filePath
      }
      .seq
      .toList

    FileUtilities.moveFiles(ListBuffer(zeroByteFiles: _*),
                            Paths.get(directory, Constants.ZeroByteFolder))
  }

  /** Prepares an element of the treated files map
    *
    * @param filePath      [[Path]] to the file
    * @param ldt           [[LocalDateTime]] of the file, needed for renaming
    * @param needsRenaming Flag indicating whether renaming is necessary
    * @return [[Tuple2]] holding both [[Path]] to the renamed file and the file's [[LocalDateTime]]
    */
  def prepareFile(filePath: Path,
                  ldt: LocalDateTime,
                  needsRenaming: Boolean): (Path, LocalDateTime) = {
    (
      if (needsRenaming)
        TimestampUtilities.writeTimestampInFilename(filePath, ldt)
      else
        filePath,
      ldt
    )
  }

  /** Downloads a resource from the internet to a location on the computer
   *
   * @param sourceUrl String representation of download URL
   * @param targetFileName String representation of download path
   * @throws NoSuchFileException: In case file is not present
   */
  @throws[NoSuchFileException]
  def download(sourceUrl: String, targetFileName: String): Long = {
    try {
      val in: InputStream = URI.create(sourceUrl).toURL.openStream
      try {
        Files.copy(in, Paths.get(targetFileName))
      } finally {
        if (in != null) in.close()
      }
    } catch {
      case NonFatal(e) =>
        error(s"An error occurred while downloading the file: ${e.getMessage}")
        throw new NoSuchFileException(s"Failed to download from $sourceUrl")
    }
  }

}