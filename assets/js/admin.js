(function() {
  'use strict';

  BibleAuth.onReady(function(user, profile) {

    if (!user || !profile || profile.role !== 'admin') {
      document.querySelector('.admin-page .container').innerHTML =
        '<div style="text-align:center;padding:80px 20px;">' +
        '<h2>Доступ запрещён</h2>' +
        '<p>Эта страница доступна только администраторам.</p>' +
        '<a href="/login/" class="btn btn-primary" style="margin-top:16px;">Войти</a></div>';
      return;
    }

    initAdmin();
  });

  function initAdmin() {
    function showError(err) {
      var el = document.getElementById('adminError');
      el.textContent = 'Не удалось выполнить операцию. Проверьте соединение и права доступа, затем повторите попытку.';
      el.classList.add('visible');
    }
    // === Tabs ===
    var tabs = document.querySelectorAll('.admin-tab');
    var panels = document.querySelectorAll('.admin-panel');

    tabs.forEach(function(tab) {
      tab.addEventListener('click', function() {
        tabs.forEach(function(t) { t.classList.remove('active'); });
        panels.forEach(function(p) { p.classList.remove('active'); });
        tab.classList.add('active');
        document.getElementById('panel-' + tab.getAttribute('data-tab')).classList.add('active');
      });
    });

    // === Users ===
    var allUsers = [];

    function loadUsers() {
      return BibleDB.getAllUsers().then(function(users) {
        allUsers = users;
        renderUsers();
        renderProgressSelect();
        renderLessonStats();
        renderLeaderTree();
      });
    }

    function renderUsers() {
      var tbody = document.getElementById('usersTableBody');
      tbody.innerHTML = '';

      allUsers.forEach(function(u) {
        var tr = document.createElement('tr');
        tr.innerHTML =
          '<td>' + escHtml(u.name) + '</td>' +
          '<td>' + escHtml(u.email) + '</td>' +
          '<td>' + (u.role === 'admin' ? 'Админ' : u.role === 'leader' ? 'Ведущий' : 'Ученик') + '</td>' +
          '<td>' +
            '<button class="btn btn-outline btn-sm edit-user" data-uid="' + u.uid + '">Изменить</button> ' +
            '<button class="btn btn-outline btn-sm reset-user" data-uid="' + u.uid + '">Сбросить пароль</button>' +
          '</td>';
        tbody.appendChild(tr);
      });

      tbody.querySelectorAll('.edit-user').forEach(function(btn) {
        btn.addEventListener('click', function() {
          var uid = btn.getAttribute('data-uid');
          var u = allUsers.find(function(x) { return x.uid === uid; });
          if (u) openEditModal(u);
        });
      });

      tbody.querySelectorAll('.reset-user').forEach(function(btn) {
        btn.addEventListener('click', function() {
          var u = allUsers.find(function(x) { return x.uid === btn.getAttribute('data-uid'); });
          if (u) openRecovery('', u.email, u.name);
        });
      });
    }

    var recoveryModal = document.getElementById('recoveryModal');
    var recoveryStatus = document.getElementById('recoveryStatus');
    var restoreBtn = document.getElementById('restoreProfileBtn');
    function openRecovery(uid, email, name) {
      document.getElementById('recoveryForm').reset();
      document.getElementById('recoveryUid').value = uid || '';
      document.getElementById('recoveryEmail').value = email || '';
      document.getElementById('recoveryName').value = name || '';
      document.getElementById('restoreFields').hidden = !uid;
      restoreBtn.hidden = !uid;
      recoveryStatus.textContent = uid ? 'Email должен совпадать с прежним аккаунтом этого ученика.' : 'После смены пароля и входа удалённый профиль восстановится автоматически.';
      recoveryModal.classList.add('active');
    }
    document.getElementById('recoverUserBtn').addEventListener('click', function() { openRecovery('','',''); });
    document.getElementById('cancelRecovery').addEventListener('click', function() { recoveryModal.classList.remove('active'); });
    document.getElementById('recoveryForm').addEventListener('submit', function(e) {
      e.preventDefault();
      var button = this.querySelector('[type=submit]'); button.disabled = true;
      recoveryStatus.textContent = 'Отправляем письмо…';
      BibleDB.resetPassword(document.getElementById('recoveryEmail').value.trim()).then(function() {
        recoveryStatus.textContent = 'Запрос принят. Если для этого email есть аккаунт, придёт письмо для смены пароля. Проверьте также «Спам».';
      }).catch(function() { recoveryStatus.textContent = 'Не удалось отправить письмо. Проверьте email и соединение, затем повторите.'; }).finally(function() { button.disabled = false; });
    });
    restoreBtn.addEventListener('click', function() {
      var emailInput = document.getElementById('recoveryEmail');
      var name = document.getElementById('recoveryName').value.trim();
      if (!emailInput.reportValidity() || !name) { recoveryStatus.textContent = 'Укажите email и имя ученика.'; return; }
      var uid = document.getElementById('recoveryUid').value, email = emailInput.value.trim();
      if (allUsers.some(function(u) { return u.uid !== uid && (u.email || '').toLowerCase() === email.toLowerCase(); })) { recoveryStatus.textContent = 'С этим email уже есть другой профиль. Проверьте данные.'; return; }
      restoreBtn.disabled = true;
      BibleDB.restoreUserProfile(uid, { name:name, email:email }).then(function() {
        recoveryStatus.textContent = 'Профиль восстановлен. Классы и ответы сохранены. При необходимости отправьте письмо для смены пароля.';
        restoreBtn.hidden = true;
        return loadUsers();
      }).catch(function(err) { recoveryStatus.textContent = 'Не удалось восстановить профиль. Обновите список и повторите попытку.'; }).finally(function() { restoreBtn.disabled = false; });
    });

    // Modal
    var modal = document.getElementById('userModal');
    var form = document.getElementById('userForm');
    var pendingCreateUid = null;

    document.getElementById('addUserBtn').addEventListener('click', function() {
      document.getElementById('modalTitle').textContent = 'Добавить пользователя';
      form.reset(); pendingCreateUid = null;
      document.getElementById('editUid').value = '';
      document.getElementById('userFormError').classList.remove('visible');
      document.getElementById('uEmail').removeAttribute('disabled');
      document.getElementById('uPassword').closest('.form-group').style.display = '';
      modal.classList.add('active');
    });

    document.getElementById('cancelModal').addEventListener('click', function() {
      modal.classList.remove('active');
    });

    modal.addEventListener('click', function(e) {
      if (e.target === modal) modal.classList.remove('active');
    });

    function openEditModal(u) {
      document.getElementById('modalTitle').textContent = 'Редактировать пользователя';
      document.getElementById('editUid').value = u.uid;
      document.getElementById('uName').value = u.name;
      document.getElementById('uEmail').value = u.email;
      document.getElementById('uEmail').setAttribute('disabled', 'disabled');
      document.getElementById('uPassword').value = '';
      document.getElementById('uPassword').closest('.form-group').style.display = 'none';
      document.getElementById('uRole').value = u.role;
      modal.classList.add('active');
    }

    form.addEventListener('submit', function(e) {
      e.preventDefault();
      var uid = document.getElementById('editUid').value;
      var errorEl = document.getElementById('userFormError');
      errorEl.classList.remove('visible');

      var name = document.getElementById('uName').value.trim();
      var email = document.getElementById('uEmail').value.trim();
      var password = document.getElementById('uPassword').value;
      var role = document.getElementById('uRole').value;

      if (!name || !email) {
        errorEl.textContent = 'Заполните имя и email';
        errorEl.classList.add('visible');
        return;
      }

      var submitBtn = form.querySelector('button[type="submit"]');
      submitBtn.disabled = true;

      if (uid) {
        // Edit existing
        BibleDB.setUserProfile(uid, { name: name, role: role }).then(function() {
          modal.classList.remove('active');
          submitBtn.disabled = false;
          loadUsers().catch(showError);
        }).catch(function() {
          errorEl.textContent = 'Не удалось сохранить изменения. Повторите попытку.';
          errorEl.classList.add('visible');
          submitBtn.disabled = false;
        });
      } else {
        // Create new
        if (!password || password.length < 6) {
          errorEl.textContent = 'Пароль должен быть не менее 6 символов';
          errorEl.classList.add('visible');
          submitBtn.disabled = false;
          return;
        }

        (pendingCreateUid ? Promise.resolve({user:{uid:pendingCreateUid}}) : BibleDB.createUser(email, password)).then(function(cred) {
          pendingCreateUid = cred.user.uid;
          document.getElementById('uEmail').disabled = true;
          return BibleDB.setUserProfile(cred.user.uid, {
            name: name,
            email: email,
            role: role,
            created: new Date().toISOString()
          });
        }).then(function() {
          modal.classList.remove('active');
          submitBtn.disabled = false;
          loadUsers().catch(showError);
        }).catch(function(err) {
          var msg = pendingCreateUid ? 'Аккаунт входа создан, но профиль не сохранён. Нажмите «Сохранить» ещё раз, не закрывая окно.' : 'Ошибка создания';
          if (err.code === 'auth/email-already-in-use') { msg = 'Аккаунт с этим email уже существует. Не создавайте его повторно: используйте «Восстановить доступ / профиль» или «Забыли пароль?» на странице входа.'; }
          if (err.code === 'auth/invalid-email') msg = 'Некорректный email';
          errorEl.textContent = msg;
          errorEl.classList.add('visible');
          submitBtn.disabled = false;
        });
      }
    });

    // === Progress ===
    // Dynamic: read lesson slugs from the lessons table on the page
    var LESSON_SLUGS = [];
    var LESSON_TITLES = {};
    document.querySelectorAll('.lesson-answer-count').forEach(function(cell) {
      var slug = cell.getAttribute('data-slug');
      LESSON_SLUGS.push(slug);
      var row = cell.closest('tr');
      if (row) {
        var title = row.cells[1] ? row.cells[1].textContent.trim() : slug;
        LESSON_TITLES[slug] = title;
      }
    });

    var allAnswersCache = null;

    function renderProgressSelect() {
      var select = document.getElementById('progressUser');
      select.innerHTML = '<option value="">— выберите —</option>';
      allUsers.forEach(function(u) {
        if (u.role === 'user') {
          var opt = document.createElement('option');
          opt.value = u.uid;
          opt.textContent = u.name + ' (' + u.email + ')';
          select.appendChild(opt);
        }
      });
    }

    document.getElementById('progressUser').addEventListener('change', function() {
      var uid = this.value;
      var content = document.getElementById('progressContent');
      if (!uid) { content.innerHTML = ''; return; }

      BibleDB.getUserAnswers(uid).then(function(answers) {
        if (Object.keys(answers).length === 0) {
          content.innerHTML = '<p style="color:var(--text-light);padding:20px 0;">Этот ученик ещё не отвечал на вопросы.</p>';
          return;
        }

        var html = '';
        LESSON_SLUGS.forEach(function(slug) {
          var lessonAnswers = answers[slug];
          if (!lessonAnswers) return;

          html += '<div style="margin-top:24px;">';
          html += '<h3>' + (LESSON_TITLES[slug] || slug) + '</h3>';
          if (lessonAnswers._savedAt) {
            html += '<p style="font-size:0.85rem;color:var(--text-light);">Сохранено: ' +
              new Date(lessonAnswers._savedAt).toLocaleString('ru') + '</p>';
          }
          html += '<ul class="answers-list">';
          Object.keys(lessonAnswers).forEach(function(key) {
            if (key.startsWith('q')) {
              html += '<li><strong>Вопрос ' + key.replace('q', '') + ':</strong><p>' +
                escHtml(lessonAnswers[key]) + '</p></li>';
            }
          });
          html += '</ul></div>';
        });

        if (document.getElementById('progressUser').value === uid) content.innerHTML = html;
      }).catch(showError);
    });

    // === Lessons stats ===
    function renderLessonStats() {
      BibleDB.getAllAnswers().then(function(allAnswers) {
        var cells = document.querySelectorAll('.lesson-answer-count');
        cells.forEach(function(cell) {
          var slug = cell.getAttribute('data-slug');
          var count = 0;
          Object.keys(allAnswers).forEach(function(uid) {
            if (allAnswers[uid][slug]) count++;
          });
          cell.textContent = count;
        });
      }).catch(showError);
    }

    function treeNode(tag, text, cls) {
      var node = document.createElement(tag); if (text != null) node.textContent = text; if (cls) node.className = cls; return node;
    }
    function renderLeaderTree() {
      var root = document.getElementById('leaderTree'); root.textContent = 'Загрузка классов…';
      return BibleDB.getClasses(BibleDB.getCurrentUser(), { role:'admin' }).then(function(classes) {
        root.replaceChildren();
        var leaders = allUsers.filter(function(u) { return u.role === 'leader' || classes.some(function(c) { return c.leaderUid === u.uid; }); });
        classes.forEach(function(c) { if (!leaders.some(function(u) { return u.uid === c.leaderUid; })) leaders.push({uid:c.leaderUid,name:c.leaderName,role:'missing'}); });
        if (!leaders.length) { root.textContent = 'Ведущих пока нет. Назначьте роль «Ведущий» в карточке пользователя.'; return; }
        var list = treeNode('ul', null, 'leader-tree'); root.appendChild(list);
        leaders.sort(function(a,b) { return (a.name || '').localeCompare(b.name || '', 'ru'); }).forEach(function(leader) {
          var owned = classes.filter(function(c) { return c.leaderUid === leader.uid; });
          var item = treeNode('li'), details = treeNode('details'); details.open = true;
          var summary = treeNode('summary', leader.name || leader.email || leader.uid);
          summary.appendChild(treeNode('span', ' · классов: ' + owned.length + (leader.role === 'user' || leader.role === 'missing' ? ' · требуется назначить ведущего' : ''), 'tree-meta'));
          details.appendChild(summary);
          var children = treeNode('ul');
          if (!owned.length) children.appendChild(treeNode('li', 'Классов пока нет', 'tree-meta'));
          owned.sort(function(a,b) { return a.name.localeCompare(b.name,'ru'); }).forEach(function(c) {
            var row = treeNode('li'), branch = treeNode('details'), title = treeNode('summary', c.name);
            title.appendChild(treeNode('span', ' · учеников: ' + c.memberUids.length + (c.archived ? ' · Архив' : ''), 'tree-meta')); branch.appendChild(title);
            var meta = treeNode('p', ['Вс','Пн','Вт','Ср','Чт','Пт','Сб'][c.weekday] + ', ' + c.time + ' МСК · ' + 'уроков: ' + c.lessonSlugs.length, 'tree-meta'); branch.appendChild(meta);
            var link = treeNode('a','Открыть класс →'); link.href = '/classes/?id=' + encodeURIComponent(c.id); branch.appendChild(link);
            var members = treeNode('ul');
            if (!c.memberUids.length) members.appendChild(treeNode('li','Ученики пока не добавлены','tree-meta'));
            c.memberUids.forEach(function(uid) {
              var student = allUsers.find(function(u) { return u.uid === uid; });
              var member = treeNode('li',student ? student.name + ' · ' + student.email : 'Профиль ученика удалён. ');
              if (!student) {
                var restore = treeNode('button','Восстановить профиль','btn btn-outline btn-sm'); restore.type = 'button';
                restore.addEventListener('click',function() { openRecovery(uid,'',''); }); member.appendChild(restore);
              }
              members.appendChild(member);
            });
            branch.appendChild(members); row.appendChild(branch); children.appendChild(row);
          });
          details.appendChild(children); item.appendChild(details); list.appendChild(item);
        });
      }).catch(function(err) { root.textContent = 'Не удалось загрузить дерево. Нажмите «Обновить», чтобы повторить.'; });
    }
    document.getElementById('refreshLeaderTree').addEventListener('click', function() { loadUsers().catch(showError); });

    // Load data
    loadUsers().catch(showError);
  }

  function escHtml(str) {
    var div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
})();
