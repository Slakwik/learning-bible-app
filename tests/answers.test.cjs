const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(getAnswers) {
  let ready; const handlers={},writes=[];
  const ta={value:'',disabled:false,getAttribute(){return 'q1';},addEventListener(n,fn){handlers[n]=fn;}};
  const els={lessonContent:{style:{},getAttribute(){return 'lesson-1';},querySelectorAll(){return [ta];}},authGate:{style:{}},lessonActions:{style:{}},saveAnswers:{addEventListener(n,fn){handlers.save=fn;}},saveStatus:{style:{}}};
  const ctx={window:{location:{search:''}},URLSearchParams,document:{getElementById(id){return els[id];}},BibleAuth:{onReady(fn){ready=fn;}},BibleDB:{getAnswers,saveAnswers(uid,slug,data){writes.push({uid,slug,data});return Promise.resolve();}},setTimeout(){},clearTimeout(){}};
  vm.runInNewContext(fs.readFileSync('assets/js/app.js','utf8'),ctx);
  return {els,ta,handlers,writes,ready};
}
test('failed read cannot overwrite saved answers',async()=>{
  const env=setup(()=>Promise.reject(new Error('offline')));
  env.ready({uid:'u'});await tick();env.handlers.save();await tick();
  assert.equal(env.writes.length,0);assert.equal(env.ta.disabled,true);
  assert.match(env.els.saveStatus.textContent,/Не удалось загрузить/);
});
test('clearing last answer persists and rapid saves preserve order',async()=>{
  const env=setup(()=>Promise.resolve({q1:'old'}));env.ready({uid:'u'});await tick();
  assert.equal(env.ta.value,'old');
  env.ta.value='new';env.handlers.save();env.ta.value='';env.handlers.blur();await tick();
  assert.equal(env.writes.length,2);assert.equal(env.writes[0].data.q1,'new');assert.deepEqual(Object.keys(env.writes[1].data),[]);
});
