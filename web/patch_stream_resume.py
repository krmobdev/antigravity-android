#!/usr/bin/env python3
"""Patch the *staged* Antigravity UI bundle to renew chat subscriptions on Android resume.

The upstream UI's consumeAgentStateStream() retries after transport failure, but
Android's background WebView suspension can leave a stream waiting indefinitely.
Only its per-conversation observation stream is aborted on agy:host-resume.
The backend agent run is NOT cancelled. The UI immediately re-subscribes and
rehydrates the conversation from the backend.

Keep web/main.js pristine for easy upstream merges. Fail closed on unexpected
upstream minification changes.
"""
from pathlib import Path
import sys

MARKER = "/* AGY_RESUME_STREAM_V1 */"
REPLACEMENTS = (
    (
        'let E=ng();p?.(E);try{let G=q(Xf.StreamAgentStateUpdatesRequestSchema',
        'let E=ng(),__agyResume=!1;p?.(E);try{let G=q(Xf.StreamAgentStateUpdatesRequestSchema',
    ),
    (
        '}),J=a(G,{signal:e.signal});for await(let F of g(J)){',
        '}),' + MARKER +
        '__agyAbort=new AbortController,__agyOnResume=()=>{__agyResume=!0;__agyAbort.abort()},'
        '__agyOnDispose=()=>__agyAbort.abort();'
        'window.addEventListener("agy:host-resume",__agyOnResume);'
        'e.signal.addEventListener("abort",__agyOnDispose,{once:!0});'
        'try{let J=a(G,{signal:__agyAbort.signal});for await(let F of g(J)){',
    ),
    (
        'finally{g()}}h()}catch(G){h();if(e.signal.aborted)break;let J=!0;',
        'finally{g()}}h()}finally{'
        'window.removeEventListener("agy:host-resume",__agyOnResume);'
        'e.signal.removeEventListener("abort",__agyOnDispose)'
        '}'
        '}catch(G){h();if(e.signal.aborted)break;'
        'if(__agyResume){D=0;B=Yf(b);continue}'
        'let J=!0;',
    ),
)


def patch_bundle(source: str) -> str:
    if MARKER in source:
        if all(new in source for _, new in REPLACEMENTS):
            return source
        raise ValueError("partial / inconsistent stream resume patch")
    for old, _ in REPLACEMENTS:
        count = source.count(old)
        if count != 1:
            raise ValueError(f"upstream stream code changed: anchor count {count}: {old[:100]}")
    for old, new in REPLACEMENTS:
        source = source.replace(old, new, 1)
    return source


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: patch_stream_resume.py <input main.js> <staged main.js>", file=sys.stderr)
        return 2
    source_path, dest_path = (Path(p) for p in argv[1:])
    source = source_path.read_text(encoding="utf-8")
    try:
        patched = patch_bundle(source)
    except ValueError as exc:
        print(f"[resume-patch] ERROR: {exc}", file=sys.stderr)
        return 1
    dest_path.write_text(patched, encoding="utf-8")
    print(f"[resume-patch] installed Android resume re-subscription ({len(patched)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
