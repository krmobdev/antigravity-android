'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function makeHarness(initialTop=700) {
  let time=1000, rafSeq=0, queue=new Map();
  const events={}, interval=[], logs=[];
  const node={
    scrollHeight:6000,clientHeight:800,isConnected:true,
    _top:initialTop,
    get scrollTop(){return this._top;},
    set scrollTop(value){ this._top=Math.max(0,Math.min(this.scrollHeight-this.clientHeight,value)); },
    addEventListener:(type,fn)=>{events[type]=fn;},
    removeEventListener:(type,fn)=>{if(events[type]===fn)delete events[type];}
  };
  const html={
    hidden:false,
    head:{appendChild(x){assert.match(x.textContent,/touch-action:none/);}},
    createElement(){return {textContent:''};},
    querySelector(){return node;},
    addEventListener(type,fn){events[type]=fn;}
  };
  const ctx={
    window:{},document:html,
    performance:{now:()=>time},
    requestAnimationFrame:(cb)=>{let id=++rafSeq;queue.set(id,cb);return id;},
    cancelAnimationFrame:(id)=>{queue.delete(id);},
    setInterval:(cb)=>{interval.push(cb);return interval.length;},
    console:{info(x){logs.push(x);}},Math
  };
  vm.runInNewContext(fs.readFileSync('web/scroll_no_native_fling_ab.js','utf8'),ctx);
  interval[0]();
  assert.equal(ctx.window.__AGY_FLING_AB_INSTALLED__,true);
  function start(y,x=200){events.touchstart({type:'touchstart',touches:[{clientY:y,clientX:x}]});}
  function move(y,dt=20,x=200){time+=dt;events.touchmove({type:'touchmove',touches:[{clientY:y,clientX:x}],cancelable:true,preventDefault(){}});}
  function end(dt=8,cancel=false){time+=dt;events[cancel?'touchcancel':'touchend']({type:cancel?'touchcancel':'touchend',touches:[]});}
  function tick(dt=16.67){
    time+=dt;
    const current=[...queue.values()];
    queue.clear();
    for(const fn of current)fn(time);
  }
  return {node,events,logs,start,move,end,tick,
    advance(ms){time+=ms;},pending:()=>queue.size};
}
{
  const h=makeHarness();
  h.start(500);h.move(420,20);h.move(325,20);h.end(5);
  assert.equal(h.node.scrollTop,875);
  assert.ok(h.pending()>0,'momentum started');
  let previous=h.node.scrollTop;
  h.tick();
  assert.ok(h.node.scrollTop>previous,'scroll continues smoothly after release');
  assert.ok(h.node.scrollTop-previous<80,'first-frame displacement bounded');
  let iterations=0;
  while(h.pending()>0 && iterations++<250)h.tick();
  assert.ok(iterations<250,'fling stops');
  assert.ok(h.node.scrollTop>875,'fling has useful travel');
  assert.ok(h.logs.some(x=>x.includes('FLING_START')));
  assert.ok(h.logs.some(x=>x.includes('FLING_END')));
}
{
  const h=makeHarness();
  h.start(460);h.move(380,20);h.move(260,20);h.end(5);
  h.tick();
  const stoppedAt=h.node.scrollTop;
  h.start(200);
  assert.equal(h.pending(),0,'new finger immediately cancels momentum');
  for(let k=0;k<10;k++)h.tick();
  assert.equal(h.node.scrollTop,stoppedAt,'no phantom scrolling after next touch');
}
{
  const h=makeHarness(1200);
  h.start(250);h.move(330,20);h.move(430,20);h.end(5);
  h.tick();
  assert.ok(h.node.scrollTop<1020,'opposite swipe moves toward start');
}
{
  const h=makeHarness();
  h.start(500);h.move(360,30);h.advance(160);h.end();
  assert.equal(h.pending(),0,'holding finger before release does not fling');
}
{
  const h=makeHarness();
  h.start(500);h.move(360,30);h.end(4,true);
  assert.equal(h.pending(),0,'touchcancel does not fling');
}
{
  const h=makeHarness(1200);
  h.start(500);h.move(400,14);h.move(250,14);h.end(4);
  const before=h.node.scrollTop;
  h.tick(1200);
  assert.ok(h.node.scrollTop-before<120,'one frame cannot jump 1200ms worth of motion');
}
{
  const h=makeHarness(5100);
  h.start(500);h.move(420,20);h.move(300,20);h.end(5);
  for(let k=0;k<250 && h.pending();k++)h.tick();
  assert.equal(h.node.scrollTop,5200,'momentum clamps exactly to end');
  assert.equal(h.pending(),0,'momentum stops at edge');
}
{
  const h=makeHarness();
  h.start(500);h.move(450,20,400);h.end(5);
  assert.equal(h.node.scrollTop,700,'horizontal gestures do not scroll chat');
  assert.equal(h.pending(),0);
}
console.log('PASS: momentum starts, damps, cancels, reverses, pauses and clamps without jumps');
