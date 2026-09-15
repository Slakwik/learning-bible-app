const assert=require('node:assert/strict');
const {test,before,after,beforeEach}=require('node:test');
const fs=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,deleteDoc,collection,query,where,getDocs,serverTimestamp,Timestamp,writeBatch}=require('firebase/firestore');
let env;
const db=uid=>env.authenticatedContext(uid).firestore();
const makeClass=(leaderUid='leader-a',memberUids=['student-a'])=>({name:'Воскресный класс',leaderUid,leaderName:'Ведущий',weekday:0,time:'11:00',duration:60,startDate:'2026-09-06',timezone:'Europe/Moscow',place:'Зал',description:'',lessonSlugs:['ephesians-2'],memberUids,archived:false,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
const answer=(uid='student-a',classId='class-a')=>({_class:classId,_uid:uid,_lesson:'ephesians-2',q1:'Ответ',_savedAt:'2026-09-05T00:00:00Z'});
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-bible-classes',firestore:{rules:fs.readFileSync('firestore.rules','utf8')}});});
after(async()=>{await env.cleanup();});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async ctx=>{
  const d=ctx.firestore();
  for(const [uid,role] of [['admin','admin'],['leader-a','leader'],['leader-b','leader'],['student-a','user'],['student-b','user']]) await setDoc(doc(d,'users',uid),{name:uid,email:uid+'@example.invalid',role});
  await setDoc(doc(d,'classes','class-a'),makeClass());
  await setDoc(doc(d,'classes','class-b'),makeClass('leader-b',['student-b']));
  await setDoc(doc(d,'classAnswers','class-a_student-a_ephesians-2'),answer());
  await setDoc(doc(d,'answers','student-a_ephesians-2'),{_uid:'student-a',_lesson:'ephesians-2',q1:'Личный ответ'});
 });
});
test('admin appoints a leader; student and leader cannot assign roles',async()=>{
 await assertSucceeds(updateDoc(doc(db('admin'),'users','student-b'),{role:'leader'}));
 await assertFails(updateDoc(doc(db('student-a'),'users','student-a'),{role:'leader'}));
 await assertFails(updateDoc(doc(db('leader-a'),'users','student-a'),{role:'leader'}));
 await assertFails(setDoc(doc(db('new-user'),'users','new-user'),{role:'leader'}));
});
test('leader can create own class and admin can assign leader',async()=>{
 await assertSucceeds(setDoc(doc(db('leader-a'),'classes','new-a'),makeClass()));
 await assertSucceeds(setDoc(doc(db('admin'),'classes','new-b'),makeClass('leader-b')));
});
test('students cannot create classes or change membership',async()=>{
 await assertFails(setDoc(doc(db('student-a'),'classes','new'),makeClass('student-a')));
 await assertFails(updateDoc(doc(db('student-b'),'classes','class-a'),{memberUids:['student-a','student-b'],updatedAt:serverTimestamp()}));
});
test('leader cannot create for another leader or modify another class',async()=>{
 await assertFails(setDoc(doc(db('leader-a'),'classes','new'),makeClass('leader-b')));
 await assertFails(updateDoc(doc(db('leader-b'),'classes','class-a'),{name:'Чужой класс',updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('leader-a'),'classes','class-a'),{leaderUid:'leader-b',updatedAt:serverTimestamp()}));
});
test('only admin can reassign class to another active leader',async()=>{
 await assertSucceeds(updateDoc(doc(db('admin'),'classes','class-a'),{leaderUid:'leader-b',updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('admin'),'classes','class-a'),{leaderUid:'student-a',updatedAt:serverTimestamp()}));
});
test('leader manages timetable, curriculum and roster',async()=>{
 await assertSucceeds(updateDoc(doc(db('leader-a'),'classes','class-a'),{time:'18:30',lessonSlugs:['ephesians-2','james-1'],memberUids:['student-a','student-b'],updatedAt:serverTimestamp()}));
});
test('invalid schedule, empty curriculum and duplicate members rejected',async()=>{
 for(const patch of [{time:'25:00'},{weekday:7},{duration:0},{lessonSlugs:[]},{memberUids:['student-a','student-a']}]) await assertFails(updateDoc(doc(db('leader-a'),'classes','class-a'),{...patch,updatedAt:serverTimestamp()}));
});
test('enrolled student reads class; other student and anonymous cannot',async()=>{
 await assertSucceeds(getDoc(doc(db('student-a'),'classes','class-a')));
 await assertFails(getDoc(doc(db('student-b'),'classes','class-a')));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'classes','class-a')));
});
test('role-scoped class queries work and unscoped student query fails',async()=>{
 await assertSucceeds(getDocs(query(collection(db('student-a'),'classes'),where('memberUids','array-contains','student-a'))));
 await assertSucceeds(getDocs(query(collection(db('leader-a'),'classes'),where('leaderUid','==','leader-a'))));
 await assertSucceeds(getDocs(collection(db('admin'),'classes')));
 await assertFails(getDocs(collection(db('student-a'),'classes')));
});
test('leaders can list students but cannot read admins or all users',async()=>{
 await assertSucceeds(getDocs(query(collection(db('leader-a'),'users'),where('role','==','user'))));
 await assertFails(getDoc(doc(db('leader-a'),'users','admin')));
 await assertFails(getDocs(collection(db('leader-a'),'users')));
});
test('member saves own answer in selected lesson',async()=>{
 await assertSucceeds(setDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2'),answer()));
});
test('other student, leader preview and out-of-program lesson cannot write',async()=>{
 await assertFails(setDoc(doc(db('student-b'),'classAnswers','class-a_student-b_ephesians-2'),answer('student-b')));
 await assertFails(setDoc(doc(db('leader-a'),'classAnswers','class-a_leader-a_ephesians-2'),answer('leader-a')));
 await assertFails(setDoc(doc(db('student-a'),'classAnswers','class-a_student-a_other'),{...answer(),_lesson:'other'}));
});
test('class answers are visible to owner and class leader only',async()=>{
 await assertSucceeds(getDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2')));
 await assertSucceeds(getDocs(query(collection(db('leader-a'),'classAnswers'),where('_class','==','class-a'))));
 await assertSucceeds(getDocs(query(collection(db('student-a'),'classAnswers'),where('_class','==','class-a'),where('_uid','==','student-a'))));
 await assertFails(getDocs(query(collection(db('leader-b'),'classAnswers'),where('_class','==','class-a'))));
 await assertFails(getDoc(doc(db('student-b'),'classAnswers','class-a_student-a_ephesians-2')));
});
test('removing a student revokes class answer access',async()=>{
 await updateDoc(doc(db('leader-a'),'classes','class-a'),{memberUids:[],updatedAt:serverTimestamp()});
 await assertFails(getDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2')));
 await assertFails(setDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2'),answer()));
});
test('archived classes remain readable but reject new answers',async()=>{
 await updateDoc(doc(db('leader-a'),'classes','class-a'),{archived:true,updatedAt:serverTimestamp()});
 await assertSucceeds(getDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2')));
 await assertFails(setDoc(doc(db('student-a'),'classAnswers','class-a_student-a_ephesians-2'),answer()));
});
test('demoted leader loses class management',async()=>{
 await updateDoc(doc(db('admin'),'users','leader-a'),{role:'user'});
 await assertFails(updateDoc(doc(db('leader-a'),'classes','class-a'),{name:'Changed',updatedAt:serverTimestamp()}));
});
test('personal answers stay private from class leader',async()=>{
 await assertSucceeds(getDoc(doc(db('student-a'),'answers','student-a_ephesians-2')));
 await assertFails(getDoc(doc(db('leader-a'),'answers','student-a_ephesians-2')));
});
test('missing own class answer can be loaded before first save',async()=>{
 await assertSucceeds(getDoc(doc(db('student-a'),'classAnswers','class-a_student-a_james-1')));
});
test('class deletion is disabled; archive preserves answers',async()=>{
 await assertFails(deleteDoc(doc(db('admin'),'classes','class-a')));
});

const invitation=(patch={})=>({email:'new@example.invalid',name:'Новый ученик',status:'pending',createdBy:'leader-a',updatedAt:serverTimestamp(),expiresAt:Timestamp.fromMillis(Date.now()+86400000),...patch});
const inviteRef=d=>doc(d,'classes/class-a/invitations/new@example.invalid');
const guest=(email='new@example.invalid',verified=true)=>env.authenticatedContext('new-student',{email,email_verified:verified}).firestore();
async function seedInvite(patch={}) {await setDoc(inviteRef(db('leader-a')),invitation(patch));}
function claim(d,extra={}) {const b=writeBatch(d);b.update(doc(d,'classes/class-a'),{memberUids:['student-a','new-student'],updatedAt:serverTimestamp(),...extra});b.update(inviteRef(d),{status:'accepted',acceptedUid:'new-student',updatedAt:serverTimestamp()});return b.commit();}
test('only admin publishes global announcements and only signed-in users read',async()=>{
 const a={title:'Встреча',body:'В воскресенье',archived:false,authorUid:'admin',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
 await assertSucceeds(setDoc(doc(db('admin'),'announcements/one'),a));
 await assertSucceeds(getDoc(doc(db('student-a'),'announcements/one')));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'announcements/one')));
 await assertFails(setDoc(doc(db('leader-a'),'announcements/two'),{...a,authorUid:'leader-a'}));
 await assertFails(updateDoc(doc(db('student-a'),'announcements/one'),{body:'Changed',updatedAt:serverTimestamp()}));
});
test('class announcements stay within class and can be archived by owner',async()=>{
 const ref=d=>doc(d,'classes/class-a/announcements/one');
 await assertSucceeds(setDoc(ref(db('leader-a')),{title:'Урок',body:'Подготовьтесь',archived:false,authorUid:'leader-a',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
 await assertSucceeds(getDoc(ref(db('student-a'))));await assertFails(getDoc(ref(db('student-b'))));
 await assertFails(updateDoc(ref(db('leader-b')),{body:'Changed',updatedAt:serverTimestamp()}));
 await assertSucceeds(updateDoc(ref(db('leader-a')),{archived:true,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(db('leader-a')),{authorUid:'admin',updatedAt:serverTimestamp()}));
});
test('only class manager creates invitations',async()=>{
 await assertSucceeds(setDoc(inviteRef(db('leader-a')),invitation()));
 await assertFails(setDoc(inviteRef(db('leader-b')),invitation({createdBy:'leader-b'})));
 await assertFails(setDoc(inviteRef(db('student-a')),invitation({createdBy:'student-a'})));
});
test('verified recipient accepts invitation atomically and can read class',async()=>{
 await seedInvite();await assertSucceeds(getDoc(doc(guest(),'classes/class-a')));await assertSucceeds(claim(guest()));
 assert.equal((await getDoc(inviteRef(db('leader-a')))).data().acceptedUid,'new-student');
 await assertSucceeds(getDoc(doc(guest(),'classes/class-a')));
});
test('wrong or unverified mailbox cannot read invitation or accept it',async()=>{
 await seedInvite();for(const d of [guest('other@example.invalid'),guest('new@example.invalid',false)]){
 await assertFails(getDoc(inviteRef(d)));await assertFails(getDoc(doc(d,'classes/class-a')));await assertFails(claim(d));}
});
test('invitation cannot authorize extra changes or unilateral membership',async()=>{
 await seedInvite();await assertFails(claim(guest(),{name:'Hijack'}));
 await assertFails(claim(guest(),{memberUids:['student-a','new-student','attacker']}));
 await assertFails(updateDoc(doc(guest(),'classes/class-a'),{memberUids:['student-a','new-student'],updatedAt:serverTimestamp()}));
});
test('revoked and expired invitations cannot be accepted',async()=>{
 await seedInvite();await updateDoc(inviteRef(db('leader-a')),{status:'revoked',updatedAt:serverTimestamp()});await assertFails(claim(guest()));
 await env.withSecurityRulesDisabled(async c=>{await setDoc(inviteRef(c.firestore()),invitation({expiresAt:Timestamp.fromMillis(Date.now()-1000)}));});await assertFails(claim(guest()));
});
test('accepted invitation cannot rejoin after removal',async()=>{
 await seedInvite();await assertSucceeds(claim(guest()));await updateDoc(doc(db('leader-a'),'classes/class-a'),{memberUids:['student-a'],updatedAt:serverTimestamp()});await assertFails(claim(guest()));
});
test('archived class and demoted inviter invalidate pending invitations',async()=>{
 await seedInvite();await updateDoc(doc(db('leader-a'),'classes/class-a'),{archived:true,updatedAt:serverTimestamp()});await assertFails(claim(guest()));
 await updateDoc(doc(db('leader-a'),'classes/class-a'),{archived:false,updatedAt:serverTimestamp()});await updateDoc(doc(db('admin'),'users/leader-a'),{role:'user'});await assertFails(claim(guest()));
});
