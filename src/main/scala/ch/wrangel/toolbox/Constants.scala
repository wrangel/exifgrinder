// Constants.scala

package ch.wrangel.toolbox

import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex

/** Holds project-wide constants used throughout the application */
object Constants {

  /** Representation of blank split character */
  final val BlankSplitter: String = " "

  /** Identifier string for the caffeinate process */
  final val CaffeinateIdentifier: String = "caffeinate"

  /** Default day string used when imputing missing day in dates */
  final val DefaultDay: String = "01"

  /** Default time string used when imputing missing time in dates */
  final val DefaultTime: String = "_000100"

  /** The user Downloads folder path as a string */
  val DownloadFolder: String =
    Paths.get(System.getProperty("user.home"), "/Downloads/").toString

  /** File types excluded during file processing */
  final val ExcludedFileTypes: Seq[String] = Seq(".txt")

  /** Content string to use when creating ExifTool config file */
  final val ExifToolConfigFileContent: String =
    "%Image::ExifTool::UserDefined::Options = (\n\tLargeFileSupport => 1,\n);"

  /** Path to ExifTool config file in the current working directory */
  final val ExifToolConfigFilePath: Path =
    Paths.get(System.getProperty("user.dir"), "exif.config")

  /** Base command string to invoke ExifTool with the config file */
  final val ExifToolBaseCommand: String =
    s"/usr/local/bin/exiftool -config $ExifToolConfigFilePath"

  /** Official ExifTool download website URL */
  final val macPkgUrl: String = "https://sourceforge.net/projects/exiftool/files/"

  /** Identifier for the Mac hdutil command-line tool */
  final val HdiUtilIdentifier: String = "hdiutil"

  /** Image identifiers relevant for dmg images */
  final val ImageIdentifiers: Seq[String] = Seq("ExifTool", ".dmg")

  /** Predicate to detect non-exiftool temporary files by filename */
  final val isNotExiftoolTmpFile: String => Boolean =
    (filename: String) => !filename.endsWith("exiftool_tmp")

  /** Mapping from friendly names to MacOS SetFile timestamp flag strings */
  final val MacOsTimestampTags: Map[String, Seq[String]] = Map(
    "create" -> Seq("d"),
    "modify" -> Seq("m")
  )

  /** Key string representing a non-applicable timestamp in secondary timestamp checks */
  final val NonApplicableKey: String = "-"

  /** Allowed command argument space mapping for various flags */
  final val ParameterSpace: Map[Seq[String], Seq[String]] = Map(
    Seq("-e", "-r", "-s") -> Seq("exif", "true", "true"),
    Seq("-e", "-r") -> Seq("exif", "true"),
    Seq("-e") -> Seq("exif", "false", "true"),
    Seq("-f", "-r", "-e") -> Seq("file", "true", "true"),
    Seq("-f", "-r") -> Seq("file", "true"),
    Seq("-f", "-e") -> Seq("file", "false", "true"),
    Seq("-f") -> Seq("file"),
    Seq("-v") -> Seq("validate")
  )

  /** String used as separator between timestamp and filename */
  final val PartitionString: String = "__"

  /** Collection of reference Exif timestamp field names */
  final val ReferenceExifTimestamps: Seq[String] = Seq(
    "DateTimeOriginal",
    "CreateDate"
  )

  /** End screen displayed after running the tool */
  val TextEnd: String = {
    """The procedure ran through. You may close all associated Terminal windows now.
      |""".stripMargin
  }

  /** Welcome screen shown on tool startup, describing usage instructions */
  val TextWelcome: String = {
    """Welcome to the photo and video timestamp toolbox.
      |This Scala tool only works on Mac.
      |It makes use of ExifTool (https://exiftool.org), installs it or updates it whenever necessary,
      |and an internet connection is available.
      |Parameters:
      |   -e -r -s <directory string>
      |       Primary exif timestamps as reference (CreateDate and DateTimeOriginal)
      |       Rename the file with a prepending timestamp
      |       Treat secondary timestamps as well (not CreateDate or DateTimeOriginal)
      |   -e -r <directory string>
      |       Primary exif timestamps as reference (CreateDate and DateTimeOriginal)
      |       Rename the file with a prepending timestamp
      |   -e <directory string>
      |       Primary exif timestamps as reference (CreateDate and DateTimeOriginal)
      |   -f -r -e <directory string>
      |       Valid timestamp contained in filename as reference
      |       Rename the file with a prepending timestamp
      |       Treat exif timestamps
      |   -f -r <directory string>
      |       Valid timestamp contained in filename as reference
      |       Rename the file with a prepending timestamp
      |   -f -e <directory string>
      |       Valid timestamp contained in filename as reference
      |       Treat exif timestamps
      |   -f <directory string>
      |       Valid timestamp contained in filename as reference
      |   -v <directory string>
      |       Validate if the timestamp in file name and principal exif timestamps
      |       (DateTimeOriginal / Create Date) coincide. Move the file to a sub folder otherwise
      |""".stripMargin
  }

  /** Regex patterns used to detect timestamps and dates hidden in file names */
  final val TimestampAndDatePatterns: Seq[Regex] = Seq(
    "[0-9]{4}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}",
    "[0-9]{4}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2} at [0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}",
    "[0-9]{4}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2} um [0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}",
    "[0-9]{4}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}T[0-9]{2}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}",
    "[0-9]{8}[-,_,/,., ][0-9]{6}",
    "[0-9]{14}",
    "[0-9]{4}[-,_,/,., ][0-9]{2}[-,_,/,., ][0-9]{2}",
    "[0-9]{8}",
    "[0-9]{4}[-,_,/,., ][0-9]{2}",
    "[0-9]{6}"
  ).map(_.r)

  /** Map of string keys to DateTimeFormatter instances for various timestamp patterns */
  final val TimestampFormatters: Map[String, DateTimeFormatter] = Map(
    "exif" -> DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
    "exif2" -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    "zonedExif" -> DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssz"),
    "mac" -> DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
    "file" -> DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
  )

  /** Allowed value ranges for each component of a timestamp */
  final val TimestampRanges: Seq[Range] = {
    val today: LocalDateTime = LocalDateTime.now
    Seq(
      today.minusYears(100).getYear to today.getYear,
      1 to 12,
      1 to 31, // to be refined during validation by month
      0 to 23,
      0 to 59,
      0 to 59
    )
  }

  /** Folder name used for files with unsuccessful Exif manipulation */
  final val UnsuccessfulFolder: String = "_unsuccessful"

  /** Folder name used for zero-byte files */
  final val ZeroByteFolder: String = "_zeroByte"

}
