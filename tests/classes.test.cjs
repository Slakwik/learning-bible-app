const {test}=require('node:test');
const assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const catalog=[{slug:'ephesians-2',title:'Ефесянам 2',course:'ephesians',reference:'Еф. 1',url:'/lessons/ephesians-2/'},{slug:'james-1',title:'Иакова 1',course:'james',reference:'Иак. 1',url:'/lessons/james-1/'}];
async function setup(role,initial=[]) {
 let html=fs.readFileSync('classes/index.html','utf8').replace(/^---[\s\S]*?---/,'').replace(/(<script id="classLessonCatalog"[^>]*>)[\s\S]*?(<\/script>)/,'$1'+JSON.stringify(catalog)+'$2');
 const dom=new JSDOM(html,{url:'https://example.invalid/classes/',runScripts:'outside-only'}),w=dom.window;
 w.HTMLElement.prototype.scrollIntoView=function(){};
 let saves=[],directoryReads=0;
 w.BibleAuth={onReady(fn){fn({uid:'leader-a'},{role,name:'Ведущий'});}};
 w.BibleDB={getClasses(){return Promise.resolve(initial);},getStudents(){directoryReads++;return Promise.resolve([{id:'student-a',name:'Ученик',email:'student@example.invalid'}]);},getAllUsers(){return Promise.resolve([{uid:'leader-a',name:'Ведущий',role:'leader'}]);},getClassAnswers(){return Promise.resolve([]);},saveClass(id,data){saves.push({id,data});return Promise.resolve(id||'new-class');}};
 w.eval(fs.readFileSync('assets/js/classes.js','utf8'));await tick();
 return {w,dom,document:w.document,saves,reads:()=>directoryReads};
}
test('student sees no class creation or student directory',async()=>{
 const e=await setup('user');assert.equal(e.document.getElementById('newClass').hidden,true);assert.equal(e.reads(),0);
 assert.match(e.document.getElementById('classesStatus').textContent,/пока не добавлены/);e.dom.window.close();
});
test('leader builds ordered curriculum and adds registered students',async()=>{
 const e=await setup('leader'),d=e.document;d.getElementById('newClass').click();await tick();
 d.getElementById('className').value='Воскресный класс';d.getElementById('classStart').value='2026-09-06';
 const choices=d.querySelectorAll('#lessonPicker input');choices[0].click();choices[1].click();
 d.querySelectorAll('#selectedLessons li')[1].querySelector('button').click();
 d.querySelector('#studentPicker input').click();
 d.getElementById('classForm').dispatchEvent(new e.w.Event('submit',{cancelable:true}));await tick();
 assert.equal(e.saves.length,1);assert.equal(e.saves[0].data.leaderUid,'leader-a');
 assert.deepEqual(Array.from(e.saves[0].data.lessonSlugs),['james-1','ephesians-2']);
 assert.deepEqual(Array.from(e.saves[0].data.memberUids),['student-a']);assert.equal(e.saves[0].data.timezone,'Europe/Moscow');
 e.dom.window.close();
});
test('empty curriculum and inconsistent weekday cannot save',async()=>{
 const e=await setup('leader'),d=e.document;d.getElementById('newClass').click();await tick();
 const submit=()=>d.getElementById('classForm').dispatchEvent(new e.w.Event('submit',{cancelable:true}));
 submit();assert.equal(e.saves.length,0);assert.match(d.getElementById('classFormError').textContent,/хотя бы один урок/);
 d.querySelector('#lessonPicker input').click();d.getElementById('className').value='Класс';d.getElementById('classStart').value='2026-09-07';submit();
 assert.equal(e.saves.length,0);assert.match(d.getElementById('classFormError').textContent,/День первой встречи/);e.dom.window.close();
});
test('class links preserve answer scope and archived class remains visible',async()=>{
 const c={id:'class-a',name:'Архивный класс',leaderUid:'leader-a',leaderName:'Ведущий',weekday:0,time:'11:00',duration:60,startDate:'2026-09-06',lessonSlugs:['ephesians-2'],memberUids:['student-a'],archived:true};
 const e=await setup('user',[c]);e.document.querySelector('#classesList button').click();
 const a=e.document.querySelector('#classDetail ol a');assert.equal(a.getAttribute('href'),'/lessons/ephesians-2/?class=class-a');
 assert.match(e.document.getElementById('classDetail').textContent,/Класс завершён/);assert.equal(e.reads(),0);e.dom.window.close();
});
