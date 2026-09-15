(function(){
  'use strict';
  var form=document.getElementById('acceptInviteForm'),status=document.getElementById('inviteStatus'),classId=new URLSearchParams(location.search).get('class');
  var link=location.href, signedIn=false;
  if(!classId){form.hidden=true;status.textContent='Откройте полную ссылку из письма-приглашения.';return;}
  form.addEventListener('submit',async function(e){
    e.preventDefault();var email=document.getElementById('inviteEmail').value.trim().toLowerCase(),password=document.getElementById('invitePassword').value;
    if(password!==document.getElementById('invitePasswordAgain').value){status.textContent='Пароли не совпадают.';return;}
    var submit=form.querySelector('[type=submit]');submit.disabled=true;status.textContent='Проверяем приглашение…';
    try {
      var auth=BibleDB.auth,current=auth.currentUser;
      if(!signedIn && auth.isSignInWithEmailLink(link)) { await auth.signInWithEmailLink(email,link);signedIn=true;history.replaceState(null,'','?class='+encodeURIComponent(classId)); }
      current=auth.currentUser;
      if(!current || !current.emailVerified || current.email.toLowerCase()!==email)throw new Error('Откройте ссылку из письма и укажите email, на который оно пришло.');
      await current.getIdToken(true);
      await BibleDB.checkInvitation(classId);
      // Recent email-link sign-in proves mailbox ownership; password never goes to a leader.
      await current.updatePassword(password);
      var invitation=await BibleDB.acceptInvitation(classId);
      try {
        var profile=await BibleDB.getUserProfile(current.uid);
        if(!profile || profile.name===profile.email)await BibleDB.setUserProfile(current.uid,{name:invitation.name,email:current.email,...(!profile?{role:'user'}:{})});
      } catch(profileError) { /* Membership is already saved; profile can be retried on normal sign-in. */ }
      form.hidden=true;status.textContent='Готово! Пароль установлен, вы добавлены в класс.';
      var a=document.createElement('a');a.className='btn btn-primary';a.href='/classes/?id='+encodeURIComponent(classId);a.textContent='Открыть класс';status.after(a);
    } catch(err){status.textContent=err.code==='auth/invalid-action-code'||err.code==='auth/expired-action-code'?'Ссылка недействительна или уже использована. Попросите ведущего прислать новое приглашение.':err.code==='auth/requires-recent-login'?'Для установки пароля нужна свежая ссылка. Попросите ведущего отправить приглашение повторно.':err.code?'Не удалось принять приглашение. Проверьте email и соединение, затем повторите.':err.message;
    } finally {document.getElementById('invitePassword').value='';document.getElementById('invitePasswordAgain').value='';submit.disabled=false;}
  });
})();
