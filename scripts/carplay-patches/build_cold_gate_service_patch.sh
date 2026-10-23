#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: build_cold_gate_service_patch.sh BASE_APK OUTPUT_APK WORK_DIR" >&2
    exit 64
fi

base_apk=$1
output_apk=$2
work_dir=$3
apktool_jar=${APKTOOL_JAR:-"${HOME}/BuildQueue/tools/apktool_3.0.2.jar"}
expected_apktool_sha=eee4669a704a14e0623407e6701b0b91887e61e1e4049cb7a82833e14ae8b5fd
expected_base_sha=0a44373f2acfa21b3c0d2af18eb8f67217398592eeca898015274afb7f030fb0

case "$work_dir" in
    */build/intermediates/carplay-cold-gate) ;;
    *) echo "refusing unexpected work directory" >&2; exit 64 ;;
esac
case "$output_apk" in
    */build/outputs/carplay-patches/TsCarPlayService-cold-gate.apk) ;;
    *) echo "refusing unexpected output path" >&2; exit 64 ;;
esac

[[ -f $base_apk ]] || { echo "missing baseline service APK" >&2; exit 66; }
[[ -f $apktool_jar ]] || { echo "missing authorized apktool_3.0.2.jar" >&2; exit 69; }

sha256_file() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        sha256sum "$1" | awk '{print $1}'
    fi
}

[[ $(sha256_file "$apktool_jar") == "$expected_apktool_sha" ]] || {
    echo "apktool digest mismatch" >&2
    exit 65
}
[[ $(sha256_file "$base_apk") == "$expected_base_sha" ]] || {
    echo "baseline service APK digest mismatch" >&2
    exit 65
}

android_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-"${HOME}/Library/Android/sdk"}}
build_tools=$(find "$android_sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort | tail -1)
zipalign="$build_tools/zipalign"
apksigner="$build_tools/apksigner"
debug_keystore=${DEBUG_KEYSTORE:-"${HOME}/.android/debug.keystore"}

[[ -x $zipalign && -x $apksigner ]] || { echo "Android build tools unavailable" >&2; exit 69; }
[[ -f $debug_keystore ]] || { echo "authorized Debug keystore unavailable" >&2; exit 69; }

rm -rf "$work_dir"
mkdir -p "$work_dir" "$(dirname "$output_apk")"
decoded="$work_dir/decoded"
unsigned="$work_dir/TsCarPlayService-cold-gate-unsigned.apk"
aligned="$work_dir/TsCarPlayService-cold-gate-aligned.apk"
verification="$work_dir/verification"

java -jar "$apktool_jar" d -f -o "$decoded" "$base_apk"
python3 "$(dirname "$0")/patch_cold_start_gate.py" "$decoded"
# The OEM APK has no resources.arsc. Apktool cannot relink its decoded manifest
# against vendor framework attributes, so preserve the authenticated original
# binary manifest and replace only the rebuilt DEX.
java -jar "$apktool_jar" b --copy-original -o "$unsigned" "$decoded"
"$zipalign" -f 4 "$unsigned" "$aligned"
"$apksigner" sign \
    --ks "$debug_keystore" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$output_apk" \
    "$aligned"
"$apksigner" verify --verbose --print-certs "$output_apk"

java -jar "$apktool_jar" d -f -o "$verification" "$output_apk" >/dev/null
[[ $(python3 "$(dirname "$0")/patch_cold_start_gate.py" "$verification") == "already-patched" ]] || {
    echo "rebuilt service APK did not retain the exact cold gate" >&2
    exit 65
}

output_sha=$(sha256_file "$output_apk")
[[ $output_sha != "$expected_base_sha" ]] || { echo "patched output unexpectedly matches baseline" >&2; exit 65; }
printf 'cold_gate_service_sha256=%s\n' "$output_sha"
