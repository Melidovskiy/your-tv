#!/usr/bin/env bash
# Saves a screenshot of the TV to out/shots/<name>.png.
# The Mi TV's screencap prints a couple of driver lines before the PNG: they are cut off here.
# Usage: ADB="adb -s <ip>:5555" tools/shot.sh <name>
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-adb}"
mkdir -p out/shots
$ADB exec-out screencap -p > "out/shots/$1.raw"
python -I -c 'import sys; b = open(sys.argv[1], "rb").read(); i = b.find(b"\x89PNG"); assert i >= 0, "no PNG in screencap output"; open(sys.argv[2], "wb").write(b[i:])' \
  "out/shots/$1.raw" "out/shots/$1.png"
rm -f "out/shots/$1.raw"
echo "out/shots/$1.png"
