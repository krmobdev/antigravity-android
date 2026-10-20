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



# Antigravity 2.4.0: upstream minifier renamed its ConnectRPC locals. In
# addition to host-resume, renew a stream after 90 seconds with no incoming
# events (important for the foreground "Working" freeze).
V24_REPLACEMENTS = (
    (
        'let E=Je();q?.(E);try{let F=p(te.StreamAgentStateUpdatesRequestSchema',
        'let E=Je(),__agyRenew=!1;q?.(E);try{let F=p(te.StreamAgentStateUpdatesRequestSchema',
    ),
    (
        '),G=a(F,{signal:e.signal});for await(let H of g(G)){',
        '),' + MARKER +
        '__agyAbort=new AbortController,__agyLastEvent=Date.now(),'
        '__agyOnResume=()=>{__agyRenew=!0;__agyAbort.abort()},'
        '__agyWatchdog=setInterval(()=>{if(Date.now()-__agyLastEvent>9E4)'
        '{__agyRenew=!0;__agyAbort.abort()}},3E4);'
        'window.addEventListener("agy:host-resume",__agyOnResume);'
        'e.signal.addEventListener("abort",__agyOnResume,{once:!0});'
        'try{let G=a(F,{signal:__agyAbort.signal});'
        'for await(let H of g(G)){__agyLastEvent=Date.now();',
    ),
    (
        '}finally{g()}}h()}catch(F){h();if(e.signal.aborted)break;let G=!0;',
        '}finally{g()}}h()}finally{'
        'clearInterval(__agyWatchdog);'
        'window.removeEventListener("agy:host-resume",__agyOnResume);'
        'e.signal.removeEventListener("abort",__agyOnResume)'
        '}}catch(F){h();if(e.signal.aborted)break;'
        'if(__agyRenew){D=0;B=ue(b);continue}'
        'let G=!0;',
    ),
)


def patch_bundle(source: str) -> str:
    versions = (REPLACEMENTS, V24_REPLACEMENTS)
    if MARKER in source:
        if any(all(new in source for _, new in steps) for steps in versions):
            return source
        raise ValueError("partial / inconsistent stream resume patch")
    for steps in versions:
        if all(source.count(old) == 1 for old, _ in steps):
            for old, new in steps:
                source = source.replace(old, new, 1)
            return source
    details = [(source.count(steps[0][0]), source.count(steps[1][0]), source.count(steps[2][0])) for steps in versions]
    raise ValueError(f"upstream stream code changed: anchor counts {details}")


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
