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


# A suspended summary feed also needs renewal. Keep the last snapshot until
# the new stream sends its initial snapshot; ignore callbacks from old streams.
SUMMARY_ANCHOR = 'function Vya(){var a=null,b=!1;return c=>e=>f=>{if(Uya.match(f)){let g=f.payload.subscribe,h=f.payload.analyticsService;a?.abort();a=new AbortController;let k=new Ys(h,"trajectory_summary_load");Ok({label:"[TrajectorySummaries]",abort:a,createStream:l=>g({signal:l}),onMessage:l=>{Xs(k);b&&(c.dispatch(cma()),b=!1);c.dispatch(Fo({updates:Object.entries(l.updates),deletes:l.deletes}))},onDisconnect:()=>{b=!0}})}return e(f)}}'
SUMMARY_REPLACEMENT = 'function Vya(){/* AGY_RESUME_SUMMARIES_V1 */var a=null,b=!1,__agyLast;return c=>{var __agyRestart=()=>{if(__agyLast){b=!0;c.dispatch(__agyLast)}};window.addEventListener("agy:host-resume",__agyRestart);window.addEventListener("agy:agent-proof-changed",e=>{if(e.detail?.fullyIdle===true)__agyRestart()});return e=>f=>{if(Uya.match(f)){__agyLast=f;let g=f.payload.subscribe,h=f.payload.analyticsService;a?.abort();a=new AbortController;let __agyCurrent=a;let k=new Ys(h,"trajectory_summary_load");Ok({label:"[TrajectorySummaries]",abort:a,createStream:l=>g({signal:l}),onMessage:l=>{if(a!==__agyCurrent||__agyCurrent.signal.aborted)return;Xs(k);b&&(c.dispatch(cma()),b=!1);c.dispatch(Fo({updates:Object.entries(l.updates),deletes:l.deletes}))},onDisconnect:()=>{if(a===__agyCurrent)b=!0}})}return e(f)}}}'
REPLACEMENTS += ((SUMMARY_ANCHOR, SUMMARY_REPLACEMENT),)


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
