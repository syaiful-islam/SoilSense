#!/usr/bin/env bash
# Runs inside the emulator: all instrumented tests, then the screenshot capture, then pulls the PNGs.
set -uo pipefail

bash .github/scripts/gradle-with-annotations.sh :app:connectedDebugAndroidTest
tests_status=$?

# connectedDebugAndroidTest uninstalls the app afterwards, so install again and run only the
# screenshot class with `am instrument`; the PNGs stay in the app's files dir until we pull them.
./gradlew --quiet :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class id.soilsense.app.screenshots.ScreenshotCaptureTest \
  id.soilsense.app.test/androidx.test.runner.AndroidJUnitRunner | tee instrument.log

mkdir -p screenshots
for f in $(adb shell run-as id.soilsense.app ls files/screenshots | tr -d '\r'); do
  adb exec-out run-as id.soilsense.app cat "files/screenshots/$f" > "screenshots/$f"
done
ls -l screenshots

if grep -q "FAILURES!!!" instrument.log; then
  echo "::error title=Screenshot capture::$(grep -A20 'There w' instrument.log | head -40 | tr '\n' ' ')"
fi
exit "$tests_status"
