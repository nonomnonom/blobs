#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

bash ./gradlew -Pandroid.testInstrumentationRunnerArguments.notClass=dev.blobs.app.MultiLibraryRetrievalScalingTest connectedDebugAndroidTest --console=plain
bash ./gradlew :app:installDebug :app:installDebugAndroidTest --console=plain

stage=/sdcard/Android/data/dev.blobs.app.debug/files
adb shell mkdir -p "$stage"
adb push ../corpus/evaluation/cases/multi-library-retrieval-scaling-v5.json "$stage/retrieval-benchmark-multi-library.json"
adb push .local/ci-multi-library/*.blobs "$stage/"

report=app/build/reports/androidTests/connected/debug/multi-library-retrieval-scaling.log
mkdir -p "$(dirname -- "$report")"
adb shell am instrument -w -e class dev.blobs.app.MultiLibraryRetrievalScalingTest \
    dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee "$report"
grep -q 'OK (1 test)' "$report"
