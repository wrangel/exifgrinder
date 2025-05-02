// utilities/TimeStampUtilities.scala
package ch.wrangel.toolbox.utilities

import ch.wrangel.toolbox.Constants
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth, ZonedDateTime}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
import wvlet.log.LogSupport

/** Holds a collection of timestamp utilities */
object TimestampUtilities extends LogSupport {

  /** Adjusts both Mac OS and exif timestamps
   *
   * @param fileToDateMap       [[Map]] from file [[Path]] to the file's [[LocalDateTime]]
   * @param treatExifTimestamps Flag whether to treat exif timestamps. Default is true
   */
  def writeTimestamps(fileToDateMap: Map[Path, LocalDateTime], treatExifTimestamps: Boolean = true): Unit = {
    if (fileToDateMap.nonEmpty) {
      if (treatExifTimestamps)
        writeExifTimestamps(fileToDateMap)
      writeMacTimestamps(fileToDateMap)
    }
  }

  /** Rewrites the Mac OS creation and modification timestamps
   *
   * @param fileNameToTimestampMap [[Map]] containing the file [[Path]] as well as the file's [[LocalDateTime]]
   */
  def writeMacTimestamps(fileNameToTimestampMap: Map[Path, LocalDateTime]): Unit = {
    fileNameToTimestampMap.keys
      .foreach { 
        (filePath: Path) =>
          Constants.MacOsTimestampTags.values
            .map(_.head)
            .foreach { 
              (macTag: String) =>
                val newDate: String = fileNameToTimestampMap(filePath).format(
                  Constants.TimestampFormatters("mac"))
                MiscUtilities.getProcessOutput(
                  s"""SetFile -$macTag "$newDate" "${filePath.toString}""""
                ) match {
                  case Some(_) =>
                    info(s"======== Treating $filePath")
                    info(s"Changed mac tag $macTag to $newDate")
                  case None =>
                }
            }
      }
  }

