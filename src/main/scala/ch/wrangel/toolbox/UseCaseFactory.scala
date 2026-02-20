package ch.wrangel.toolbox

import ch.wrangel.toolbox.utilities.{FileUtilities, MiscUtilities, StringUtilities, TimestampUtilities}
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import wvlet.log.LogSupport
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer

/**
 * Factory object for creating and managing different UseCases.
 * Implements the factory pattern to return use case instances based on a command string.
 */
object UseCaseFactory extends LogSupport {

  /**
   * Use case implementation that uses Exif timestamps as reference.
   */
  private object ExifAsReference extends UseCase {

    def run(directory: String, needsRenaming: Boolean, treatExifTimestamps: Boolean): Unit = {
      val treatedFiles = new ConcurrentHashMap[Path, LocalDateTime]()
      val treatedFiles2 = new ConcurrentHashMap[Path, LocalDateTime]()

      FileUtilities
        .iterateFiles(directory)
        .foreach { filePath =>
          val (principalTimestamps, secondaryTimestamps) = TimestampUtilities
            .readExifTimestamps(filePath)
            .partition { case (tag, _) => Constants.ReferenceExifTimestamps.contains(tag) }

          if (principalTimestamps.nonEmpty && principalTimestamps.forall(_._2.isDefined)) {
            handlePrincipalTimestamps(principalTimestamps, filePath, needsRenaming)
              .foreach { case (path, timestamp) => treatedFiles.put(path, timestamp) }
          } else if (treatExifTimestamps) {
            handleSecondaryTimestamps(secondaryTimestamps, filePath, needsRenaming)
              .foreach { case (path, timestamp) => treatedFiles2.put(path, timestamp) }
          } else {
            warn(s"======== Omitting $filePath")
          }
        }

      TimestampUtilities.writeTimestamps(treatedFiles.asScala.toMap)
      TimestampUtilities.writeTimestamps(treatedFiles2.asScala.toMap)
      Validate.run(directory, needsRenaming)

      if (treatExifTimestamps) {
        val output = MiscUtilities.getProcessOutput("""osascript -e 'quit app "Preview"'""")
        output.foreach(result => info(s"Process output: $result"))
      }
    }

    private def handlePrincipalTimestamps(
        principalTimestamps: Map[String, Option[LocalDateTime]],
        filePath: Path,
        needsRenaming: Boolean
    ): Seq[(Path, LocalDateTime)] = {
      info("Handling principal timestamps")
      TimestampUtilities
        .getExifTimestamps(principalTimestamps)
        .headOption
        .map(ldt => Seq(FileUtilities.prepareFile(filePath, ldt, needsRenaming)))
        .getOrElse(Seq.empty)
    }

    private def handleSecondaryTimestamps(
        secondaryTimestamps: Map[String, Option[LocalDateTime]],
        filePath: Path,
        needsRenaming: Boolean
    ): Seq[(Path, LocalDateTime)] = {
      info("Handling secondary timestamps")
      MiscUtilities.getProcessOutput(s"""open -a Preview ${filePath.toString}""")
      val candidateTimestamps: Seq[LocalDateTime] =
        TimestampUtilities.getExifTimestamps(secondaryTimestamps).toSeq.sorted
      val options: Seq[(LocalDateTime, Int)] = candidateTimestamps.zipWithIndex
      val result = if (candidateTimestamps.nonEmpty) {
        val feedback: String = MiscUtilities
          .getFeedback(
            options.mkString("\n") + "\nNone of those: -\n",
            Constants.NonApplicableKey +: options.map(_._2.toString)
          )
        if (feedback != Constants.NonApplicableKey)
          Seq(FileUtilities.prepareFile(
            filePath,
            candidateTimestamps(feedback.toInt),
            needsRenaming = needsRenaming
          ))
        else Seq.empty
      } else {
        warn("No valid timestamps found")
        Seq.empty
      }
      MiscUtilities.getProcessOutput(
        """osascript -e 'tell application "Preview" to close first window'""")
      result
    }
  }

