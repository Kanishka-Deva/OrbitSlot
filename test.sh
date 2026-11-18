#!/usr/bin/env sh
# Compile src/ and test/ and run all JUnit 5 tests (macOS/Linux/Git Bash). No build tool needed.
#
# Usage:
#   ./test.sh                                              all tests
#   ./test.sh --select-class orbitslot.scheduler.SchedulerTest
set -e
cd "$(dirname "$0")"

rm -rf out-test
find src test -name '*.java' > out_test_sources.txt
javac --release 21 -encoding UTF-8 -d out-test -cp 'lib/*' @out_test_sources.txt
rm -f out_test_sources.txt

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
CP="out-test"
for jar in lib/*.jar; do
  case "$jar" in *junit-platform-console-standalone*) ;; *) CP="$CP$SEP$jar" ;; esac
done

if [ "$#" -eq 0 ]; then set -- --scan-class-path; fi
java -jar lib/junit-platform-console-standalone-1.11.3.jar execute \
  --class-path "$CP" --disable-banner --details=tree "$@"