  /** Newly writes exif timestamps
   *
   * @param fileNameToTimestampMap [[Map]] containing the file [[Path]] as well as the file's [[LocalDateTime]]
   */
  private def writeExifTimestamps(fileNameToTimestampMap: Map[Path, LocalDateTime]): Unit = {
    fileNameToTimestampMap.foreach {
      case (filePath: Path, ldt: LocalDateTime) =>
        createDates(filePath)
        val newDate: String =
          ldt.format(Constants.TimestampFormatters("exif"))
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

  /** Creates reference tags (CreateDate and DateTimeOriginal) if not existing
   *
   * @param filePath [[Path]] to the file
   */
  private def createDates(filePath: Path): Unit = {
    Constants.ReferenceExifTimestamps.foreach { 
      (ret: String) =>
        val retl: String = ret.toLowerCase()
        MiscUtilities.getProcessOutput(
          s"""${Constants.ExifToolBaseCommand} -if 'not $$retl' -$retl=now
            |"-$retl<$retl" -overwrite_original "$filePath"""".stripMargin
        )
    }
  }

  /** Renames a file, if necessary (i.e. the file does not already bear exactly the same file name)
   *
   * @param filePath [[Path]] to the file
   * @param ldt      [[LocalDateTime]] to be used for renaming
   * @return [[Path]] to the renamed file name
   */
  def writeTimestampInFilename(filePath: Path, ldt: LocalDateTime): Path = {
    val filePathComponents: Seq[String] =
      FileUtilities.splitExtension(filePath, isPathNeeded = false)
    val oldFileName: String = filePathComponents.mkString("")
    val timestamp: String = ldt.format(Constants.TimestampFormatters("file"))
    val hasCorrectTimestampAlready: Boolean = Try {
      filePathComponents.head.split(Constants.PartitionString).head.equals(timestamp) |
        filePathComponents.head.equals(timestamp)
    }.getOrElse(false)
    if (!hasCorrectTimestampAlready) {
      val newFileName: String =
        timestamp + Constants.PartitionString + oldFileName
      info(s"======== Treating $filePath")
      info(s"Renaming $oldFileName to $newFileName")
      val newPath: Path = filePath.resolveSibling(newFileName)
      Files.move(filePath, newPath)
      newPath
    } else {
      info(s"No need to rename $oldFileName")
      filePath
    }
  }

  /** Detects timestamps / dates in file names. File names without valid timestamps / dates are omitted
   *
   * @param directory [[String]] representation of path of directory
   * @return [[Map]] containing file [[Path]]s as keys, and valid [[LocalDateTime]]s as values.
   *         For dates, the time component is set to "00:01:00"
   */
  def detectHiddenTimestampsOrDates(
                                     directory: String
                                   ): Map[Path, LocalDateTime] = {
    identifyCandidates(directory)
      .map {
        case (filePath: Path, value: String) =>

          filePath -> {
            val (date: String, time: String) = value.splitAt(8)
            if (time.nonEmpty)
              date + "_" + time
            else
              date
          } ->
            {println(filePath)
            println(value)
              println(isValidCandidate(value))
            isValidCandidate(value)}
      }
      .filter(_._2 == true)
      .keys
      .map {
        case (filePath: Path, element: String) =>
          filePath -> {
            if (element.length > 8)
              TimestampUtilities.convertStringToTimestamp(
                element,
                Constants.TimestampFormatters("file"))
            else
              addTimeToDate(filePath, element)
          }
      }
      .filter(_._2.isDefined)
      .map {
        case (filePath: Path, oldt: Option[LocalDateTime]) =>
          filePath -> oldt.get
      }
      .toMap
  }

  /** Filters out [[Path]]s to files whose file names might include a valid timestamp / date
   *
   * @param directory [[String]] representation of directory path
   * @return [[Map]] containing the candidate files' [[Path]] as keys, and the timestamp / date candidate [[String]]
   */
  def identifyCandidates(directory: String): Map[Path, String] = {
    FileUtilities
      .iterateFiles(directory)
      .map { filePath =>
        filePath -> {
          val rawSeq = Constants.TimestampAndDatePatterns
            .map(_.findFirstIn(filePath.getFileName.toString).getOrElse(""))
          rawSeq
            .maxBy(_.length)
            .replaceAll("[^0-9]", "")
        }
      }
      .filter(_._2.nonEmpty)
      .seq
      .toMap
  }

  /** Extracts components from a timestamp candidate, for both European and American notation
   *
   * @param candidate [[String]] potentially representing a valid timestamp / date
   * @return Seq containing all date and time components in the correct order
   */
  def extractTimestampComponents(candidate: String): Seq[Seq[Int]] = {
    val components: ListBuffer[String] = ListBuffer[String]()
    MiscUtilities.splitCollection(Seq(4, 2, 2, 2, 2, 2), candidate, components)
    val c: Seq[Int] = components.map(_.toInt).toSeq
    Seq(c, Seq(c.head, c(2), c(1), c(3), c(4), c(5)))
  }

  /** Determines whether a candidate [[String]] is a valid timestamp / date
   *
   * @param candidate [[String]] potentially representing a valid timestamp / date
   * @return Flag indicating whether candidate [[String]] is a valid timestamp / date
   */
  def isValidCandidate(candidate: String): Boolean = {
    extractTimestampComponents(candidate).map {
      (components: Seq[Int]) =>
        components.zipWithIndex
          .map {
            case (timestampElement: Int, idx: Int) =>
              idx match {
                // idx 2 is representing the days per month, which are to be calculated exactly
                case 2 =>
                  Try {
                    (1 to YearMonth
                      .of(components.head, components(1))
                      .lengthOfMonth)
                      .contains(timestampElement)
                  }
                    .getOrElse(false)
                case _ =>
                  Constants
                    .TimestampRanges(idx)
                    .contains(timestampElement)
              }
          }
          .forall(_ == true)
    }
      .foldLeft(false)(_ || _) // Check if at least one of the possible formats (European, American) is valid
  }

  /** Adds [[LocalTime]] to a date
   * If there is at least one exif timestamp supporting the date present in the file name, the former's time portion
   * is added to the date. If not, a default time is added
   *
   * @param filePath [[Path]] to the file
   * @param date     [[String]] representation of a potentially valid date
   * @return Potentially valid [[LocalDateTime]]
   */
  def addTimeToDate(filePath: Path, date: String): Option[LocalDateTime] = {
    val coincidingExifTimestamps: ListBuffer[LocalDateTime] =
      ListBuffer[LocalDateTime]()
    readExifTimestamps(filePath)
      .foreach {
        case (_: String, oldt: Option[LocalDateTime]) =>
          oldt match {
            case Some(ldt: LocalDateTime) =>
              if (Try {
                ldt.getYear == date.substring(0, 4).toInt &
                  ldt.getMonthValue == date.substring(4, 6).toInt &
                  ldt.getDayOfMonth == date.substring(6, 8).toInt
              }.getOrElse(false))
                coincidingExifTimestamps += ldt
            case None =>
          }
      }
    Try {
      Some(coincidingExifTimestamps.min)
    }.getOrElse {
      Try {
        Some(
          convertStringToTimestamp(date + Constants.DefaultTime,
            Constants.TimestampFormatters("file")).get)
      }.getOrElse {
        MiscUtilities.getFeedback(
          s"Is $date a valid partial date?",
          Seq("y", "n")
        ) match {
          case "y" =>
            Some(
              convertStringToTimestamp(
                date + Constants.DefaultDay + Constants.DefaultTime,
                Constants.TimestampFormatters("file")
              ).get
            )
          case _ =>
            None
        }
      }
    }
  }

  /** Extracts the exif timestamp from ExifTool output, if possible
   *
   * @param filePath [[Path]] to the file
   * @return [[Map]] of tag identifier and the optional corresponding [[LocalDateTime]]
   */
  def readExifTimestamps(filePath: Path): Map[String, Option[LocalDateTime]] = {
    StringUtilities
      .prepareExifToolOutput(constructExifToolGetAllTimestampsCommand(filePath))
      .map { 
        (descriptorAndTimestamp: Array[String]) =>
          descriptorAndTimestamp.head ->
            convertStringToTimestamp(descriptorAndTimestamp.last,
              Constants.TimestampFormatters("exif"))
      }
      .toMap
  }

  /** Constructs the ExifTool command to extract all timestamps
   *
   * @param filePath [[Path]] to the file
   * @return [[String]] representing the ExifTool command
   */
  def constructExifToolGetAllTimestampsCommand(filePath: Path): String = {
    s"""${Constants.ExifToolBaseCommand} -time:all -m -s "$filePath""""
  }

  /** Converts [[String]] to [[LocalDateTime]], if possible (core method)
   *
   * @param timestamp [[String]] potentially containing the [[LocalDateTime]]
   * @param dtf       [[DateTimeFormatter]] used to convert [[String]] to [[LocalDateTime]]
   * @return Optional [[LocalDateTime]]
   */
  def convertStringToTimestamp(timestamp: String,
                               dtf: DateTimeFormatter): Option[LocalDateTime] =
    Try {
      LocalDateTime.parse(timestamp, dtf)
    } match {
      case Success(ldt: LocalDateTime) =>
        if (ldt.getYear == 1970 & ldt.getMonthValue == 1 & ldt.getDayOfMonth == 1)
          None
        else
          Some(ldt)
      case Failure(_) =>
        Try {
          Some(
            ZonedDateTime
              .parse(timestamp, Constants.TimestampFormatters("zonedExif"))
              .toLocalDateTime)
        } match {
          case Success(ldt: Some[LocalDateTime]) =>
            ldt
          case Failure(_) =>
            Try {
              Some(
                LocalDateTime.parse(timestamp, Constants.TimestampFormatters("exif2")))
            }
              .getOrElse(None)
        }
    }

  /** Gets all exif timestamps available for the file
   *
   * @param timestamps [[Map]] with exif timestamp ids as keys, and optional [[LocalDateTime]] as values
   * @return [[Iterable]] of existing [[LocalDateTime]]
   */
  def getExifTimestamps(timestamps: Map[String, Option[LocalDateTime]])
  : Iterable[LocalDateTime] = {
    timestamps.values.flatten
  }

}