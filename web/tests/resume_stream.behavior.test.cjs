// Behavior check: the real minified stream consumer must re-subscribe after resume.
// This simulates a stream that never produces a response until its AbortSignal fires.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

async function main() {
  const s = fs.readFileSync(process.argv[2], 'utf8');
  const start = s.indexOf('async function sba(');
  const end = s.indexOf(';var pg=()=>', start);
  assert(start >= 0 && end > start, 'Cannot locate upstream stream consumer');
  const src = s.slice(start, end);
  const window = new EventTarget();
  let subscribers = 0;
  const errors = [];
  const context = {
    window, AbortController, setTimeout, console,
    d: x => x,
    Yf: id => ({conversationId: id, trajectorySlice: {stepsSlice: {}}}),
    ng: () => 'subscriber-' + (++subscribers),
    q: (schema, x) => x,
    Xf: {
      StreamAgentStateUpdatesRequestSchema: {},
      ClientTrajectoryVerbosity: {PROD_UI: 1}
    },
    og: () => false,
  };
  vm.createContext(context);
  vm.runInContext(src + ';this.agentStateStream = sba;', context);
  const lifetime = new AbortController();
  const calls = [];
  function fakeStream(req, options) {
    calls.push({req, signal: options.signal});
    return {
      async *[Symbol.asyncIterator]() {
        await new Promise((resolve, reject) => {
          if (options.signal.aborted) return reject(new Error('already aborted'));
          options.signal.addEventListener('abort', () =>
            reject(new Error('stream interrupted')), {once: true});
        });
      }
    };
  }
  const result = context.agentStateStream(
    fakeStream, 'conversation-123', () => {}, lifetime,
    {onError: error => errors.push(error)}
  );
  async function waitForCalls(n) {
    for (let i = 0; i < 100 && calls.length < n; i++) {
      await new Promise(r => setTimeout(r, 10));
    }
    assert.equal(calls.length, n, 'expected another subscription');
  }
  await waitForCalls(1);
  assert.equal(calls[0].req.conversationId, 'conversation-123');
  window.dispatchEvent(new Event('agy:host-resume'));
  await waitForCalls(2);
  assert.equal(calls[0].signal.aborted, true);
  assert.equal(calls[1].signal.aborted, false);
  assert.equal(lifetime.signal.aborted, false, 'must not cancel backend conversation');
  assert.deepEqual(errors, [], 'expected reconnection without reporting an error');
  lifetime.abort();
  await result;
  assert.equal(calls[1].signal.aborted, true);
  console.log('PASS: Android resume restarts observation stream; lifetime cancellation still works');
}
main().catch(error => {console.error(error); process.exitCode = 1});
