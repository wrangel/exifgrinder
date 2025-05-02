// UseCaseFactory.scala
package ch.wrangel.toolbox

import ch.wrangel.toolbox.utilities.{FileUtilities, MiscUtilities, StringUtilities, TimestampUtilities}
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import wvlet.log.LogSupport
import scala.collection.parallel.CollectionConverters._
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer

object UseCaseFactory extends LogSupport {

  private object ExifAsReference extends UseCase {
    def run(directory: String,
            needsRenaming: Boolean,
            treatExifTimestamps: Boolean): Unit = {

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
    def run(directory: String,
            needsRenaming: Boolean,
            treatExifTimestamps: Boolean): Unit = {
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
    def run(directory: String,
            needsRenaming: Boolean = false,
            treatExifTimestamps: Boolean = false): Unit = {
      val treatedFiles = new ConcurrentHashMap[Path, LocalDateTime]()

      FileUtilities
        .iterateFiles(directory)
        .foreach { filePath =>
          info(s"======== Validating $filePath")
          if (Constants.isNotExiftoolTmpFile(filePath.getFileName.toString)) {
            checkFileTimestamp(filePath) match {
              case Some(filenameTimestamp) =>
                val exifResults = Constants.ReferenceExifTimestamps.par.flatMap { tag =>
                  checkValidity(filePath, tag).flatMap { element =>
                    convertExifTimestamp(element) match {
                      case Some(exifTimestamp) =>
                        Some(compareTimestamps(filePath, filenameTimestamp, exifTimestamp, tag))
                      case None =>
                        warn(s"$tag cannot be converted properly")
                        None
                    }
                  }
                }
                if (exifResults.isEmpty) {
                  treatedFiles.put(filePath, LocalDateTime.now())
                  (): Unit // Explicitly discarding the returned value
                }

              case None =>
                warn(s"File timestamp contains no valid timestamp")
                treatedFiles.put(filePath, LocalDateTime.now)
            }
          } else {
            warn(s"File is a remnant exiftool temp file")
            treatedFiles.put(filePath, LocalDateTime.now)
          }
        }

      FileUtilities.moveFiles(
        ListBuffer(treatedFiles.asScala.map(_._1).toSeq: _*),
        Paths.get(directory, Constants.UnsuccessfulFolder)
      )
    }

    /** Attempts to extract a [[LocalDateTime]] from the file name
      *
      * @param filePath [[Path]] to the file
      * @return Optional [[LocalDateTime]] extracted from file name
      */
    private def checkFileTimestamp(filePath: Path): Option[LocalDateTime] = {
      val fileName: String =
        FileUtilities.splitExtension(filePath, isPathNeeded = false).head
      TimestampUtilities.convertStringToTimestamp(
        Try {
          fileName.substring(0, fileName.indexOf(Constants.PartitionString))
        } match {
          case Success(s: String) =>
            s
          case Failure(_) =>
            fileName
        },
        Constants.TimestampFormatters("file")
      )
    }

    /** Checks if shell command returns valid stdout
      *
      * @param filePath [[Path]] to the file
      * @param tag Relevant exif timestamp tag
      * @return Optional [[Array[String]]] containing the stdout
      */
    private def checkValidity(filePath: Path,
                              tag: String): Option[Array[String]] = {
      StringUtilities
        .prepareExifToolOutput(
          s"""${Constants.ExifToolBaseCommand} -s -$tag "$filePath""""
        )
        .headOption
    }

    /** Attempts to convert the exif timestamps to [[LocalDateTime]]
      *
      * @param element Output of exiftool stdout
      * @return Valid [[LocalDateTime]]
      */
    private def convertExifTimestamp(
        element: Array[String]): Option[LocalDateTime] = {
      Constants.TimestampFormatters
        .flatMap(
          ts =>
            TimestampUtilities.convertStringToTimestamp(
              element.last,
              ts._2
          ))
        .headOption
    }

    /** Compares exif timestamps with timestamp extracted from file name
      *
      * @param filePath [[Path]] to the file
      * @param filenameTimestamp [[LocalDateTime]] representing the timestamp extracted from the file name
      * @param exifTimestamp [[LocalDateTime]] representing the exif timestamp
      * @param tag Tag of the exif timestamp
      */
    private def compareTimestamps(filePath: Path,
                                  filenameTimestamp: LocalDateTime,
                                  exifTimestamp: LocalDateTime,
                                  tag: String): Unit = {
      info(
        s"Comparing file timestamp $filenameTimestamp" +
          s" with $tag $exifTimestamp"
      )
      if (!filenameTimestamp.equals(exifTimestamp)) {
        warn(s"Timestamps do not match")
        treatedFiles += ((filePath, LocalDateTime.now))
      } else
        info(s"Timestamps match")
    }
  }

  /** Factory method
    *
    * @param useCase Applicable use case
    * @return Use case singleton
    */
  def apply(useCase: String): UseCase = useCase match {
      case "exif" => ExifAsReference
      case "file" => FileNameAsReference
      case "validate" => Validate
      case _ => throw new IllegalArgumentException(s"Unknown use case: $useCase")
    }
}
