#!/usr/bin/env bash
#
# 05-web-bus.sh - build the web bundles, then stage web/ and bus/ as assets.
#
# web/build.sh (owned by the web layer) generates web/generated/**. We run it
# first so the staged tree matches the freshly generated sources, then enforce
# the byte-for-byte invariant. Build-only files (web/src/**, web/build.sh,
# web/src.manifest) are pruned from the APK, not shipped.

log "[5/9] assets/web + bus"
bash "$WEB_DIR/build.sh" || die "web/build.sh failed"
cp -a "$WEB_DIR" "$STAGE_ASSETS/web"
# prune build-only inputs from the shipped assets
rm -rf "$STAGE_ASSETS/web/src" "$STAGE_ASSETS/web/build.sh" "$STAGE_ASSETS/web/patch_main.py" \
    "$STAGE_ASSETS/web/patch_agent_notifications.py" "$STAGE_ASSETS/web/patch_agent_diag.py" \
    "$STAGE_ASSETS/web/patch_stream_resume.py" "$STAGE_ASSETS/web/patch_sidebar_reconcile.py" \
    "$STAGE_ASSETS/web/patch_notification_navigation.py" \
    "$STAGE_ASSETS/web/tests" "$STAGE_ASSETS/web/src.manifest"
if [ -d "$BUS_DIR" ]; then
    cp -a "$BUS_DIR" "$STAGE_ASSETS/bus"
    log "  bus/ copied"
else
    log "  bus/ absent, skipped"
fi
# byte-for-byte invariant: assets/web == web/ (build-only files excluded)
if ! diff -r --exclude=src --exclude=build.sh --exclude=patch_main.py \
        --exclude=patch_agent_notifications.py --exclude=patch_agent_diag.py \
        --exclude=patch_stream_resume.py --exclude=patch_sidebar_reconcile.py \
        --exclude=patch_notification_navigation.py \
        --exclude=tests --exclude=src.manifest \
        "$WEB_DIR" "$STAGE_ASSETS/web" >/dev/null 2>&1; then
    die "invariant failed: assets/web != web/ (excluding build-only files)"
fi
log "  invariant ok: assets/web == web/ (build-only files pruned)"

# Diagnostic build combines stream-resume patch, notifications, and summary diagnostics.
python3 "$WEB_DIR/patch_stream_resume.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "WebView stream resume patch could not be applied"
python3 "$WEB_DIR/patch_agent_diag.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "diagnostic summary hook could not be applied"
python3 "$WEB_DIR/patch_agent_notifications.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "native agent notification hook could not be applied"
python3 "$WEB_DIR/patch_notification_navigation.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "notification deep-link hook could not be applied"
python3 "$WEB_DIR/patch_sidebar_reconcile.py" \
    "$STAGE_ASSETS/web/main.js" "$STAGE_ASSETS/web/main.js" \
    || die "sidebar status reconciliation hook could not be applied"
if command -v node >/dev/null 2>&1; then
    node --check "$STAGE_ASSETS/web/main.js" || die "notification hook JS is invalid"
fi
