const fs=require('fs'),vm=require('vm'),assert=require('assert');
const helper=fs.readFileSync('web/src/mobile/composer.js','utf8').split('/* Compact mobile composer')[0];
let now=0,next=0,cursor=0,stops=0;
const timers=new Map(),cells=[];
let previousEffect=null,cleanup=null;
const A={
 useState(init){const i=cursor++;if(!cells[i])cells[i]={value:init};return[cells[i].value,x=>{cells[i].value=x}]},
 useRef(init){const i=cursor++;return cells[i]||(cells[i]={current:init})},
 useEffect(fn,deps){if(previousEffect===null||previousEffect!==deps[0]){cleanup?.();previousEffect=deps[0];cleanup=fn()}},
 createElement:(type,props,...children)=>({type,props,children})
};
const box={A,window:{},performance:{now:()=>now},
 setTimeout(fn,delay){const id=++next;timers.set(id,{fn,at:now+delay});return id},
 clearTimeout(id){timers.delete(id)}};
vm.createContext(box);vm.runInContext(helper,box);
function render(active){cursor=0;return box.useAgyDictationClock(active,()=>stops++)}
function advance(value){now=value;for(const [id,t]of [...timers])if(t.at<=now){timers.delete(id);t.fn()}}
render(true);assert.equal(render(true),210);
advance(1000);assert.equal(render(true),209);
advance(209000);assert.equal(render(true),1);assert.equal(stops,0);
advance(210000);assert.equal(render(true),0);assert.equal(stops,1);
advance(400000);assert.equal(stops,1,'only one automatic stop');
render(false);assert.equal(timers.size,0);
render(true);assert.equal(render(true),210,'restart gets its own deadline');
advance(401000);assert.equal(render(true),209);
render(false);advance(800000);assert.equal(stops,1,'stopped timer cannot stop a later recording');
render(true);advance(1100000);assert.equal(stops,2,'delayed browser timer uses elapsed monotonic time');
render(false);
for(let i=0;i<10;i++){render(true);render(false)}
assert.equal(timers.size,0,'all repeated start/stop timers released');
render(true);cleanup?.();advance(1500000);assert.equal(stops,2,'unmount releases timer');
assert.equal(box.agyDictationRemaining(210000,0),210);
assert.equal(box.agyDictationRemaining(210000,209001),1);
assert.equal(box.agyDictationRemaining(210000,300000),0);
for(const [seconds,label]of [[210,'3:30 remaining'],[60,'1:00 remaining'],[1,'0:01 remaining'],[0,'0:00 remaining']]){
 const element=box.AgyDictationCountdown({seconds});
 assert.equal(element.children[0],label);assert.equal(element.props.role,'timer');
 assert.equal(element.props['aria-live'],'off','do not announce every second');
}
console.log('PASS dictation clock: 210s deadline, delayed timers, repeated stop/restart, unmount, countdown and accessibility');
