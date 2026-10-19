#!/usr/bin/env bash
#
# 05-web-bus.sh - build the web bundles, then stage web/ and bus/ as assets.
#
# web/build.sh (owned by the web layer) generates web/generated/**. We run it
# first so the staged tree matches the freshly generated sources, then enforce
# the byte-for-byte invariant. Build-only files are not shipped. Our resume
# patch is applied only to the staged UI bundle, leaving upstream main.js intact.

log "[5/9] assets/web + bus"
bash "$WEB_DIR/build.sh" || die "web/build.sh failed"
cp -a "$WEB_DIR" "$STAGE_ASSETS/web"
# prune build-only inputs from the shipped assets
rm -rf "$STAGE_ASSETS/web/src" "$STAGE_ASSETS/web/build.sh" \
    "$STAGE_ASSETS/web/patch_main.py" "$STAGE_ASSETS/web/patch_stream_resume.py" \
    "$STAGE_ASSETS/web/tests" "$STAGE_ASSETS/web/src.manifest"
if [ -d "$BUS_DIR" ]; then
    cp -a "$BUS_DIR" "$STAGE_ASSETS/bus"
    log "  bus/ copied"
else
    log "  bus/ absent, skipped"
fi
# First validate byte-for-byte equality against unmodified upstream web assets.
if ! diff -r --exclude=src --exclude=build.sh --exclude=patch_main.py \
        --exclude=patch_stream_resume.py --exclude=tests --exclude=src.manifest \
        "$WEB_DIR" "$STAGE_ASSETS/web" >/dev/null 2>&1; then
    die "invariant failed: assets/web != web/ (excluding build-only files)"
fi
log "  invariant ok: upstream web assets match"

# Change only the packaged view's observation stream. The running agent's work
# continues on the backend; foreground resume renews a stale UI subscription.
python3 "$WEB_DIR/patch_stream_resume.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "Android WebView stream resume patch failed"
if command -v node >/dev/null 2>&1; then
    node --check "$STAGE_ASSETS/web/main.js" \
        || die "patched main.js syntax check failed"
fi
log "  stream resume patch: applied only to packaged assets"
