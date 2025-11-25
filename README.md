# exifgrinder

This is a toolbox to get creation timestamps from image exif data or, if exif data is not available, from the image's name.
If no timestamp is available, it is attempted to extract a creation date.
The image is given a prefix with the creation timestamp or date, if there isn't any already.
If exif data and file name differ, the exif data takes prevalence.
This helps you organize your images in a very consistent way.
Works only on Mac

Fully re-written in compliance with Scala 3.

Run sbt assembly to generate the uber jar.
