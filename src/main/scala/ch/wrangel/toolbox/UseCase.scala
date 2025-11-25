// UseCase.scala

package ch.wrangel.toolbox

import java.nio.file.Path
import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import wvlet.log.LogSupport

/**
 * Base trait defining a UseCase in the application.
 * Provides common tools and contract for various UseCase implementations.
 */
trait UseCase extends LogSupport {

  /** 
   * List of files (Path to LocalDateTime pairs) to be processed.
   * Represents the primary set of files targeted by the UseCase.
   */
  val treatedFiles: ListBuffer[(Path, LocalDateTime)] = ListBuffer[(Path, LocalDateTime)]()

  /**
   * List of secondary files to be processed, similar to treatedFiles.
   * This allows handling multiple batches or categories of files.
   */
  val treatedFiles2: ListBuffer[(Path, LocalDateTime)] = ListBuffer[(Path, LocalDateTime)]()

  /**
   * Executes the UseCase's main logic.
   * 
   * @param directory String representation of the directory path to operate on.
   * @param needsRenaming Flag indicating whether files should be renamed as part of the process.
   * @param treatExifTimestamps Flag indicating whether secondary Exif timestamps should be processed.
   */
  def run(directory: String, needsRenaming: Boolean, treatExifTimestamps: Boolean): Unit
}
