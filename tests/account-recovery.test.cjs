const {test}=require('node:test');
const assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(overrides={}) {
 const source=fs.readFileSync('admin/index.html','utf8').replace(/^---[\s\S]*?---/,'');
 const dom=new JSDOM('<main class="admin-page"><div class="container">'+source+'</div></main>',{url:'https://example.invalid/admin/',runScripts:'outside-only'}),w=dom.window;
 const users=[{uid:'leader',name:'Leader',role:'leader',email:'lead@example.invalid'}];
 w.BibleAuth={onReady(fn){fn({uid:'admin'},{role:'admin'});}};
 w.BibleDB={getAllUsers:async()=>users,getAllAnswers:async()=>({}),getClasses:async()=>[{id:'one',name:'Class',leaderUid:'leader',memberUids:['orphan'],weekday:0,time:'10:00',lessonSlugs:['a']}],getCurrentUser:()=>({uid:'admin'}),...overrides};
 w.eval(fs.readFileSync('assets/js/admin.js','utf8'));return dom;
}
test('missing member opens recovery with original UID and no destructive delete control',async()=>{
 let restored,emails=[];const dom=setup({restoreUserProfile:async(uid,data)=>{restored={uid,...data};},resetPassword:async email=>emails.push(email)}),w=dom.window,d=w.document;await tick();
 assert.equal(d.querySelectorAll('.delete-user').length,0);
 d.querySelector('.leader-tree button').click();assert.equal(d.getElementById('recoveryUid').value,'orphan');
 d.getElementById('recoveryEmail').value='student@example.invalid';d.getElementById('recoveryName').value='Анна';d.getElementById('restoreProfileBtn').click();await tick();
 assert.equal(restored.uid,'orphan');assert.equal(restored.name,'Анна');assert.equal(emails.length,0);
 d.getElementById('recoveryForm').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await tick();assert.deepEqual(emails,['student@example.invalid']);dom.window.close();
});
test('profile save failure retries same account instead of creating a duplicate',async()=>{
 let creates=0,saves=0;const dom=setup({createUser:async()=>{creates++;return {user:{uid:'created'}};},setUserProfile:async()=>{saves++;if(saves===1) throw new Error('offline');}}),w=dom.window,d=w.document;await tick();
 d.getElementById('addUserBtn').click();d.getElementById('uName').value='Анна';d.getElementById('uEmail').value='student@example.invalid';d.getElementById('uPassword').value='test-password';
 const submit=()=>d.getElementById('userForm').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));submit();await tick();assert.match(d.getElementById('userFormError').textContent,/Аккаунт входа создан/);submit();await tick();assert.equal(creates,1);assert.equal(saves,2);dom.window.close();
});
