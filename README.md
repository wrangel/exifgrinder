# exifgrinder

## Overview

exifgrinder is a toolbox to extract and synchronize creation timestamps from image EXIF data or, if unavailable, from image filenames.  
If no timestamp is available, it attempts to deduce a creation date.  
Images are renamed by prefixing a consistent timestamp or date, prioritizing EXIF data over filename discrepancies.  
This utility helps organize images reliably and consistently.  
**Note:** Works only on Mac systems.

Fully rewritten for Scala 3 compliance.

---

## Features

- Extract creation timestamps from EXIF metadata.
- Fallback to extracting timestamps from filenames.
- Renames files with timestamp prefixes for consistent sorting.
- Validates and compares timestamps between metadata and filenames.
- Automatically handles EXIF tool installation and updates.
- Processes zero-byte files and manages failsafe folders.

---

## Installation and Build

Ensure you have [sbt](https://www.scala-sbt.org/) installed.  
To build the executable JAR with dependencies, run:

`sbt assembly`

---

## Metals Setup Fix

To resolve common Metals build issues in the project, run the provided script located in the root directory:

`./fix-metals.sh`

This script sets up necessary symbolic links and configurations for smooth Metals integration.

---

## Usage

Run the built jar using Java:

`java -jar target/scala-3.x/exifgrinder.jar options`

### Options:

| Flags      | Description                                                     |
| ---------- | --------------------------------------------------------------- |
| `-e -r -s` | Use EXIF primary timestamps, rename files, treat secondary EXIF |
| `-e -r`    | Use EXIF primary timestamps and rename files                    |
| `-e`       | Use EXIF primary timestamps only                                |
| `-f -r -e` | Use timestamps from file names, rename files, treat EXIF        |
| `-f -r`    | Use timestamps from file names and rename files                 |
| `-f -e`    | Use timestamps from file names, treat EXIF timestamps           |
| `-f`       | Use timestamps from file names only                             |
| `-v`       | Validate timestamps between filename and EXIF, move mismatches  |

Replace `<directory>` with your folder path.

---

## Contributing

Contributions and feedback are welcome! Please submit issues and pull requests via GitHub.

---

## License

[Include license information here]

---

## Additional Resources

- See [docs](link-to-docs) for detailed information.
- Report issues and request features on [GitHub repository](link-to-repo).
