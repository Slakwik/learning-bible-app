const {test}=require('node:test');
const assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
test('admin tree groups several classes and students under each leader',async()=>{
 const source=fs.readFileSync('admin/index.html','utf8').replace(/^---[\s\S]*?---/,'');
 const dom=new JSDOM('<main class="admin-page"><div class="container">'+source+'</div></main>',{url:'https://example.invalid/admin/',runScripts:'outside-only'}),w=dom.window;
 const users=[{uid:'leader',name:'Ведущий',role:'leader',email:'leader@example.invalid'},{uid:'student-a',name:'Анна',role:'user',email:'a@example.invalid'},{uid:'student-b',name:'Борис',role:'user',email:'b@example.invalid'}];
 const classes=[{id:'one',name:'Утро',leaderUid:'leader',memberUids:['student-a'],weekday:0,time:'10:00',lessonSlugs:['a']},{id:'two',name:'Вечер',leaderUid:'leader',memberUids:['student-b'],weekday:0,time:'18:00',lessonSlugs:['b']}];
 w.BibleAuth={onReady(fn){fn({uid:'admin'},{role:'admin'});}};
 w.BibleDB={getAllUsers(){return Promise.resolve(users);},getAllAnswers(){return Promise.resolve({});},getClasses(){return Promise.resolve(classes);},getCurrentUser(){return {uid:'admin'};}};
 w.eval(fs.readFileSync('assets/js/admin.js','utf8'));await tick();
 const tree=w.document.querySelector('.leader-tree');assert.ok(tree);assert.equal(tree.children.length,1);
 assert.match(tree.textContent,/Ведущий · классов: 2/);assert.match(tree.textContent,/Утро/);assert.match(tree.textContent,/Вечер/);assert.match(tree.textContent,/Анна/);assert.match(tree.textContent,/Борис/);
 assert.deepEqual(Array.from(tree.querySelectorAll('a')).map(a=>a.getAttribute('href')),['/classes/?id=two','/classes/?id=one']);
 dom.window.close();
});
