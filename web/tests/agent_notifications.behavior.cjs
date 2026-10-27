const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const calls = [];
const win = { __AG_NATIVE_BRIDGE_TOKEN__: "unit-test-token", Android: {onAgentState: (...args) => calls.push(args)} };
vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), {window: win});
const notify = win.__AGY_NOTIFY_AGENT_STATE__;
notify({conversationId: 'c123', status: 2, fullyIdle: false,
        trajectorySlice: { stepsInSlice: [{step: {metadata: {toolSummary: 'Running tests'}}}] }});
notify({conversationId: 'c123', status: 1, fullyIdle: false});
notify({conversationId: 'c123', status: 1, fullyIdle: true});
notify({conversationId: 'c123', status: 3, fullyIdle: false});
notify({conversationId: null, status: 2, fullyIdle: false});
assert.equal(calls.length, 3);
assert.equal(calls[0][1], 'c123');
assert.equal(calls[0][2], 2);
assert.equal(calls[0][4], 'Agent is working');
assert.equal(calls[1][3], false);
assert.equal(calls[2][2], 1);
assert.equal(calls[2][3], true);
win.Android.onAgentState = () => {throw new Error('android gone')};
assert.doesNotThrow(() => notify({conversationId: 'c123', status: 2}));
console.log('PASS: browser sends decoded agent state, never crashes the stream');

const titles=[];win.Android.onConversationSummary=(...args)=>titles.push(args);
win.__AGY_NOTIFY_SUMMARIES__([['abc',{summary:'My task'}],['empty',{}]]);
assert.deepEqual(titles,[['unit-test-token','abc','My task']]);
assert.ok(!JSON.stringify(calls).includes('Running tests'));
