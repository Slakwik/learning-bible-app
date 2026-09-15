const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const firebase=require('firebase/compat/app');require('firebase/compat/auth');require('firebase/compat/firestore');
const {initializeTestEnvironment}=require('@firebase/rules-unit-testing');
const {doc,setDoc}=require('firebase/firestore');
test('email invitation uses real Auth emulator, sets own password and retains UID on login',async()=>{
 const env=await initializeTestEnvironment({projectId:'demo-bible-classes',firestore:{rules:fs.readFileSync('firestore.rules','utf8')}});
 const window={location:{origin:'https://example.invalid'}};
 const source=fs.readFileSync('assets/js/firebase-app.js','utf8').replaceAll('bible-learning-b4b4d','demo-bible-classes');
 new Function('firebase','window',source)(firebase,window);
 const api=window.BibleDB;api.auth.useEmulator('http://127.0.0.1:9099',{disableWarnings:true});api.db.useEmulator('127.0.0.1',8080);
 try {
  const email='invite-'+Date.now()+'@example.invalid';
  const leaderEmail='lead-'+Date.now()+'@example.invalid';
  const leader=(await api.auth.createUserWithEmailAndPassword(leaderEmail,'initial-test-password')).user;
  await env.withSecurityRulesDisabled(async c=>{
   await setDoc(doc(c.firestore(),'users',leader.uid),{name:'Leader',role:'leader'});
   await setDoc(doc(c.firestore(),'classes','integration'),{name:'Integration',leaderUid:leader.uid,leaderName:'Leader',weekday:0,time:'11:00',duration:60,startDate:'2026-09-06',timezone:'Europe/Moscow',place:'',description:'',lessonSlugs:['ephesians-1'],memberUids:[],archived:false,createdAt:new Date(),updatedAt:new Date()});
  });
  await api.sendInvitation('integration',email,'Ученик');
  const messages=await (await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-bible-classes/oobCodes')).json();
  const invitation=messages.oobCodes.find(x=>x.email===email);assert.ok(invitation);
  await api.auth.signOut();
  const user=(await api.auth.signInWithEmailLink(email,invitation.oobLink)).user;
  assert.equal(user.emailVerified,true);await api.checkInvitation('integration');await user.updatePassword('my-own-new-password');await user.getIdToken(true);
  await api.acceptInvitation('integration');
  const c=await api.getClass('integration');assert.deepEqual(c.memberUids,[user.uid]);
  await api.auth.signOut();await api.auth.signInWithEmailAndPassword(email,'my-own-new-password');assert.equal(api.auth.currentUser.uid,user.uid);
  await assert.rejects(api.checkInvitation('integration'),/уже использовано/);
  await api.auth.signInWithEmailAndPassword(leaderEmail,'initial-test-password');
  // An editor opened before acceptance must not remove a newly joined student.
  await api.saveClass('integration',{description:'Updated while student joined',memberUids:[]},[]);
  assert.deepEqual((await api.getClass('integration')).memberUids,[user.uid]);
  // Existing account accepts another email link with its original UID.
  await api.sendInvitation('integration',email,'Ученик');
  const again=await (await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-bible-classes/oobCodes')).json();
  const latest=again.oobCodes.filter(x=>x.email===email).at(-1);
  await api.auth.signOut();
  const existing=(await api.auth.signInWithEmailLink(email,latest.oobLink)).user;
  assert.equal(existing.uid,user.uid);
  await api.checkInvitation('integration');await existing.updatePassword('second-own-password');await api.acceptInvitation('integration');
  assert.deepEqual((await api.getClass('integration')).memberUids,[user.uid]);
 } finally {await api.auth.signOut();await api.db.terminate();await firebase.app().delete();await env.cleanup();}
});
