#!/usr/bin/env sh
# Compile all sources in src/ and run a main class (macOS/Linux). No build tool needed.
#
# Usage:
#   ./run.sh                                  runs orbitslot.Main
#   ./run.sh some args                        passes args to orbitslot.Main
set -e
cd "$(dirname "$0")"

MAIN="${MAIN:-orbitslot.Main}"

rm -rf out
find src -name '*.java' > out_sources.txt
javac --release 21 -encoding UTF-8 -d out -cp 'lib/*' @out_sources.txt
rm -f out_sources.txt

# Classpath separator is ';' on Windows (Git Bash), ':' elsewhere.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
java -cp "out${SEP}lib/*" "$MAIN" "$@"
