#!/usr/bin/env bash
# Runs a Gradle command and, if it fails, republishes the relevant error lines as GitHub
# annotations so they are readable through the REST API and on the run summary page
# without downloading the full log.
set -uo pipefail

log="$(mktemp)"
./gradlew --stacktrace "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ]; then
  grep -E -A3 '^e: |^w: .*error|error:|What went wrong|FAILED|Exception|Caused by|> Could not|> Task .* FAILED|Unresolved reference|Cannot |expected:<|AssertionError|\.kt:[0-9]+' "$log" \
    | grep -v -E '^\s+at (org\.gradle|java\.|jdk\.|sun\.|kotlin\.coroutines|worker\.org\.gradle)' \
    | awk '!seen[$0]++' \
    | head -n 400 > "$log.errors"
  # Lint prints only the first issue to the console; append every error from its text report.
  for report in app/build/intermediates/lint_intermediate_text_report/*/*/lint-results-*.txt; do
    [ -f "$report" ] && grep -E -A4 ': Error: ' "$report" >> "$log.errors"
  done

  # GitHub keeps at most 10 error annotations per step; send the lines in up to 9 chunks.
  split -n l/9 "$log.errors" "$log.chunk." 2>/dev/null || cp "$log.errors" "$log.chunk.aa"
  for chunk in "$log".chunk.*; do
    [ -s "$chunk" ] || continue
    message="$(sed -e 's/%/%25/g' -e 's/\r//g' "$chunk" | awk '{printf "%s%%0A", $0}')"
    echo "::error title=Gradle $*::${message}"
  done
fi
exit "$status"
