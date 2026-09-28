#!/usr/bin/env bash
set -euo pipefail

echo "Generating Gradle dependency verification metadata..."
echo "This is a deliberate maintenance command: review the resulting diff before committing."

./gradlew   --write-verification-metadata sha256   --export-keys   help   test   lintRelease   assembleDebug   --no-daemon

echo
echo "Generated/updated:"
echo "  gradle/verification-metadata.xml"
echo "  gradle/verification-keyring.keys (when signature keys are available)"
echo
echo "Now review the diff. Do not blindly accept unexpected new repositories, components, or checksums."
