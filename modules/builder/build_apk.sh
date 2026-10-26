#!/usr/bin/env bash
# build_apk.sh - Master orchestrator for building, dexing, packaging, and signing Antigravity APK.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
export BUILD_DIR="$PROJECT_ROOT/build"
export DIST_DIR="$PROJECT_ROOT/dist"
export APP_DIR="$PROJECT_ROOT/app"
export TOOLS_DIR="$PROJECT_ROOT/tools"

if [ -d "/data/user/0/com.agy.dev/files" ] && [ -w "/data/user/0/com.agy.dev/files" ]; then
    export HOME="/data/user/0/com.agy.dev/files"
    export PATH="/data/user/0/com.agy.dev/files/usr/bin:$PATH"
elif [ -d "/data/user/0/com.agy/files" ] && [ -w "/data/user/0/com.agy/files" ]; then
    export HOME="/data/user/0/com.agy/files"
    export PATH="/data/user/0/com.agy/files/usr/bin:$PATH"
fi
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

export FAST_BUILD=0
export DEV_MODE=0
export KR_RELEASE_MODE=0
export UNSIGNED_KR_RELEASE_MODE=0
export NO_BYPASS="${NO_BYPASS:-1}"
if [ "${REGION_BYPASS:-0}" = "1" ] || [ "${BYPASS:-0}" = "1" ]; then export NO_BYPASS=0; fi
if [ "${SKIP_GATES:-0}" = "1" ]; then export NO_BYPASS=1; fi
export CUSTOM_PREFIX=""
export CUSTOM_OUT=""

while [ $# -gt 0 ]; do
    case "$1" in
        --fast)
            export FAST_BUILD=1
            shift
            ;;
        --dev|-d)
            export DEV_MODE=1
            shift
            ;;
        --unsigned)
            export UNSIGNED_KR_RELEASE_MODE=1
            shift
            ;;
        --kr-release)
            export KR_RELEASE_MODE=1
            shift
            ;;
        --bypass|--region-bypass)
            export NO_BYPASS=0
            shift
            ;;
        --no-bypass|--nobypass|--no-region-bypass|--skip-gates|--no-gates|--standard)
            export NO_BYPASS=1
            shift
            ;;
        --prefix)
            export CUSTOM_PREFIX="$2"
            shift 2
            ;;
        --out|-o)
            export CUSTOM_OUT="$2"
            shift 2
            ;;
        *)
            echo "[WARNING] Unknown argument: $1" >&2
            shift
            ;;
    esac
done

if [ "$KR_RELEASE_MODE" = "1" ] && [ "$DEV_MODE" = "1" ]; then
    echo "[ERROR] --kr-release cannot be combined with --dev" >&2
    exit 1
fi
if [ "$UNSIGNED_KR_RELEASE_MODE" = "1" ] && [ "$KR_RELEASE_MODE" != "1" ]; then
    echo "[ERROR] --unsigned is allowed only with --kr-release" >&2
    exit 1
fi
BASE_PREFIX="${CUSTOM_PREFIX:-antigravity}"

if [ -n "$CUSTOM_OUT" ]; then
    OUT_APK="$CUSTOM_OUT"
    EDITION_LABEL="CUSTOM OUTPUT ($OUT_APK)"
elif [ "$KR_RELEASE_MODE" = "1" ]; then
    if [ "$NO_BYPASS" = "0" ]; then
        OUT_APK="$DIST_DIR/${BASE_PREFIX}-v2.4.5-kr1-region-bypass.apk"
        EDITION_LABEL="KR RELEASE REGION BYPASS (com.krmobdev.antigravity, ports 45159/45162, non-debuggable)"
    else
        OUT_APK="$DIST_DIR/${BASE_PREFIX}-v2.4.5-kr1.apk"
        EDITION_LABEL="KR RELEASE STANDARD (com.krmobdev.antigravity, ports 45159/45162, non-debuggable)"
    fi
elif [ "$DEV_MODE" = "1" ]; then
    if [ "$NO_BYPASS" = "0" ]; then
        OUT_APK="$DIST_DIR/${BASE_PREFIX}-dev-region-bypass.apk"
        EDITION_LABEL="DEV EDITION WITH REGION BYPASS (com.agy.dev, port 45158, debuggable)"
    else
        OUT_APK="$DIST_DIR/${BASE_PREFIX}-dev.apk"
        EDITION_LABEL="DEV EDITION STANDARD (com.agy.dev, port 45158, debuggable, standard gates)"
    fi
else
    if [ "$NO_BYPASS" = "0" ]; then
        OUT_APK="$DIST_DIR/${BASE_PREFIX}-region-bypass.apk"
        EDITION_LABEL="RELEASE EDITION WITH REGION BYPASS (com.agy, port 45157, with account eligibility bypass)"
    else
        OUT_APK="$DIST_DIR/${BASE_PREFIX}.apk"
        EDITION_LABEL="RELEASE EDITION STANDARD (com.agy, port 45157, original gates)"
    fi
fi

if [ "$UNSIGNED_KR_RELEASE_MODE" = "1" ]; then
    OUT_APK="${OUT_APK%.apk}-unsigned.apk"
    EDITION_LABEL="$EDITION_LABEL [UNSIGNED: OWNER SIGNING REQUIRED]"
fi


echo "============================================================"
echo "  ANTIGRAVITY V2 APK BUILD PIPELINE"
echo "  $EDITION_LABEL"
if [ "$FAST_BUILD" = "1" ]; then
    echo "  [MODE: FAST ITERATION (UNCOMPRESSED ASSETS & CACHE)]"
