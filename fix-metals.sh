#!/bin/bash

echo "Stopping Metals and cleaning up caches..."

# Close VSCode (optional, uncomment if you want to automate closing)
# echo "Closing VSCode..."
# killall Code

# Delete Coursier cache to force redownloading Scala jars
echo "Deleting Coursier cache..."
rm -rf ~/.cache/coursier/v1/
rm -rf ~/Library/Caches/Coursier/v1/

# Delete Metals caches in your project workspace (adjust the path to your project)
echo "Deleting Metals cache..."
rm -rf /Users/matthiaswettstein/SynologyDrive/Matthias/Programming/exifgrinder/.metals/

# Change to your project directory
cd /Users/matthiaswettstein/SynologyDrive/Matthias/Programming/exifgrinder || exit

# Clean and compile the sbt project
echo "Cleaning and compiling the sbt project..."
sbt clean compile

# Regenerate Bloop config for Metals
echo "Regenerating Bloop build metadata..."
sbt bloopInstall

echo "Please restart VSCode now and run 'Metals: Connect to build server' command."

# Optional: If you have vscode-cli installed, you can restart VSCode and open the folder
# code --folder-uri /Users/matthiaswettstein/SynologyDrive/Matthias/Programming/exifgrinder
