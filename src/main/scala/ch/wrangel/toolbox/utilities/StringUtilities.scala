// utilities.StringUtilities.scala
package ch.wrangel.toolbox.utilities

/* Utilities for [[String]] manipulation */
object StringUtilities {

  /** Prepares the output of an ExifTool command for further processing
    *
    * @param command ExifTool command to execute
    * @return Array of String arrays with tag and value pairs, filtering unwanted lines
    */
  def prepareExifToolOutput(command: String): Array[Array[String]] = {
    MiscUtilities.getProcessOutput(command)
      .map { output =>
        output
          .split("\n")
          .map(_.split(" : ").map(_.trim))
          .filterNot(arr => arr.headOption.exists(h => h.contains("scanned") || h.contains("read")))
      }
      .getOrElse(Array.empty)
  }

}
