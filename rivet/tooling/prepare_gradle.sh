#!/usr/bin/env bash
# Warm the wrapper before running any build tasks. Retrying tests would hide failures.
set -euo pipefail
for attempt in 1 2 3 4; do
  echo "Preparing Gradle distribution (attempt $attempt/4)"
  if ./gradlew --version --no-daemon; then
    exit 0
  fi
  if [ "$attempt" -lt 4 ]; then
    delay=$((5 * (2 ** (attempt - 1))))
    echo "Gradle bootstrap failed; retrying in ${delay}s." >&2
    sleep "$delay"
  fi
done
echo "Gradle distribution could not be prepared after 4 attempts; build tasks were not started." >&2
exit 1
