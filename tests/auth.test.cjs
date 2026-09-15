const {test}=require('node:test');
const assert=require('node:assert/strict');
const {JSDOM}=require('jsdom');
const fs=require('node:fs');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(getProfile,path='/profile/') {
 const dom=new JSDOM(fs.readFileSync('_includes/header.html','utf8')+'<main></main>',{url:'https://example.invalid'+path,runScripts:'outside-only'}),w=dom.window;
 const user={uid:'user-1',email:'test@example.invalid'};
 let active=user,listener,writes=0;
 w.BibleDB={onAuthChanged(fn){listener=fn;},getCurrentUser(){return active?{...active}:null;},getUserProfile:getProfile,setUserProfile(){writes++;return Promise.resolve();},logout(){return Promise.resolve();}};
 w.eval(fs.readFileSync('assets/js/auth.js','utf8'));
 return {dom,w,user,auth:w.BibleAuth,resolve(u=user){active=u;listener(u);},writes:()=>writes};
}
test('auth delivers once and replays to late subscribers using UID identity',async()=>{
 const e=setup(()=>Promise.resolve({name:'Test',role:'user'}));let count=0;
 e.auth.onReady(()=>count++);e.resolve();await tick();assert.equal(count,1);
 let late=0;e.auth.onReady((u,p)=>{late++;assert.equal(p.uid,u.uid);});assert.equal(late,1);e.dom.window.close();
});
test('missing profile is persisted before ready',async()=>{
 const e=setup(()=>Promise.resolve(null));let ready=false;
 e.auth.onReady(()=>{assert.equal(e.writes(),1);ready=true;});e.resolve();await tick();assert.ok(ready);e.dom.window.close();
});
test('profile failure retains account link and exposes retry without successful auth',async()=>{
 const e=setup(()=>Promise.reject(new Error('permission-denied')));let ready=false,failed=false;
 e.auth.onReady(()=>ready=true);e.w.addEventListener('bible-auth-error',()=>failed=true);
 e.resolve();await tick();assert.equal(ready,false);assert.equal(failed,true);
 assert.equal(e.w.document.querySelector('#authNav .profile-link').getAttribute('href'),'/profile/');
 assert.match(e.w.document.getElementById('authError').textContent,/Не удалось загрузить профиль/);
 assert.ok(e.w.document.querySelector('#authError button'));e.dom.window.close();
});
test('profile button is present on every route while Firestore is still pending',async()=>{
 for(const path of ['/profile/','/classes/','/lessons/','/my-answers/','/admin/']) {
  const e=setup(()=>new Promise(()=>{}),path);e.resolve();await tick();
  assert.equal(e.w.document.querySelector('#authNav .profile-link').getAttribute('href'),'/profile/');
  assert.ok(e.w.document.getElementById('logoutBtn'));e.dom.window.close();
 }
});
test('signout cannot be undone by a late profile response',async()=>{
 let finish;const e=setup(()=>new Promise(r=>finish=r));e.resolve();e.resolve(null);finish({name:'Old',role:'admin'});await tick();
 assert.equal(e.w.document.querySelector('#authNav a').getAttribute('href'),'/login/');assert.equal(e.auth.getProfile(),null);e.dom.window.close();
});
test('mobile menu exposes expanded state and closes on Escape',async()=>{
 const e=setup(()=>Promise.resolve({name:'Test',role:'user'}));await tick();
 const toggle=e.w.document.getElementById('navToggle');toggle.click();assert.equal(toggle.getAttribute('aria-expanded'),'true');
 e.w.document.dispatchEvent(new e.w.KeyboardEvent('keydown',{key:'Escape'}));assert.equal(toggle.getAttribute('aria-expanded'),'false');e.dom.window.close();
});
