// utilities/TimeStampUtilities.scala

package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth, ZonedDateTime}
import scala.util.{Try, Success, Failure}
import wvlet.log.LogSupport

/**
 * Utility object holding timestamp related functions for reading, writing,
 * validating, and manipulating timestamps across files and metadata.
 */
object TimestampUtilities extends LogSupport {

  /**
   * Writes timestamps to files according to the provided mapping.
   * Can write both Exif and Mac OS timestamps.
   *
   * @param fileToDateMap Map where keys are file paths and values timestamps to write.
   * @param treatExifTimestamps Flag indicating whether to write Exif timestamps additionally.
   */
  def writeTimestamps(fileToDateMap: Map[Path, LocalDateTime], treatExifTimestamps: Boolean = true): Unit = {
    if (fileToDateMap.nonEmpty) {
      if (treatExifTimestamps) writeExifTimestamps(fileToDateMap)
      writeMacTimestamps(fileToDateMap)
    }
  }

  /**
   * Writes Mac OS timestamps to the specified files using SetFile tool.
   *
   * @param fileNameToTimestampMap Map from file to timestamp to set.
   */
  def writeMacTimestamps(fileNameToTimestampMap: Map[Path, LocalDateTime]): Unit = {
    for {
      filePath <- fileNameToTimestampMap.keys
      macTag <- Constants.MacOsTimestampTags.values.map(_.head)
    } {
      val newDate = fileNameToTimestampMap(filePath).format(Constants.TimestampFormatters("mac"))
      MiscUtilities.getProcessOutput(s"""SetFile -$macTag "$newDate" "${filePath.toString}"""") match {
        case Some(_) =>
          info(s"======== Treating $filePath")
          info(s"Changed mac tag $macTag to $newDate")
        case None =>
      }
    }
  }

  /**
   * Writes ExifTool timestamps over the specified files with overwrite.
   *
   * @param fileNameToTimestampMap Map from file to timestamp to set via ExifTool.
   */
  private def writeExifTimestamps(fileNameToTimestampMap: Map[Path, LocalDateTime]): Unit = {
    fileNameToTimestampMap.foreach { case (filePath, ldt) =>
      createDates(filePath)
      val newDate = ldt.format(Constants.TimestampFormatters("exif"))
      MiscUtilities.getProcessOutput(
        s"""${Constants.ExifToolBaseCommand} -overwrite_original -wm w -time:all="$newDate" "$filePath""""
      ) match {
        case Some(_) =>
          info(s"======== Treating $filePath")
          info(s"Changed exif tags to $newDate")
        case None =>
      }
    }
  }

