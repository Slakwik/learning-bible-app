(function() {
  'use strict';
  var currentProfile = null, authResolved = false, resolvedUser = null, authSequence = 0;
  var accountIcon = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></svg>';
  function escHtml(str) { var d = document.createElement('span'); d.textContent = str || ''; return d.innerHTML; }
  function renderAuthNav(user, profile) {
    var nav = document.getElementById('authNav'); if (!nav) return;
    if (!user) {
      nav.innerHTML = '<a href="/login/" class="profile-link" aria-label="Войти">' + accountIcon + '<span class="profile-label">Войти</span></a>';
      return;
    }
    var name = profile && profile.name || user.email || 'Мой аккаунт';
    var role = profile ? {admin:'Администратор',leader:'Ведущий',user:'Ученик'}[profile.role] || 'Ученик' : 'Загрузка профиля…';
    nav.innerHTML = '<a href="/profile/" class="profile-link" aria-label="Профиль">' + accountIcon + '<span class="profile-label">Профиль</span></a>' +
      '<details class="account-menu"><summary aria-label="Меню аккаунта"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></summary>' +
      '<div class="account-dropdown"><div class="account-identity"><strong>' + escHtml(name) + '</strong><span>' + escHtml(role) + '</span></div>' +
      '<a href="/profile/">Настройки профиля</a><a href="/classes/">Мои классы</a><a href="/my-answers/">Личные ответы</a>' +
      (profile && profile.role === 'admin' ? '<a href="/admin/">Администрирование</a>' : '') +
      '<button type="button" id="logoutBtn">Выйти из аккаунта</button></div></details>';
    document.getElementById('logoutBtn').addEventListener('click', function() {
      var btn = this; btn.disabled = true;
      BibleDB.logout().then(function() { window.location.href = '/'; }).catch(function() { btn.disabled = false; showAuthError('Не удалось выйти. Проверьте соединение и повторите попытку.'); });
    });
  }
  function showAuthError(text, retry) {
    var message = document.getElementById('authError');
    if (!message) { message = document.createElement('div'); message.id = 'authError'; message.className = 'auth-notice'; message.setAttribute('role','alert'); document.querySelector('main').prepend(message); }
    message.replaceChildren(document.createTextNode(text));
    if (retry) { var btn = document.createElement('button'); btn.type = 'button'; btn.className = 'btn btn-outline btn-sm'; btn.textContent = 'Повторить'; btn.addEventListener('click', retry); message.appendChild(btn); }
  }
  function clearAuthError() { var n = document.getElementById('authError'); if (n) n.remove(); }
  function initUI() {
    var toggle = document.getElementById('navToggle'), nav = document.getElementById('mainNav');
    if (toggle && nav) {
      function closeMenu() { nav.classList.remove('open'); toggle.setAttribute('aria-expanded','false'); toggle.setAttribute('aria-label','Открыть меню'); }
      toggle.addEventListener('click',function() { var open = nav.classList.toggle('open'); toggle.setAttribute('aria-expanded',String(open)); toggle.setAttribute('aria-label',open ? 'Закрыть меню' : 'Открыть меню'); });
      nav.addEventListener('click',function(e) { if (e.target.closest('a')) closeMenu(); });
      document.addEventListener('click',function(e) { if (!nav.contains(e.target) && !toggle.contains(e.target)) closeMenu(); var menu = document.querySelector('.account-menu'); if (menu && !menu.contains(e.target)) menu.open = false; });
      document.addEventListener('keydown',function(e) { if (e.key === 'Escape') { closeMenu(); var menu = document.querySelector('.account-menu'); if (menu && menu.open) { menu.open = false; menu.querySelector('summary').focus(); } } });
      nav.querySelectorAll('a').forEach(function(a) { if (window.location.pathname.startsWith(new URL(a.href).pathname)) a.setAttribute('aria-current','page'); });
    }
    initLoginForm();
    if (!window.BibleDB) showAuthError('Сервис входа не загрузился. Проверьте подключение и обновите страницу.',function() { window.location.reload(); });
  }
  function initLoginForm() {
    var form = document.getElementById('loginForm'); if (!form) return;
    var forgot = document.getElementById('forgotPassword');
    if (forgot) forgot.addEventListener('click',function() {
      var email = document.getElementById('username'), status = document.getElementById('passwordResetStatus');
      if (!email.reportValidity()) return;
      if (!window.BibleDB) { status.textContent = 'Сервис входа не загрузился. Обновите страницу.'; return; }
      forgot.disabled = true; status.textContent = 'Отправляем письмо…';
      BibleDB.resetPassword(email.value.trim()).then(function() {
        status.textContent = 'Если для этого email есть аккаунт, придёт письмо для смены пароля. Проверьте также «Спам».';
      }).catch(function() { status.textContent = 'Не удалось отправить письмо. Проверьте соединение и повторите позже.'; }).finally(function() { forgot.disabled = false; });
    });
    form.addEventListener('submit',function(e) {
      e.preventDefault();
      var error = document.getElementById('loginError'), submit = form.querySelector('[type=submit]');
      if (!window.BibleDB) { error.textContent = 'Сервис входа не загрузился. Обновите страницу.'; error.classList.add('visible'); return; }
      error.classList.remove('visible'); submit.disabled = true; submit.textContent = 'Вход…';
      var timer = setTimeout(function() { error.textContent = 'Сервис входа отвечает дольше обычного. Проверьте подключение.'; error.classList.add('visible'); submit.disabled = false; submit.textContent = 'Войти'; },15000);
      BibleDB.login(document.getElementById('username').value.trim(),document.getElementById('password').value).then(function() {
        clearTimeout(timer); window.location.href = '/classes/';
      }).catch(function(err) {
        clearTimeout(timer); error.textContent = err.code === 'auth/network-request-failed' ? 'Нет связи с сервисом входа. Проверьте подключение.' : err.code === 'auth/too-many-requests' ? 'Слишком много попыток. Попробуйте позже.' : 'Неверный email или пароль';
        error.classList.add('visible'); submit.disabled = false; submit.textContent = 'Войти';
      });
    });
  }
  function publishAuth(user, profile) {
    resolvedUser = user; currentProfile = profile; authResolved = true;
    renderAuthNav(user, profile);
    window.dispatchEvent(new CustomEvent('bible-auth-ready',{detail:{user:user,profile:profile}}));
  }
  function loadProfile(user) {
    var sequence = ++authSequence;
    clearAuthError();
    function stillCurrent() { var active = BibleDB.getCurrentUser(); return sequence === authSequence && active && active.uid === user.uid; }
    var timer = setTimeout(function() {
      if (stillCurrent()) showAuthError('Профиль загружается дольше обычного. Кнопка личного кабинета остаётся доступной.',function() { loadProfile(user); });
    },12000);
    BibleDB.getUserProfile(user.uid).then(function(profile) {
      if (!stillCurrent()) return null;
      if (profile) return profile;
      profile = {name:user.email,role:'user',email:user.email};
      return BibleDB.setUserProfile(user.uid,profile).then(function() { return profile; });
    }).then(function(profile) {
      clearTimeout(timer); if (!stillCurrent() || !profile) return;
      profile.uid = user.uid; clearAuthError(); publishAuth(user,profile);
    }).catch(function(err) {
      clearTimeout(timer); if (!stillCurrent()) return;
      currentProfile = null;
      showAuthError('Не удалось загрузить профиль. Вы по-прежнему вошли в аккаунт.',function() { loadProfile(user); });
      window.dispatchEvent(new CustomEvent('bible-auth-error',{detail:{error:err}}));
    });
  }
  window.BibleAuth = {
    getProfile:function() { return currentProfile; },
    onReady:function(cb) { window.addEventListener('bible-auth-ready',function(e) { cb(e.detail.user,e.detail.profile); }); if (authResolved) cb(resolvedUser,currentProfile); }
  };
  if (window.BibleDB) BibleDB.onAuthChanged(function(user) {
    authResolved = false; currentProfile = null; resolvedUser = user; ++authSequence;
    renderAuthNav(user,null); // Navigation must never wait for Firestore.
    if (!user) { clearAuthError(); publishAuth(null,null); return; }
    loadProfile(user);
  });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',initUI,{once:true}); else initUI();
})();
