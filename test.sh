#!/usr/bin/env bash
# Host tests for the pure classes (no Android): compiles them with tests/io/freemytv/*.java and runs each *Test.
# Needs a JDK and tests/lib/json.jar (tools/fetch_libs.sh).
set -euo pipefail
cd "$(dirname "$0")"

PURE="Ru Lang ProxyLink XrayConfig Forecast Debloat VpnRoutes DnsGuard"
JSON=tests/lib/json.jar
[ -f "$JSON" ] || { echo "Missing $JSON: run tools/fetch_libs.sh"; exit 1; }
# java and javac are Windows programs under Git Bash: their class path separator is ';'.
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

srcs=()
for c in $PURE; do [ -f "app/src/io/freemytv/$c.java" ] && srcs+=("app/src/io/freemytv/$c.java"); done
rm -rf out/test && mkdir -p out/test
javac -encoding UTF-8 -nowarn -cp "$JSON" -d out/test "${srcs[@]}" tests/io/freemytv/*.java

for t in tests/io/freemytv/*Test.java; do
  java -ea -Dfile.encoding=UTF-8 -cp "out/test$SEP$JSON" "io.freemytv.$(basename "$t" .java)"
done
