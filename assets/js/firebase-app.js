(function() {
  'use strict';

  var firebaseConfig = {
    apiKey: "AIzaSyBu_g78Wmrls7_Q6A1mTgQwb013LlTiWls",
    authDomain: "bible-learning-b4b4d.firebaseapp.com",
    projectId: "bible-learning-b4b4d",
    storageBucket: "bible-learning-b4b4d.firebasestorage.app",
    messagingSenderId: "1093935422667",
    appId: "1:1093935422667:web:a1d86785a7ac3aea320f4e"
  };

  firebase.initializeApp(firebaseConfig);

  var auth = firebase.auth();
  var db = firebase.firestore();

  // ========== Auth API ==========

  function login(email, password) {
    return auth.signInWithEmailAndPassword(email, password);
  }

  function logout() {
    return auth.signOut();
  }

  function createUser(email, password) {
    // Admin creates user via secondary app to avoid logging out
    var secondaryApp;
    try {
      secondaryApp = firebase.app('secondary');
    } catch (e) {
      secondaryApp = firebase.initializeApp(firebaseConfig, 'secondary');
    }
    return secondaryApp.auth().createUserWithEmailAndPassword(email, password)
      .then(function(cred) {
        return secondaryApp.auth().signOut().then(function() { return cred; });
      });
  }

  function onAuthChanged(callback) {
    return auth.onAuthStateChanged(callback);
  }

  function getCurrentUser() {
    return auth.currentUser;
  }

  // ========== User Profiles (Firestore) ==========

  function getUserProfile(uid) {
    return db.collection('users').doc(uid).get().then(function(doc) {
      return doc.exists ? doc.data() : null;
    });
  }

  function setUserProfile(uid, data) {
    return db.collection('users').doc(uid).set(data, { merge: true });
  }

  function getAllUsers() {
    return db.collection('users').get().then(function(snap) {
      var users = [];
      snap.forEach(function(doc) {
        var d = doc.data();
        d.uid = doc.id;
        users.push(d);
      });
      return users;
    });
  }

  function resetPassword(email) {
    auth.languageCode = 'ru';
    return auth.sendPasswordResetEmail(email);
  }

  function restoreUserProfile(uid, data) {
    var ref = db.collection('users').doc(uid);
    return db.runTransaction(function(tx) {
      return tx.get(ref).then(function(snapshot) {
        if (snapshot.exists) throw new Error('Профиль уже существует. Обновите список пользователей.');
        tx.set(ref, { name: data.name, email: data.email, role: 'user', restoredAt: new Date().toISOString() });
      });
    });
  }

  // ========== Answers (Firestore) ==========

  function getAnswers(uid, lessonSlug, classId) {
    return answerRef(uid, lessonSlug, classId).get().then(function(doc) {
      return doc.exists ? doc.data() : null;
    });
  }

  function saveAnswers(uid, lessonSlug, answers, classId) {
    answers._savedAt = new Date().toISOString();
    answers._uid = uid;
    answers._lesson = lessonSlug;
    if (classId) answers._class = classId;
    return answerRef(uid, lessonSlug, classId).set(answers);
  }

  function getUserAnswers(uid) {
    return db.collection('answers').where('_uid', '==', uid).get().then(function(snap) {
      var result = {};
      snap.forEach(function(doc) {
        var d = doc.data();
        result[d._lesson] = d;
      });
      return result;
    });
  }

  function getAllAnswers() {
    return db.collection('answers').get().then(function(snap) {
      var result = {};
      snap.forEach(function(doc) {
        var d = doc.data();
        if (!result[d._uid]) result[d._uid] = {};
        result[d._uid][d._lesson] = d;
      });
      return result;
    });
  }

  function answerRef(uid, slug, classId) {
    return classId ? db.collection('classAnswers').doc(classId + '_' + uid + '_' + slug)
      : db.collection('answers').doc(uid + '_' + slug);
  }

  function rows(snapshot) {
    return snapshot.docs.map(function(doc) { return Object.assign({}, doc.data(), { id: doc.id }); });
  }
  function getClasses(user, profile) {
    var query = db.collection('classes');
    if (profile.role === 'leader') query = query.where('leaderUid', '==', user.uid);
    else if (profile.role !== 'admin') query = query.where('memberUids', 'array-contains', user.uid);
    return query.get().then(rows);
  }
  function getClass(id) {
    return db.collection('classes').doc(id).get().then(function(doc) {
      if (!doc.exists) throw new Error('class-not-found');
      return Object.assign({}, doc.data(), { id: doc.id });
    });
  }
  function saveClass(id, data, originalMembers) {
    var ref = id ? db.collection('classes').doc(id) : db.collection('classes').doc();
    data.updatedAt = firebase.firestore.FieldValue.serverTimestamp();
    if (!id) data.createdAt = firebase.firestore.FieldValue.serverTimestamp();
    if (id && originalMembers) return db.runTransaction(function(tx) {
      return tx.get(ref).then(function(snapshot) {
        if (!snapshot.exists) throw new Error('class-not-found');
        var removed = originalMembers.filter(function(uid) { return data.memberUids.indexOf(uid) < 0; });
        var added = data.memberUids.filter(function(uid) { return originalMembers.indexOf(uid) < 0; });
        var members = snapshot.data().memberUids.filter(function(uid) { return removed.indexOf(uid) < 0; });
        added.forEach(function(uid) { if (members.indexOf(uid) < 0) members.push(uid); });
        if (members.length > 200) throw new Error('class-full');
        tx.set(ref, Object.assign({}, data, {memberUids:members}), {merge:true});
        return ref.id;
      });
    });
    return ref.set(data, { merge: true }).then(function() { return ref.id; });
  }
  function getStudents() {
    return db.collection('users').where('role', '==', 'user').get().then(rows);
  }
  function getClassAnswers(classId, uid) {
    var query = db.collection('classAnswers').where('_class', '==', classId);
    if (uid) query = query.where('_uid', '==', uid);
    return query.get().then(rows);
  }

  function announcementCollection(classId) {
    return classId ? db.collection('classes').doc(classId).collection('announcements') : db.collection('announcements');
  }
  function getAnnouncements(classId) { return announcementCollection(classId).get().then(rows); }
  function saveAnnouncement(classId, id, data) {
    var ref = id ? announcementCollection(classId).doc(id) : announcementCollection(classId).doc();
    var payload = Object.assign({}, data, {updatedAt:firebase.firestore.FieldValue.serverTimestamp()});
    if (!id) { payload.createdAt = firebase.firestore.FieldValue.serverTimestamp(); payload.authorUid = auth.currentUser.uid; }
    return ref.set(payload, {merge:true});
  }
  function invitationRef(classId, email) { return db.collection('classes').doc(classId).collection('invitations').doc(email.trim().toLowerCase()); }
  function getInvitations(classId) { return db.collection('classes').doc(classId).collection('invitations').get().then(rows); }
  function sendInvitation(classId, email, name) {
    email = email.trim().toLowerCase();
    var ref = invitationRef(classId,email);
    return ref.set({email:email, name:name.trim(), status:'pending', createdBy:auth.currentUser.uid,
      updatedAt:firebase.firestore.FieldValue.serverTimestamp(), expiresAt:firebase.firestore.Timestamp.fromMillis(Date.now()+7*86400000)})
      .then(function() {
        auth.languageCode = 'ru';
        return auth.sendSignInLinkToEmail(email,{url:window.location.origin+'/invite/?class='+encodeURIComponent(classId),handleCodeInApp:true});
      });
  }
  function revokeInvitation(classId,email) { return invitationRef(classId,email).update({status:'revoked',updatedAt:firebase.firestore.FieldValue.serverTimestamp()}); }
  function checkInvitation(classId) {
    var current = auth.currentUser;
    if (!current || !current.emailVerified) return Promise.reject(new Error('Подтвердите email по ссылке из письма.'));
    return invitationRef(classId,current.email).get().then(function(snapshot) {
      if (!snapshot.exists) throw new Error('Приглашение для этого email не найдено.');
      var invitation = snapshot.data();
      if (invitation.status !== 'pending' || invitation.expiresAt.toMillis() <= Date.now()) throw new Error('Приглашение истекло или уже использовано. Попросите ведущего отправить новое.');
      return getClass(classId).then(function(c) {
        if (c.archived) throw new Error('Этот класс закрыт.');
        if (c.memberUids.length >= 200 && c.memberUids.indexOf(current.uid) < 0) throw new Error('Класс заполнен. Обратитесь к ведущему.');
        return invitation;
      });
    });
  }
  function acceptInvitation(classId) {
    var current = auth.currentUser;
    if (!current || !current.emailVerified) return Promise.reject(new Error('Подтвердите email по ссылке из письма.'));
    var ref = invitationRef(classId,current.email), classRef = db.collection('classes').doc(classId);
    return db.runTransaction(function(tx) {
      return Promise.all([tx.get(ref),tx.get(classRef)]).then(function(snaps) {
        if (!snaps[0].exists) throw new Error('Приглашение для этого email не найдено.');
        var invitation = snaps[0].data();
        if (invitation.status !== 'pending' || invitation.expiresAt.toMillis() <= Date.now()) throw new Error('Приглашение истекло или уже использовано. Попросите ведущего отправить новое.');
        if (!snaps[1].exists || snaps[1].data().archived) throw new Error('Этот класс закрыт.');
        var members = snaps[1].data().memberUids.slice();
        if (members.indexOf(current.uid)<0) members.push(current.uid);
        if (members.length>200) throw new Error('Класс заполнен. Обратитесь к ведущему.');
        tx.update(classRef,{memberUids:members,updatedAt:firebase.firestore.FieldValue.serverTimestamp()});
        tx.update(ref,{status:'accepted',acceptedUid:current.uid,updatedAt:firebase.firestore.FieldValue.serverTimestamp()});
        return invitation;
      });
    });
  }

  // ========== Expose API ==========

  window.BibleDB = {
    getAnnouncements:getAnnouncements, saveAnnouncement:saveAnnouncement,
    getInvitations:getInvitations, sendInvitation:sendInvitation, revokeInvitation:revokeInvitation, checkInvitation:checkInvitation, acceptInvitation:acceptInvitation,
    getClasses: getClasses,
    getClass: getClass,
    saveClass: saveClass,
    getStudents: getStudents,
    getClassAnswers: getClassAnswers,
    auth: auth,
    db: db,
    login: login,
    logout: logout,
    createUser: createUser,
    onAuthChanged: onAuthChanged,
    getCurrentUser: getCurrentUser,
    getUserProfile: getUserProfile,
    setUserProfile: setUserProfile,
    getAllUsers: getAllUsers,
    resetPassword: resetPassword,
    restoreUserProfile: restoreUserProfile,
    getAnswers: getAnswers,
    saveAnswers: saveAnswers,
    getUserAnswers: getUserAnswers,
    getAllAnswers: getAllAnswers
  };
})();
