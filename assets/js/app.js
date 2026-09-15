(function() {
  'use strict';
  var lessonEl = document.getElementById('lessonContent');
  if (!lessonEl) return;
  var lessonSlug = lessonEl.getAttribute('data-lesson');
  var authGate = document.getElementById('authGate');
  var lessonActions = document.getElementById('lessonActions');
  var saveBtn = document.getElementById('saveAnswers');
  var textareas = lessonEl.querySelectorAll('textarea[data-question]');
  var classId = new URLSearchParams(window.location.search).get('class');
  var currentUid = null;
  var readOnly = false;
  var loaded = false;
  var saveQueue = Promise.resolve();
  var statusTimer;

  BibleAuth.onReady(function(user) {
    currentUid = user ? user.uid : null;
    loaded = false;
    authGate.style.display = user ? 'none' : '';
    lessonEl.style.display = user ? '' : 'none';
    lessonActions.style.display = user ? '' : 'none';
    saveBtn.disabled = true;
    textareas.forEach(function(ta) { ta.disabled = true; ta.value = ''; });
    if (!user) return;
    var access = classId ? BibleDB.getClass(classId).then(function(c) {
      if (!c.lessonSlugs.includes(lessonSlug)) throw new Error('lesson-not-in-class');
      readOnly = c.archived || !c.memberUids.includes(user.uid);
      var notice = document.getElementById('classLessonNotice');
      if (!notice) { notice = document.createElement('p'); notice.id = 'classLessonNotice'; lessonEl.before(notice); }
      notice.replaceChildren();
      var back = document.createElement('a'); back.href = '/classes/?id=' + encodeURIComponent(classId); back.textContent = '← ' + c.name; notice.appendChild(back);
      if (readOnly) notice.appendChild(document.createTextNode(' · Просмотр урока (ответы недоступны для изменения)'));
      var nav = document.querySelector('.lesson-nav');
      if (nav) {
        nav.replaceChildren(); var index = c.lessonSlugs.indexOf(lessonSlug);
        [-1, 1].forEach(function(step) {
          var slug = c.lessonSlugs[index + step]; if (!slug) return;
          var link = document.createElement('a'); link.href = '/lessons/' + encodeURIComponent(slug) + '/?class=' + encodeURIComponent(classId);
          link.className = 'btn btn-outline'; link.textContent = step < 0 ? '← Предыдущий урок класса' : 'Следующий урок класса →'; nav.appendChild(link);
        });
      }
    }) : Promise.resolve();
    access.then(function() { return BibleDB.getAnswers(user.uid, lessonSlug, classId); }).then(function(saved) {
      if (currentUid !== user.uid) return;
      textareas.forEach(function(ta) {
        ta.value = (saved || {})[ta.getAttribute('data-question')] || '';
        ta.disabled = readOnly;
      });
      loaded = true;
      saveBtn.disabled = readOnly;
    }).catch(function() {
      showStatus('Не удалось загрузить ответы. Обновите страницу для повторной попытки.', true);
    });
  });

  saveBtn.addEventListener('click', doSave);
  textareas.forEach(function(ta) { ta.addEventListener('blur', doSave); });

  function doSave() {
    if (!loaded || !currentUid || readOnly) return;
    var uid = currentUid;
    var answers = {};
    textareas.forEach(function(ta) {
      var val = ta.value.trim();
      if (val) answers[ta.getAttribute('data-question')] = val;
    });
    showStatus('Сохранение...', false);
    // Preserve edit order, including clearing the final answer.
    saveQueue = saveQueue.then(function() {
      return BibleDB.saveAnswers(uid, lessonSlug, answers, classId);
    }).then(function() {
      if (currentUid === uid) showStatus('Ответы сохранены!', false);
    }).catch(function() {
      if (currentUid === uid) showStatus('Ошибка сохранения. Нажмите «Сохранить ответы», чтобы повторить.', true);
    });
  }

  function showStatus(msg, isError) {
    var el = document.getElementById('saveStatus');
    if (!el) return;
    clearTimeout(statusTimer);
    el.textContent = msg;
    el.style.color = isError ? '#c0392b' : 'var(--primary)';
    if (!isError) statusTimer = setTimeout(function() { el.textContent = ''; }, 3000);
  }
})();
