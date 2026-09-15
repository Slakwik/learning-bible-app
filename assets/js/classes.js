(function() {
  'use strict';
  var catalog = JSON.parse(document.getElementById('classLessonCatalog').textContent);
  var courseNames = {james:'Послание Иакова',ephesians:'Послание к Ефесянам',galatians:'Послание к Галатам',philippians:'Послание к Филиппийцам',proverbs:'Мудрость Соломона','christian-life':'Жизнь христианина',deuteronomy:'Второзаконие',mark:'Евангелие от Марка',genesis1:'Бытие (часть 1)',genesis2:'Бытие (часть 2)',joshua:'Иисус Навин','amos-isaiah':'Амос и Исаия',daniel:'Даниил',acts1:'Деяния (часть 1)',acts2:'Деяния (часть 2)',corinthians1:'1 Коринфянам',corinthians2:'2 Коринфянам',thessalonians:'1–2 Фессалоникийцам','first-john':'Послание 1 Иоанна',prayer:'Если будете молиться','bible-book':'Библия: Божья удивительная книга'};
  var lessons = {};
  catalog.forEach(function(l) { lessons[l.slug] = l; });
  var user, profile, students = [], leaders = [], classes = [], editingId = null, originalMembers = [];
  var selectedLessons = [], selectedMembers = [], initialized = false;
  var days = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  function byId(id) { return document.getElementById(id); }
  function el(tag, text, cls) { var n = document.createElement(tag); if (text != null) n.textContent = text; if (cls) n.className = cls; return n; }
  function button(text, fn, cls) { var n = el('button', text, cls || 'btn btn-outline btn-sm'); n.type = 'button'; n.addEventListener('click', fn); return n; }
  function manages(c) { return profile.role === 'admin' || (profile.role === 'leader' && c.leaderUid === user.uid); }
  function manager() { return profile.role === 'admin' || profile.role === 'leader'; }
  function status(text) { byId('classesStatus').textContent = text; }
  function error() { status('Не удалось загрузить данные. Обновите страницу, чтобы повторить попытку.'); }
  function schedule(c) { return days[c.weekday] + ', ' + c.time + ' · ' + c.duration + ' мин · Москва (UTC+3)'; }
  function lessonLink(c, slug) {
    var l = lessons[slug];
    var a = el('a', l ? l.title + ' — ' + l.reference : slug);
    a.href = l ? l.url + '?class=' + encodeURIComponent(c.id) : '#';
    return a;
  }

  BibleAuth.onReady(function(u, p) {
    user = u; profile = p;
    byId('classesList').replaceChildren(); byId('classDetail').hidden = true; byId('classEditor').hidden = true;
    byId('newClass').hidden = !u || !manager();
    byId('classesGuest').hidden = !!u;
    byId('classesHeading').hidden = !u;
    byId('classesList').hidden = !u;
    if (!u) { status(''); return; }
    if (!initialized) { bind(); initialized = true; }
    refresh();
  });

  function refresh() {
    status('Загрузка классов…');
    var uid = user.uid;
    return BibleDB.getClasses(user, profile).then(function(data) {
      if (!user || user.uid !== uid) return;
      classes = data.sort(function(a,b) { return Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name, 'ru'); });
      renderList();
      var id = new URLSearchParams(location.search).get('id');
      if (id) { var c = classes.find(function(x) { return x.id === id; }); if (c) showDetail(c); else status('Класс не найден или вы больше не состоите в нём.'); }
    }).catch(error);
  }
  function renderList() {
    var list = byId('classesList'); list.replaceChildren();
    status(classes.length ? '' : manager() ? 'Классов пока нет. Создайте первый класс.' : 'Вы пока не добавлены в класс. Обратитесь к ведущему.');
    classes.forEach(function(c) {
      var card = el('article', null, 'class-card');
      card.appendChild(el('h2', c.name));
      card.appendChild(el('p', 'Ведущий: ' + c.leaderName));
      card.appendChild(el('p', schedule(c), 'class-muted'));
      card.appendChild(el('p', 'Уроков: ' + c.lessonSlugs.length + ' · Участников: ' + c.memberUids.length + (c.archived ? ' · Архив' : ''), 'class-badge'));
      card.appendChild(button('Открыть класс', function() { history.replaceState(null, '', '?id=' + encodeURIComponent(c.id)); showDetail(c); }));
      list.appendChild(card);
    });
  }
  function showDetail(c) {
    byId('classEditor').hidden = true;
    var section = byId('classDetail'); section.hidden = false; section.replaceChildren();
    var card = el('article', null, 'class-card'); section.appendChild(card);
    var header = el('div', null, 'class-heading'); header.appendChild(el('h2', c.name));
    if (manages(c)) header.appendChild(button('Изменить класс', function() { openEditor(c); }));
    card.appendChild(header);
    card.appendChild(el('p', 'Ведущий: ' + c.leaderName));
    card.appendChild(el('p', schedule(c)));
    card.appendChild(el('p', 'Первая встреча: ' + c.startDate.split('-').reverse().join('.')));
    if (c.place) card.appendChild(el('p', 'Место: ' + c.place));
    if (c.description) card.appendChild(el('p', c.description));
    if (window.BibleCommunity) {
      var news = el('section'); card.appendChild(news); BibleCommunity.announcements(news, c.id, manages(c) && !c.archived);
      if (manages(c) && !c.archived) { var invites = el('section'); card.appendChild(invites); BibleCommunity.invitations(invites, c.id); }
    }
    if (c.archived) card.appendChild(el('p', 'Класс завершён. Ответы доступны для просмотра; новые ответы не принимаются.', 'class-muted'));
    card.appendChild(el('h3', 'Программа класса'));
    var list = el('ol', null, 'class-lessons');
    c.lessonSlugs.forEach(function(slug) { var li = el('li'); li.appendChild(lessonLink(c, slug)); list.appendChild(li); });
    card.appendChild(list);
    if (manages(c)) {
      var roster = el('div'); card.appendChild(roster); roster.appendChild(el('p', 'Загрузка участников и ответов…'));
      Promise.all([BibleDB.getStudents(), BibleDB.getClassAnswers(c.id)]).then(function(data) {
        if (!roster.isConnected) return;
        roster.replaceChildren(); roster.appendChild(el('h3', 'Участники и ответы'));
        if (!c.memberUids.length) { roster.appendChild(el('p', 'Добавьте учеников через «Изменить класс».')); return; }
        c.memberUids.forEach(function(uid) {
          var student = data[0].find(function(s) { return s.id === uid; });
          var answers = data[1].filter(function(a) { return a._uid === uid; });
          var details = el('details'); var summary = el('summary', (student ? student.name + ' (' + student.email + ')' : 'Профиль ученика недоступен (обратитесь к администратору)') + ' · уроков с ответами: ' + answers.filter(function(a) { return Object.keys(a).some(function(k) { return /^q/.test(k); }); }).length);
          details.appendChild(summary);
          if (!answers.length) details.appendChild(el('p', 'Ответов пока нет.'));
          answers.forEach(function(a) {
            details.appendChild(el('h4', lessons[a._lesson] ? lessons[a._lesson].title : a._lesson));
            Object.keys(a).filter(function(k) { return /^q/.test(k); }).sort(function(a,b) { return a.localeCompare(b, 'ru', { numeric:true }); }).forEach(function(k) {
              details.appendChild(el('p', 'Вопрос ' + k.slice(1) + ': ' + a[k], 'class-answer'));
            });
          });
          roster.appendChild(details);
        });
      }).catch(function() { roster.replaceChildren(el('p', 'Не удалось загрузить участников и ответы. Откройте класс ещё раз.')); });
    }
    section.scrollIntoView({ behavior:'smooth', block:'start' });
  }

  function openEditor(c) {
    status('Загрузка списка учеников…');
    var requests = [BibleDB.getStudents()];
    if (profile.role === 'admin') requests.push(BibleDB.getAllUsers());
    var uid = user.uid;
    Promise.all(requests).then(function(data) {
      if (!user || user.uid !== uid) return;
      students = data[0]; leaders = data[1] ? data[1].filter(function(u) { return u.role === 'leader' || u.role === 'admin'; }).map(function(u) { return Object.assign({}, u, { id:u.uid }); }) : [{ id:user.uid, name:profile.name }];
      editingId = c ? c.id : null; selectedLessons = c ? c.lessonSlugs.slice() : []; selectedMembers = c ? c.memberUids.slice() : [];
      originalMembers = selectedMembers.slice();
      byId('classForm').reset(); byId('classFormError').textContent = '';
      byId('classEditorTitle').textContent = c ? 'Изменить класс' : 'Новый класс';
      byId('className').value = c ? c.name : '';
      byId('classDay').value = c ? c.weekday : 0;
      byId('classTime').value = c ? c.time : '11:00';
      byId('classDuration').value = c ? c.duration : 60;
      byId('classStart').value = c ? c.startDate : '';
      byId('classPlace').value = c ? c.place : '';
      byId('classDescription').value = c ? c.description : '';
      byId('classArchived').checked = c ? c.archived : false;
      var select = byId('classLeader'); select.replaceChildren();
      leaders.forEach(function(l) { var opt = el('option', l.name || l.email); opt.value = l.id; select.appendChild(opt); });
      select.value = c ? c.leaderUid : user.uid;
      byId('classLeaderField').hidden = profile.role !== 'admin';
      byId('classDetail').hidden = true; byId('classEditor').hidden = false;
      renderLessonPicker(); renderSelected(); renderStudentPicker(); status('');
      byId('className').focus({ preventScroll:true });
      byId('classEditor').scrollIntoView({ behavior:'smooth', block:'start' });
    }).catch(error);
  }
  function checkRow(text, checked, fn) {
    var label = el('label', null, 'class-check'); var input = el('input'); input.type = 'checkbox'; input.checked = checked;
    input.addEventListener('change', function() { fn(input.checked); }); label.appendChild(input); label.appendChild(el('span', text)); return label;
  }
  function renderLessonPicker() {
    var box = byId('lessonPicker'), search = byId('lessonSearch').value.toLocaleLowerCase('ru'); box.replaceChildren();
    catalog.filter(function(l) { return (!byId('lessonCourse').value || byId('lessonCourse').value === l.course) && (l.title + ' ' + (courseNames[l.course] || l.course) + ' ' + l.reference).toLocaleLowerCase('ru').includes(search); }).forEach(function(l) {
      box.appendChild(checkRow(l.title + ' · ' + l.reference + ' (' + (courseNames[l.course] || l.course) + ')', selectedLessons.includes(l.slug), function(checked) {
        if (checked) selectedLessons.push(l.slug); else selectedLessons = selectedLessons.filter(function(s) { return s !== l.slug; }); renderSelected();
      }));
    });
  }
  function renderSelected() {
    var list = byId('selectedLessons'); list.replaceChildren();
    selectedLessons.forEach(function(slug, i) {
      var li = el('li', lessons[slug] ? lessons[slug].title : slug);
      var up = button('↑', function() { var temp = selectedLessons[i-1]; selectedLessons[i-1] = slug; selectedLessons[i] = temp; renderSelected(); });
      up.disabled = i === 0; up.setAttribute('aria-label', 'Поднять урок ' + (i+1)); li.appendChild(up);
      var down = button('↓', function() { var temp = selectedLessons[i+1]; selectedLessons[i+1] = slug; selectedLessons[i] = temp; renderSelected(); });
      down.disabled = i === selectedLessons.length - 1; down.setAttribute('aria-label', 'Опустить урок ' + (i+1)); li.appendChild(down); list.appendChild(li);
    });
  }
  function renderStudentPicker() {
    var box = byId('studentPicker'), search = byId('studentSearch').value.toLocaleLowerCase('ru'); box.replaceChildren();
    var available = students.slice();
    selectedMembers.forEach(function(uid) { if (!available.some(function(s) { return s.id === uid; })) available.push({ id:uid, name:'Профиль ученика недоступен (обратитесь к администратору)', email:'профиль недоступен' }); });
    available.filter(function(s) { return (s.name + ' ' + s.email).toLocaleLowerCase('ru').includes(search); }).forEach(function(s) {
      box.appendChild(checkRow(s.name + ' (' + s.email + ')', selectedMembers.includes(s.id), function(checked) {
        if (checked) selectedMembers.push(s.id); else selectedMembers = selectedMembers.filter(function(uid) { return uid !== s.id; });
        byId('studentCount').textContent = 'Выбрано: ' + selectedMembers.length;
      }));
    });
    byId('studentCount').textContent = 'Выбрано: ' + selectedMembers.length;
  }
  function bind() {
    Object.keys(courseNames).filter(function(course) { return catalog.some(function(l) { return l.course === course; }); }).forEach(function(course) {
      var opt = el('option', courseNames[course]); opt.value = course; byId('lessonCourse').appendChild(opt);
    });
    byId('lessonCourse').addEventListener('change', renderLessonPicker);
    byId('newClass').addEventListener('click', function() { openEditor(null); });
    byId('cancelClass').addEventListener('click', function() { byId('classEditor').hidden = true; });
    byId('lessonSearch').addEventListener('input', renderLessonPicker);
    byId('studentSearch').addEventListener('input', renderStudentPicker);
    byId('classForm').addEventListener('submit', function(e) {
      e.preventDefault(); var err = byId('classFormError'); err.textContent = '';
      var leader = leaders.find(function(l) { return l.id === byId('classLeader').value; });
      if (!leader) { err.textContent = 'Выберите действующего ведущего.'; return; }
      if (!selectedLessons.length) { err.textContent = 'Выберите хотя бы один урок.'; return; }
      if (selectedMembers.length > 200) { err.textContent = 'В классе может быть не больше 200 учеников.'; return; }
      var name = byId('className').value.trim();
      if (!name) { err.textContent = 'Укажите название класса.'; return; }
      var weekday = Number(byId('classDay').value), startDate = byId('classStart').value;
      if (new Date(startDate + 'T12:00:00Z').getUTCDay() !== weekday) { err.textContent = 'День первой встречи должен совпадать с выбранным днём недели.'; return; }
      var data = { name:name, leaderUid:leader.id, leaderName:leader.name || leader.email,
        weekday:weekday, time:byId('classTime').value, duration:Number(byId('classDuration').value), startDate:startDate,
        timezone:'Europe/Moscow', place:byId('classPlace').value.trim(), description:byId('classDescription').value.trim(),
        lessonSlugs:selectedLessons.slice(), memberUids:selectedMembers.slice(), archived:byId('classArchived').checked };
      var submit = byId('classForm').querySelector('[type=submit]'); submit.disabled = true;
      BibleDB.saveClass(editingId, data, originalMembers).then(function(id) {
        byId('classEditor').hidden = true; history.replaceState(null, '', '?id=' + encodeURIComponent(id)); return refresh();
      }).catch(function() { err.textContent = 'Не удалось сохранить класс. Проверьте соединение и права ведущего, затем повторите попытку.'; }).finally(function() { submit.disabled = false; });
    });
  }
})();