  private object FileNameAsReference extends UseCase {
    def run(directory: String, needsRenaming: Boolean, treatExifTimestamps: Boolean): Unit = {
      val treatedFiles = new ConcurrentHashMap[Path, LocalDateTime]()

      TimestampUtilities
        .detectHiddenTimestampsOrDates(directory)
        .foreach { case (filePath, ldt) =>
          treatedFiles.put(filePath, ldt)
        }

      TimestampUtilities.writeTimestamps(treatedFiles.asScala.toMap, treatExifTimestamps)
      Validate.run(directory)
    }
  }

  private object Validate extends UseCase {
    def run(directory: String, needsRenaming: Boolean = false, treatExifTimestamps: Boolean = false): Unit = {
      val treatedFiles = new ConcurrentHashMap[Path, LocalDateTime]()

      FileUtilities
        .iterateFiles(directory)
        .foreach { filePath =>
          info(s"======== Validating $filePath")
          if (Constants.isNotExiftoolTmpFile(filePath.getFileName.toString)) {
            checkFileTimestamp(filePath) match {
              case Some(filenameTimestamp) =>
                val exifResults = Constants.ReferenceExifTimestamps.flatMap { tag =>
                  checkValidity(filePath, tag).flatMap { element =>
                    convertExifTimestamp(element) match {
                      case Some(exifTimestamp) =>
                        Some(compareTimestamps(filePath, filenameTimestamp, exifTimestamp, tag, treatedFiles))
                      case None =>
                        warn(s"$tag cannot be converted properly")
                        None
                    }
                  }
                }
                if (exifResults.isEmpty) {
                  treatedFiles.put(filePath, LocalDateTime.now())
                }

              case None =>
                warn(s"File timestamp contains no valid timestamp")
                treatedFiles.put(filePath, LocalDateTime.now())
            }
          } else {
            warn(s"File is a remnant exiftool temp file")
            treatedFiles.put(filePath, LocalDateTime.now())
          }
        }

      FileUtilities.moveFiles(
        ListBuffer(treatedFiles.asScala.map(_._1).toSeq*),
        Paths.get(directory, Constants.UnsuccessfulFolder)
      )
    }

    private def checkFileTimestamp(filePath: Path): Option[LocalDateTime] = {
      val fileName: String =
        FileUtilities.splitExtension(filePath, isPathNeeded = false).head
      TimestampUtilities.convertStringToTimestamp(
        Try {
          fileName.substring(0, fileName.indexOf(Constants.PartitionString))
        } match {
          case Success(s: String) => s
          case Failure(_) => fileName
        },
        Constants.TimestampFormatters("file")
      )
    }

    private def checkValidity(filePath: Path, tag: String): Option[Array[String]] = {
      StringUtilities
        .prepareExifToolOutput(
          s"""${Constants.ExifToolBaseCommand} -s -$tag "$filePath""""
        )
        .headOption
    }

    private def convertExifTimestamp(element: Array[String]): Option[LocalDateTime] = {
      Constants.TimestampFormatters
        .flatMap(ts => TimestampUtilities.convertStringToTimestamp(element.last, ts._2))
        .headOption
    }

    private def compareTimestamps(
      filePath: Path, 
      filenameTimestamp: LocalDateTime, 
      exifTimestamp: LocalDateTime, 
      tag: String,
      treatedFiles: ConcurrentHashMap[Path, LocalDateTime]
    ): Unit = {
      info(s"Comparing file timestamp $filenameTimestamp with $tag $exifTimestamp")
      if (!filenameTimestamp.equals(exifTimestamp)) {
        warn(s"Timestamps do not match")
        treatedFiles.put(filePath, LocalDateTime.now())
      } else {
        info(s"Timestamps match")
      }
    }
  }

  def apply(useCase: String): UseCase = useCase match {
    case "exif" => ExifAsReference
    case "file" => FileNameAsReference
    case "validate" => Validate
    case _ => throw new IllegalArgumentException(s"Unknown use case: $useCase")
  }
}
