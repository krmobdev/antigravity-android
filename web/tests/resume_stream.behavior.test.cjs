// Behavior check for the real upstream 2.4 minified stream consumer.
// Simulate an idle ConnectRPC stream that only unblocks on AbortSignal.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

async function main() {
  const s = fs.readFileSync(process.argv[2], 'utf8');
  const start = s.indexOf('async function Hca(');
  const end = s.indexOf(';var Ke=class', start);
  assert(start >= 0 && end > start, 'Cannot locate v2.4 stream consumer');
  const src = s.slice(start, end);
  const window = new EventTarget();
  let subscribers = 0;
  let now = Date.now();
  const intervals = new Set();
  const errors = [];
  const FakeDate = class extends Date { static now() { return now; } };
  const context = {
    window, AbortController, setTimeout, console, Date: FakeDate,
    setInterval: fn => { intervals.add(fn); return fn; },
    clearInterval: fn => intervals.delete(fn),
    d: x => x,
    ue: id => ({conversationId: id, trajectorySlice: {stepsSlice: {}}}),
    Je: () => 'subscriber-' + (++subscribers),
    p: (schema, x) => x,
    te: {
      StreamAgentStateUpdatesRequestSchema: {},
      ClientTrajectoryVerbosity: {PROD_UI: 1}
    },
    Gca: () => false,
  };
  vm.createContext(context);
  vm.runInContext(src + ';this.agentStateStream = Hca;', context);
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
    for (let i = 0; i < 150 && calls.length < n; i++) {
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
  assert.equal(lifetime.signal.aborted, false, 'must not cancel backend agent');
  // Foreground watchdog: if the UI receives no events for 90s, renew stream.
  now += 91000;
  for (const tick of Array.from(intervals)) tick();
  await waitForCalls(3);
  assert.equal(calls[1].signal.aborted, true);
  assert.equal(calls[2].signal.aborted, false);
  assert.deepEqual(errors, [], 'reconnections should not raise visible errors');
  lifetime.abort();
  await result;
  assert.equal(calls[2].signal.aborted, true);
  assert.equal(intervals.size, 0, 'watchdog must be cleaned up');
  console.log('PASS: resume and foreground inactivity renew UI subscription without stopping agent');
}
main().catch(error => {console.error(error); process.exitCode = 1});
