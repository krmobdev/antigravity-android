#!/usr/bin/env python3
"""Wire decoded agent state to the native progress notification bridge.

Only patches *staged* web assets, leaving upstream web/main.js unchanged.
Fail closed if the upstream/minified stream implementation moves.
"""
from pathlib import Path
import sys

MARKER = "/*AGY_NATIVE_NOTIFY_V1*/"
ANCHOR = "B=qba(B,F.update),C=B.trajectorySlice?.stepsSlice??k"
REPLACEMENT = (
    "B=qba(B,F.update)," + MARKER +
    "window.__AGY_NOTIFY_AGENT_STATE__?.(B),C=B.trajectorySlice?.stepsSlice??k"
)


def patch_state(source: str) -> str:
    if MARKER in source:
        if source.count(REPLACEMENT) == 1:
            return source
        raise ValueError("inconsistent notification patch")
    if source.count(ANCHOR) != 1:
        raise ValueError("upstream StreamAgentStateUpdates anchor changed")
    return source.replace(ANCHOR, REPLACEMENT, 1)


SUMMARY_MARKER = "/*AGY_NOTIFICATION_TITLES_V1*/"
SUMMARY_ANCHOR = 'applyStreamBatch(a,b){a.hasLoadedInitialSummaries=!0;'
SUMMARY_REPLACEMENT = SUMMARY_ANCHOR + SUMMARY_MARKER + 'window.__AGY_NOTIFY_SUMMARIES__?.(b.payload.updates);'

def patch(source: str) -> str:
    source = patch_state(source)
    if SUMMARY_MARKER in source:
        if source.count(SUMMARY_REPLACEMENT) == 1: return source
        raise ValueError("inconsistent notification title patch")
    if source.count(SUMMARY_ANCHOR) != 1:
        raise ValueError("upstream summary anchor changed")
    return source.replace(SUMMARY_ANCHOR, SUMMARY_REPLACEMENT, 1)

def main(args):
    if len(args) != 3:
        print("usage: patch_agent_notifications.py <original> <staged>", file=sys.stderr)
        return 2
    try:
        src = Path(args[1]).read_text(encoding="utf-8")
        patched = patch(src)
    except ValueError as exc:
        print("ERROR: " + str(exc), file=sys.stderr)
        return 1
    Path(args[2]).write_text(patched, encoding="utf-8")
    print("Patched native notifications state hook")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
