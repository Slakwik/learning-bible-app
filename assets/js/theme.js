(function () {
  'use strict';

  var THEMES = {
    original: { name: 'Классика', desc: 'Тёмно-синий с золотым акцентом — спокойный и надёжный.' },
    papyrus:  { name: 'Папирус',  desc: 'Журнальная типографика, кремовая бумага, винные акценты.' },
    hearth:   { name: 'Очаг',     desc: 'Тёплая палитра, мягкие карточки, неспешное чтение.' },
    covenant: { name: 'Завет',    desc: 'Тёмная тема с янтарными акцентами — современно и смело.' }
  };

  function getDefaultTheme() {
    var defaultTheme = document.documentElement.dataset.defaultTheme || 'original';
    return defaultTheme;
  }

  function getCurrentTheme() {
    return document.documentElement.getAttribute('data-theme') || 'original';
  }

  function applyTheme(name) {
    if (!THEMES[name]) name = 'original';
    document.documentElement.setAttribute('data-theme', name);
    try { localStorage.setItem('bible-theme', name); } catch (e) {}
  }

  function clearLocalTheme() {
    try { localStorage.removeItem('bible-theme'); } catch (e) {}
  }

  function saveUserTheme(uid, name) {
    if (!window.BibleDB || !uid) return Promise.resolve();
    return BibleDB.setUserProfile(uid, { theme: name });
  }

  // Когда профиль пользователя загрузится, применим его тему,
  // если она отличается от уже применённой.
  window.addEventListener('bible-auth-ready', function (e) {
    var profile = e.detail && e.detail.profile;
    if (profile && profile.theme && profile.theme !== getCurrentTheme()) {
      applyTheme(profile.theme);
    } else if (!e.detail.user) {
      // Неавторизованный пользователь — сбросить локальный выбор
      // и вернуться к default. Если хочется сохранять выбор для гостей —
      // достаточно убрать строку clearLocalTheme().
      // По текущим требованиям: гости видят тему из _config.yml.
    }
  });

  window.BibleTheme = {
    THEMES: THEMES,
    list: function () { return Object.keys(THEMES); },
    get: getCurrentTheme,
    apply: applyTheme,
    save: saveUserTheme,
    clearLocal: clearLocalTheme
  };
})();