fi
echo "============================================================"

# Check host tools
for tool in python3 aapt javac java apksigner; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[ERROR] Missing required build tool: $tool" >&2
        exit 1
    fi
done

# Clean previous build artifacts
echo "[*] Cleaning build directories..."
rm -rf "$BUILD_DIR/gen" "$BUILD_DIR/res" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$BUILD_DIR/staging" \
       "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/res" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$BUILD_DIR/staging" "$DIST_DIR"

# Phase 01: Config Generation (creates BuildConfig.java, gen.xml, and build/AndroidManifest.xml)
echo -e "\n>>> [1/8] Generating Configs..."
python3 "$SCRIPT_DIR/01_gen_config.py" "$BUILD_DIR"

TARGET_MANIFEST="$BUILD_DIR/AndroidManifest.xml"

# Phase 02: Generate R.java
echo -e "\n>>> [2/8] Generating R.java..."
bash "$SCRIPT_DIR/02_r_java.sh" "$BUILD_DIR/gen" "$TARGET_MANIFEST" "$APP_DIR/src/main/res" "$TOOLS_DIR/android.jar" "$BUILD_DIR/res"

# Phase 03: Compile Java Sources
echo -e "\n>>> [3/8] Compiling Java Sources..."
bash "$SCRIPT_DIR/03_compile_java.sh" "$BUILD_DIR/obj" "$BUILD_DIR/gen" "$APP_DIR/src/main/java" "$TOOLS_DIR/android.jar"

# Phase 04: D8 Dexing
echo -e "\n>>> [4/8] Converting Bytecode to classes.dex (D8)..."
bash "$SCRIPT_DIR/04_dex.sh" "$BUILD_DIR/dex" "$BUILD_DIR/obj" "$TOOLS_DIR/r8.jar" "$TOOLS_DIR/android.jar" 24

# Phase 05: Stage Assets
echo -e "\n>>> [5/8] Staging Runtime and Web Assets..."
STAGE_FLAGS=()
if [ "$NO_BYPASS" = "1" ]; then
    STAGE_FLAGS+=(--no-bypass)
else
    STAGE_FLAGS+=(--region-bypass)
fi
python3 "$SCRIPT_DIR/05_stage_assets.py" "$BUILD_DIR/staging" "${STAGE_FLAGS[@]}"

# Phase 06: Package Unaligned APK
echo -e "\n>>> [6/8] Packaging Unaligned APK..."
bash "$SCRIPT_DIR/06_package.sh" "$BUILD_DIR/unaligned.apk" "$TARGET_MANIFEST" "$APP_DIR/src/main/res" "$TOOLS_DIR/android.jar" "$BUILD_DIR/staging/assets" "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/res"

# Phase 07: ZipAlign
echo -e "\n>>> [7/8] ZipAligning APK..."
python3 "$SCRIPT_DIR/align.py" "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk" 4

# Phase 08: Sign and Verify APK
echo -e "\n>>> [8/8] Signing and Verifying APK..."
if [ "$UNSIGNED_KR_RELEASE_MODE" = "1" ]; then
    cp "$BUILD_DIR/aligned.apk" "$OUT_APK"
    echo "[*] Unsigned aligned KR APK staged for interactive owner signing: $OUT_APK"
elif [ "$KR_RELEASE_MODE" = "1" ]; then
    : "${AGY_RELEASE_KEYSTORE_PATH:?Private release keystore required}"
    : "${AGY_RELEASE_KEY_ALIAS:?Permanent release key alias required}"
    : "${AGY_RELEASE_STORE_PASSWORD_FILE:?Private store password file required}"
    : "${AGY_RELEASE_KEY_PASSWORD_FILE:?Private key password file required}"
    for material in "$AGY_RELEASE_KEYSTORE_PATH" "$AGY_RELEASE_STORE_PASSWORD_FILE" "$AGY_RELEASE_KEY_PASSWORD_FILE"; do
        if [ ! -s "$material" ]; then echo "[ERROR] Signing material missing" >&2; exit 1; fi
    done
    if [ "$AGY_RELEASE_KEYSTORE_PATH" = "$TOOLS_DIR/debug.keystore" ]; then
        echo "[ERROR] Public debug key forbidden" >&2; exit 1
    fi
    echo "[*] KR release signing using private permanent keystore..."
    apksigner sign --ks "$AGY_RELEASE_KEYSTORE_PATH" --ks-key-alias "$AGY_RELEASE_KEY_ALIAS" --ks-pass "file:$AGY_RELEASE_STORE_PASSWORD_FILE" --key-pass "file:$AGY_RELEASE_KEY_PASSWORD_FILE" --out "$OUT_APK" "$BUILD_DIR/aligned.apk"
else
    bash "$SCRIPT_DIR/08_sign.sh" "$BUILD_DIR/aligned.apk" "$OUT_APK" "$TOOLS_DIR/debug.keystore"
fi
if [ "$UNSIGNED_KR_RELEASE_MODE" != "1" ]; then
    bash "$SCRIPT_DIR/09_verify.sh" "$OUT_APK"
else
    aapt dump badging "$OUT_APK" | head -n 1
    echo "[!] UNSIGNED APK: not installable until signed with permanent KR release key"
fi

echo "============================================================"
echo "  BUILD SUCCESSFUL! APK CREATED:"
echo "  $OUT_APK ($(stat -c%s "$OUT_APK") bytes)"
echo "============================================================"
