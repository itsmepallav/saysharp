#!/usr/bin/env bash
# Bounded UI QA; keeps plugged-in hardware awake and restores settings/permission.
# Build/install the matching debug app and test APK first. Never clears/uninstalls.
set -euo pipefail
isolated_runs=${1:-10}
full_runs=${2:-2}
[[ "$isolated_runs" =~ ^[0-9]+$ && "$full_runs" =~ ^[0-9]+$ ]] || { echo 'Usage: r1-device-preflight.sh [isolated-count] [full-count]' >&2; exit 2; }
(( isolated_runs <= 20 && full_runs <= 5 && isolated_runs + full_runs > 0 )) || { echo 'Use 0–20 isolated and 0–5 full runs, at least one total.' >&2; exit 2; }
project_root=$(cd "$(dirname "$0")/.." && pwd)
adb_bin=$(command -v adb || true)
if [[ -z "$adb_bin" ]]; then adb_bin="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/platform-tools/adb"; fi
[[ -x "$adb_bin" ]] || { echo 'adb unavailable; set ANDROID_SDK_ROOT.' >&2; exit 2; }
"$adb_bin" get-state >/dev/null # ANDROID_SERIAL selects a device; multiple unspecified devices fail safely.
package=com.saysharp.saysharp
runner=com.saysharp.saysharp.test/androidx.test.runner.AndroidJUnitRunner
output="$project_root/app/build/reports/r1-08/device-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$output"
original_awake=$("$adb_bin" shell settings get global stay_on_while_plugged_in | tr -d '\r')
package_info=$("$adb_bin" shell dumpsys package "$package")
if grep -q 'android.permission.RECORD_AUDIO: granted=true' <<< "$package_info"; then original_mic=true; else original_mic=false; fi
restore() {
    if [[ "$original_awake" == null ]]; then
        "$adb_bin" shell settings delete global stay_on_while_plugged_in >/dev/null
    else
        "$adb_bin" shell settings put global stay_on_while_plugged_in "$original_awake"
    fi
    if [[ "$original_mic" == false && "$full_runs" -gt 0 ]]; then
        "$adb_bin" shell pm revoke "$package" android.permission.RECORD_AUDIO
    fi
}
trap restore EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'original_stay_awake=%s original_microphone_grant=%s\n' "$original_awake" "$original_mic" > "$output/environment.txt"
"$adb_bin" shell svc power stayon true
"$adb_bin" shell input keyevent KEYCODE_WAKEUP # Does not bypass a secure lock; unlock before running.
if [[ "$full_runs" -gt 0 && "$original_mic" == false ]]; then
    "$adb_bin" shell pm grant "$package" android.permission.RECORD_AUDIO
fi
failures=0
run_check() {
    local label=$1
    shift
    local log="$output/$label.log"
    if "$adb_bin" shell am instrument -w -r "$@" "$runner" > "$log" 2>&1; then :; fi
    # Runner success is in its output, not adb's exit status. Skipped native checks are not a pass.
    if grep -Eq 'OK \([0-9]+ tests?\)' "$log" && ! grep -Eq 'INSTRUMENTATION_STATUS_CODE: -[234]' "$log"; then
        echo "$label PASS"
    else
        failures=$((failures + 1))
        echo "$label FAIL ($log)"
    fi
}
for (( run=1; run<=isolated_runs; run++ )); do
    run_check "isolated-$run" -e class 'com.saysharp.saysharp.CurriculumFlowTest#everyOrdinaryLessonHidesExamplesUntilReplaySelfCheckAndCorrection'
done
for (( run=1; run<=full_runs; run++ )); do run_check "full-$run"; done
"$adb_bin" logcat -d -s TestRunner:I ActivityTaskManager:I PowerManagerService:I AndroidRuntime:E R107_PERF:I R108_PERF:I '*:S' > "$output/device.log"
printf 'isolated=%s full=%s failed_runs=%s\n' "$isolated_runs" "$full_runs" "$failures" | tee "$output/summary.txt"
echo "Evidence: $output"
[[ "$failures" == 0 ]]
