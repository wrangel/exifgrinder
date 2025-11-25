// Main.scala
package ch.wrangel.toolbox

import ch.wrangel.toolbox.utilities.{FileUtilities, MiscUtilities}
import scala.util.Try
import wvlet.log.LogSupport

/**
 * Command line tool to synchronize photo file names, exif and mac timestamps.
 * 
 * This is the main object containing the application entry point.
 */
object Main extends LogSupport {

  /**
   * Main entry point for the application.
   * 
   * @param args Command line arguments.
   *             Expects one or more parameters which match configured parameter space flags, followed by a directory path.
   *             Example: -f -r -e /some/path
   */
  def main(args: Array[String]): Unit = {
    val relevantParameters = args.slice(0, args.length - 1).toSeq // Extract all but last arg as flags
    if (Constants.ParameterSpace.keys.toSeq.contains(relevantParameters)) {
      FileUtilities.createOrAdaptExifConfigFile()

      // Handle zero-byte files in target directory
      val arguments: Seq[String] = Constants.ParameterSpace(relevantParameters) :+ args.last
      FileUtilities.handleZeroByteLengthFiles(arguments.last)

      // Ensure ExifTool is installed/up-to-date
      MiscUtilities.handleExifTool()

      // Create and run the appropriate use case as per first argument
      UseCaseFactory(arguments.head).run(
        arguments.last,
        Try { arguments(1).toBoolean }.getOrElse(false),
        Try { arguments(2).toBoolean }.getOrElse(false)
      )
    } else {
      info(Constants.TextWelcome)
    }
  }
}
