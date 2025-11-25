// utilities/TimeStampUtilities.scala

package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth, ZonedDateTime}
import scala.util.{Try, Success, Failure}
import wvlet.log.LogSupport

/** Holds a collection of timestamp utilities */
object TimestampUtilities extends LogSupport {

  /**
   * Write timestamps to files according to date map.
   *
   * @param fileToDateMap Map of file paths to corresponding timestamps.
   * @param treatExifTimestamps Whether to write Exif timestamps (default true).
   */
  def writeTimestamps(fileToDateMap: Map[Path, LocalDateTime], treatExifTimestamps: Boolean = true): Unit = {
    if (fileToDateMap.nonEmpty) {
      if (treatExifTimestamps) writeExifTimestamps(fileToDateMap)
      writeMacTimestamps(fileToDateMap)
    }
  }

  /**
   * Writes Mac OS timestamps to files.
   *
   * @param fileNameToTimestampMap Map of file paths to timestamps.
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
   * Writes Exif timestamps to files.
   *
   * @param fileNameToTimestampMap Map of file paths to timestamps.
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
   * Creates reference dates (if not existing) for given file.
   *
   * @param filePath Path to the file.
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
   * Prepends timestamp to filename if not already present.
   *
   * @param filePath Path to file.
   * @param ldt The timestamp to add in filename.
   * @return New file path with timestamp or original if unchanged.
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
   * Detects hidden timestamps or dates in filenames within a directory.
   *
   * @param directory Directory path as string.
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
   * Identifies candidate files having timestamp or date pattern in filenames.
   *
   * @param directory Directory path as string.
   * @return Map of file paths to candidate timestamp/date strings.
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
   * Extracts timestamp components from a candidate string for validation.
   *
   * @param candidate Candidate timestamp string.
   * @return Sequence of component integer sequences.
   */
  def extractTimestampComponents(candidate: String): Seq[Seq[Int]] = {
    val components = scala.collection.mutable.ListBuffer[String]()
    MiscUtilities.splitCollection(Seq(4, 2, 2, 2, 2, 2), candidate, components)
    val c = components.map(_.toInt).toSeq
    Seq(c, Seq(c.head, c(2), c(1), c(3), c(4), c(5)))
  }

  /**
   * Validates if the candidate string is a valid timestamp or date.
   *
   * @param candidate Candidate timestamp string.
   * @return True if valid, else false.
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
            Constants.TimestampRanges(idx).contains(value)
          }
      }
    }
  }

  /**
   * Adds time component to a date string using Exif timestamps or defaults.
   *
   * @param filePath Path to file.
   * @param date Date string.
   * @return Optional LocalDateTime with time added.
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
   * Reads Exif timestamps from a file using ExifTool output parsing.
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
   * Builds ExifTool command to extract all relevant timestamps.
   *
   * @param filePath Path to file.
   * @return Command string.
   */
  def constructExifToolGetAllTimestampsCommand(filePath: Path): String = 
    s"""${Constants.ExifToolBaseCommand} -time:all -m -s "$filePath""""

  /**
   * Converts string to LocalDateTime using multiple formatters, safely.
   *
   * @param timestamp Timestamp string.
   * @param dtf DateTimeFormatter to use.
   * @return Optional LocalDateTime if parsing succeeded.
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
   * Extracts all existing Exif timestamps from parsed map.
   *
   * @param timestamps Map of tag ids to optional LocalDateTimes.
   * @return Iterable of existing LocalDateTime values.
   */
  def getExifTimestamps(timestamps: Map[String, Option[LocalDateTime]]): Iterable[LocalDateTime] = {
    timestamps.values.flatten
  }
}