  /**
   * Creates reference dates for missing exif tags in a file.
   *
   * @param filePath Path to file for which to create missing dates.
   */
  private def createDates(filePath: Path): Unit = {
    Constants.ReferenceExifTimestamps.foreach { ret =>
      val retLower = ret.toLowerCase()
      MiscUtilities.getProcessOutput(
        s"""${Constants.ExifToolBaseCommand} -if 'not $$retLower' -$retLower=now
           |"-$retLower<$retLower" -overwrite_original "$filePath"""".stripMargin)
    }
  }

  /**
   * Writes a timestamp prefix in the filename if not already present.
   *
   * @param filePath Original file path.
   * @param ldt The timestamp to prepend.
   * @return New path with renamed file or original if unchanged.
   */
  def writeTimestampInFilename(filePath: Path, ldt: LocalDateTime): Path = {
    val filePathComponents = FileUtilities.splitExtension(filePath, isPathNeeded = false)
    val oldFileName = filePathComponents.mkString("")
    val timestamp = ldt.format(Constants.TimestampFormatters("file"))
    val hasCorrectTimestampAlready = Try {
      filePathComponents.head.split(Constants.PartitionString).head == timestamp ||
      filePathComponents.head == timestamp
    }.getOrElse(false)

    if (!hasCorrectTimestampAlready) {
      val newFileName = timestamp + Constants.PartitionString + oldFileName
      info(s"======== Treating $filePath")
      info(s"Renaming $oldFileName to $newFileName")
      val newPath = filePath.resolveSibling(newFileName)
      Files.move(filePath, newPath)
      newPath
    } else {
      info(s"No need to rename $oldFileName")
      filePath
    }
  }

  /**
   * Detects hidden timestamps or dates in filenames in a directory.
   *
   * @param directory Directory to scan.
   * @return Map of file paths to detected LocalDateTimes.
   */
  def detectHiddenTimestampsOrDates(directory: String): Map[Path, LocalDateTime] = {
    identifyCandidates(directory)
      .map { case (filePath, value) =>
        val fullValue = if (value.length > 8) value.take(8) + "_" + value.drop(8) else value
        filePath -> (fullValue, isValidCandidate(fullValue))
      }
      .filter(_._2._2)
      .map { case (filePath, (value, _)) =>
        filePath -> (if (value.length > 8) convertStringToTimestamp(value, Constants.TimestampFormatters("file"))
                     else addTimeToDate(filePath, value))
      }
      .collect { case (filePath, Some(ldt)) => filePath -> ldt }
      .toMap
  }

  /**
   * Identifies candidate files with timestamps or dates in filenames.
   *
   * @param directory Directory path.
   * @return Map of file paths to extracted candidate timestamp strings.
   */
  def identifyCandidates(directory: String): Map[Path, String] = {
    FileUtilities.iterateFiles(directory)
      .map { filePath =>
        filePath -> {
          val rawSeq = Constants.TimestampAndDatePatterns
            .map(_.findFirstIn(filePath.getFileName.toString).getOrElse(""))
          rawSeq.maxBy(_.length).replaceAll("[^0-9]", "")
        }
      }
      .filter(_._2.nonEmpty)
      .seq
      .toMap
  }

  /**
   * Extracts components of a timestamp candidate as sequences of integers.
   * Uses safe integer conversion to avoid exceptions.
   *
   * @param candidate Timestamp candidate string.
   * @return Sequence of integer sequences representing timestamp parts.
   */
  def extractTimestampComponents(candidate: String): Seq[Seq[Int]] = {
    val digitsOnly = candidate.replaceAll("[^0-9]", "")
    val components = scala.collection.mutable.ListBuffer[String]()
    MiscUtilities.splitCollection(Seq(4, 2, 2, 2, 2, 2), digitsOnly, components)
    val validInts = components.map(_.toIntOption)
    if (validInts.forall(_.isDefined)) {
      val c = validInts.flatten.toSeq
      Seq(c, Seq(c.head, c(2), c(1), c(3), c(4), c(5)))
    } else {
      warn(s"Invalid timestamp components in candidate: $candidate")
      Seq.empty
    }
  }

  /**
   * Validates if a timestamp candidate string represents a valid timestamp.
   *
   * @param candidate Timestamp candidate string.
   * @return Boolean indicating validity.
   */
  def isValidCandidate(candidate: String): Boolean = {
    extractTimestampComponents(candidate).exists { components =>
      components.zipWithIndex.forall {
        case (value, idx) =>
          if (idx == 2) {
            Try {
              (1 to YearMonth.of(components.head, components(1)).lengthOfMonth).contains(value)
            }.getOrElse(false)
          } else {
            Constants.TimestampRanges.lift(idx).exists(_.contains(value))
          }
      }
    }
  }

  /**
   * Adds time components to a date string by leveraging Exif timestamps or defaults.
   *
   * @param filePath Path to file.
   * @param date Date string extracted.
   * @return Optional LocalDateTime with added time or None.
   */
  def addTimeToDate(filePath: Path, date: String): Option[LocalDateTime] = {
    val coincidingExifTimestamps = readExifTimestamps(filePath).values.flatten.toList
      .filter(ldt => Try {
        ldt.getYear == date.substring(0, 4).toInt &&
        ldt.getMonthValue == date.substring(4, 6).toInt &&
        ldt.getDayOfMonth == date.substring(6, 8).toInt
      }.getOrElse(false))

    coincidingExifTimestamps.sorted.headOption.orElse {
      convertStringToTimestamp(date + Constants.DefaultTime, Constants.TimestampFormatters("file")).orElse {
        MiscUtilities.getFeedback(s"Is $date a valid partial date?", Seq("y", "n")) match {
          case "y" =>
            convertStringToTimestamp(date + Constants.DefaultDay + Constants.DefaultTime, Constants.TimestampFormatters("file"))
          case _ => None
        }
      }
    }
  }

  /**
   * Reads all Exif timestamps from a file by parsing ExifTool output.
   *
   * @param filePath Path to file.
   * @return Map of Exif tag keys to optional LocalDateTime values.
   */
  def readExifTimestamps(filePath: Path): Map[String, Option[LocalDateTime]] = {
    StringUtilities.prepareExifToolOutput(constructExifToolGetAllTimestampsCommand(filePath))
      .map { case Array(key, value) =>
        key -> convertStringToTimestamp(value, Constants.TimestampFormatters("exif"))
      }
      .toMap
  }

  /**
   * Constructs the ExifTool command for extracting all timestamps.
   *
   * @param filePath Path to file.
   * @return String command.
   */
  def constructExifToolGetAllTimestampsCommand(filePath: Path): String = 
    s"""${Constants.ExifToolBaseCommand} -time:all -m -s "$filePath""""

  /**
   * Converts a string to LocalDateTime using multiple timestamp formatters safely.
   *
   * @param timestamp Timestamp string.
   * @param dtf DateTimeFormatter to use.
   * @return Option of LocalDateTime if successfully parsed.
   */
  def convertStringToTimestamp(timestamp: String, dtf: DateTimeFormatter): Option[LocalDateTime] = {
    Try(LocalDateTime.parse(timestamp, dtf)) match {
      case Success(ldt) if !(ldt.getYear == 1970 && ldt.getMonthValue == 1 && ldt.getDayOfMonth == 1) =>
        Some(ldt)
      case _ =>
        Try(ZonedDateTime.parse(timestamp, Constants.TimestampFormatters("zonedExif")).toLocalDateTime).toOption
          .orElse(Try(LocalDateTime.parse(timestamp, Constants.TimestampFormatters("exif2"))).toOption)
    }
  }

  /**
   * Extracts existing LocalDateTime values from timestamp map.
   *
   * @param timestamps Map of tag keys to optional LocalDateTime values.
   * @return Iterable of valid LocalDateTime values.
   */
  def getExifTimestamps(timestamps: Map[String, Option[LocalDateTime]]): Iterable[LocalDateTime] = {
    timestamps.values.flatten
  }
}
