(function() {
  'use strict';
  function el(tag,text,cls) { var n=document.createElement(tag); if(text!=null)n.textContent=text; if(cls)n.className=cls; return n; }
  function button(text,fn) { var n=el('button',text,'btn btn-outline btn-sm');n.type='button';n.addEventListener('click',fn);return n; }
  function field(form,title,type,limit) { var label=el('label',title,'form-group'),input=el(type==='textarea'?'textarea':'input');if(type!=='textarea')input.type=type;input.required=true;input.maxLength=limit;label.appendChild(input);form.appendChild(label);return input; }
  function stamp(v) { return v && v.toMillis ? v.toMillis() : 0; }
  function announcements(root,classId,canManage) {
    root.replaceChildren(); var generation={};root._newsGeneration=generation;
    root.classList.add('community-section');
    var heading=el('div',null,'community-heading');root.appendChild(heading);
    if(!root.hasAttribute('data-announcements-page'))heading.appendChild(el('h2',classId?'Объявления класса':'Общие объявления'));
    var status=el('p','Загрузка…','community-status');status.setAttribute('role','status');root.appendChild(status);
    var list=el('div');root.appendChild(list);
    var editor=el('form',null,'community-editor');editor.hidden=true;root.insertBefore(editor,list);
    var title=field(editor,'Заголовок','text',120),body=field(editor,'Текст объявления','textarea',4000),save=el('button','Опубликовать','btn btn-primary btn-sm');save.type='submit';editor.appendChild(save);
    editor.appendChild(button('Отмена',function(){editor.hidden=true;})); var editing=null;
    if(canManage) heading.appendChild(button('Написать объявление',function(){editing=null;editor.reset();save.textContent='Опубликовать';editor.hidden=false;title.focus();}));
    function load() {
      return BibleDB.getAnnouncements(classId).then(function(items){
        if(root._newsGeneration!==generation)return;
        list.replaceChildren();var visible=items.filter(function(a){return canManage || !a.archived;}).sort(function(a,b){return stamp(b.createdAt)-stamp(a.createdAt);});
        status.textContent=visible.length?'':'Объявлений пока нет.';
        visible.forEach(function(a){
          var article=el('article',null,'community-card');article.appendChild(el('h3',a.title+(a.archived?' · Архив':'')));
          article.appendChild(el('p',stamp(a.createdAt)?new Date(stamp(a.createdAt)).toLocaleDateString('ru-RU'):'' ,'class-muted'));
          article.appendChild(el('p',a.body,'community-body'));
          if(canManage){article.appendChild(button('Изменить',function(){editing=a.id;title.value=a.title;body.value=a.body;save.textContent='Сохранить';editor.hidden=false;title.focus();}));
            article.appendChild(button(a.archived?'Вернуть из архива':'В архив',function(){BibleDB.saveAnnouncement(classId,a.id,{archived:!a.archived}).then(load).catch(fail);}));}
          list.appendChild(article);
        });
      }).catch(fail);
    }
    function fail(){if(root._newsGeneration===generation)status.textContent='Не удалось загрузить или сохранить объявления. Обновите страницу и повторите.';}
    editor.addEventListener('submit',function(e){e.preventDefault();if(!canManage)return;if(!title.value.trim()||!body.value.trim())return;
      save.disabled=true;var data={title:title.value.trim(),body:body.value.trim()};if(!editing)data.archived=false;
      BibleDB.saveAnnouncement(classId,editing,data).then(function(){editor.hidden=true;return load();}).catch(fail).finally(function(){save.disabled=false;});});
    load();
  }
  function invitations(root,classId) {
    root.classList.add('community-section');root.appendChild(el('h3','Пригласить ученика'));
    root.appendChild(el('p','Ученик получит ссылку по email, сам задаст пароль и присоединится к этому классу. Приглашение действует 7 дней.'));
    root.appendChild(el('p','На бесплатном тарифе Firebase доступно до 5 писем-приглашений в день.','class-muted'));
    var form=el('form',null,'community-editor'),name=field(form,'Имя ученика','text',120),email=field(form,'Email ученика','email',254),send=el('button','Отправить приглашение','btn btn-primary btn-sm');send.type='submit';form.appendChild(send);root.appendChild(form);
    var status=el('p');status.setAttribute('role','status');root.appendChild(status);var list=el('div');root.appendChild(list);
    function load(){return BibleDB.getInvitations(classId).then(function(items){if(!root.isConnected)return;list.replaceChildren();items.forEach(function(i){
      var expired=stamp(i.expiresAt)<=Date.now(), row=el('div',null,'community-card');row.appendChild(el('p',i.name+' · '+i.email));
      row.appendChild(el('p',i.status==='accepted'?'Принято':i.status==='revoked'?'Отозвано':expired?'Срок истёк':'Ожидает принятия (статус не подтверждает доставку письма)'));
      if(i.status!=='accepted')row.appendChild(button('Отправить повторно',function(){name.value=i.name;email.value=i.email;email.focus();status.textContent='Проверьте адрес и нажмите «Отправить приглашение».';}));
      if(i.status==='pending')row.appendChild(button('Отозвать',function(){BibleDB.revokeInvitation(classId,i.email).then(load).catch(function(){status.textContent='Не удалось отозвать приглашение.';});}));list.appendChild(row);
    });}).catch(function(){status.textContent='Не удалось загрузить приглашения.';});}
    form.addEventListener('submit',function(e){e.preventDefault();if(!name.value.trim())return;send.disabled=true;status.textContent='Отправляем приглашение…';
      BibleDB.sendInvitation(classId,email.value,name.value).then(function(){status.textContent='Firebase принял запрос отправки. Попросите ученика проверить входящие и «Спам».';form.reset();}).catch(function(err){status.textContent=err.code==='auth/quota-exceeded'||err.code==='auth/too-many-requests'?'Лимит писем Firebase исчерпан. Попробуйте позже; не нужно создавать аккаунт повторно.':'Не удалось отправить письмо. Проверьте адрес и повторите отправку. Приглашение могло сохраниться без отправки письма.';}).finally(function(){send.disabled=false;load();});});
    load();
  }
  window.BibleCommunity={announcements:announcements,invitations:invitations};
  BibleAuth.onReady(function(user,profile){document.querySelectorAll('[data-global-announcements]').forEach(function(root){if(user){announcements(root,null,profile.role==='admin');}else{root._newsGeneration={};root.replaceChildren();if(root.hasAttribute('data-announcements-page')){var login=el('a','Войдите, чтобы прочитать объявления.');login.href='/login/';root.appendChild(login);}}});});
})();
