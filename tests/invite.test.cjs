const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');
const tick=()=>new Promise(r=>setImmediate(r));
function setup(rejectCheck=false){
 const d=new JSDOM(fs.readFileSync('invite/index.html','utf8'),{url:'https://example.invalid/invite/?class=my-class&oobCode=test-only',runScripts:'outside-only'}),w=d.window,steps=[];
 const user={uid:'same-uid',email:'student@example.invalid',emailVerified:true,getIdToken:async()=>{},updatePassword:async()=>steps.push('password')};
 w.BibleDB={auth:{currentUser:null,isSignInWithEmailLink:()=>true,signInWithEmailLink:async()=>{steps.push('signin');w.BibleDB.auth.currentUser=user;}},checkInvitation:async()=>{steps.push('check');if(rejectCheck)throw new Error('Приглашение истекло');},acceptInvitation:async()=>{steps.push('accept');return{name:'Student'};},getUserProfile:async()=>{throw new Error('Temporary profile failure');}};
 w.eval(fs.readFileSync('assets/js/invite.js','utf8'));
 w.document.getElementById('inviteEmail').value=user.email;
 w.document.getElementById('invitePassword').value='my-test-password';w.document.getElementById('invitePasswordAgain').value='my-test-password';
 return {d,w,steps};
}
test('revoked or expired invitation is checked before password change',async()=>{
 const {w,steps}=setup(true);w.document.querySelector('form').dispatchEvent(new w.Event('submit',{cancelable:true}));await tick();
 assert.deepEqual(steps,['signin','check']);assert.match(w.document.getElementById('inviteStatus').textContent,/истекло/);assert.equal(w.document.getElementById('invitePassword').value,'');w.close();
});
test('acceptance strips email token and reports membership success despite profile failure',async()=>{
 const {w,steps}=setup();w.document.querySelector('form').dispatchEvent(new w.Event('submit',{cancelable:true}));await tick();
 assert.deepEqual(steps,['signin','check','password','accept']);assert.equal(w.location.search,'?class=my-class');assert.equal(w.document.querySelector('form').hidden,true);assert.equal(w.document.querySelector('a.btn').getAttribute('href'),'/classes/?id=my-class');w.close();
});
