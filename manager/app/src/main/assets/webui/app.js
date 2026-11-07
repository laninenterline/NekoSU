(() => {
  const token = window.NEKO_WEBUI?.token || '';
  const $ = (s) => document.querySelector(s);
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pages = ['home', 'modules', 'superuser', 'logs', 'settings'];
  const titles = { home: '首页', modules: '模块', superuser: '超级用户', logs: '日志中心', settings: '设置' };
  let current = 'home';
  let logsRaw = '';
  let logSource = 'ALL';

  function toast(text, error = false) {
    const el = $('#toast');
    el.textContent = text;
    el.style.borderColor = error ? 'var(--error)' : 'var(--outline-variant)';
    el.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.add('hidden'), 3000);
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        Authorization: 'Bearer ' + token,
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      },
    });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) throw new Error(data?.error || 'HTTP ' + response.status);
    return data;
  }

  function setConn(ok) {
    const chip = $('#conn-chip');
    chip.textContent = ok ? '已连接' : '未连接';
    chip.style.color = ok ? '#2e7d32' : 'var(--error)';
    chip.style.borderColor = ok ? '#2e7d32' : 'var(--error)';
  }

  function navigate(page) {
    current = page;
    document.querySelectorAll('[data-page]').forEach((el) => el.classList.toggle('active', el.dataset.page === page));
    $('#title').textContent = titles[page] || page;
    const load = {
      home: loadHome, modules: loadModules, superuser: loadSuperUser, logs: loadLogs, settings: loadSettings,
    }[page];
    if (load) load().catch((e) => toast(e.message, true));
  }

  function kmiOf(v) {
    if (!v) return '—';
    const map = {
      '4.19': 'android10-4.19', '4.14': 'android9-4.14', '4.9': 'android8-4.9', '4.4': 'android7-4.4',
      '5.4': 'android11-5.4', '5.10': 'android12-5.10', '5.15': 'android13-5.15',
      '6.1': 'android14-6.1', '6.6': 'android15-6.6', '6.12': 'android16-6.12', '6.18': 'android17-6.18',
    };
    return map[`${v.major}.${v.patchLevel}`] || `${v.major}.${v.patchLevel}.x`;
  }

  async function loadHome() {
    const data = await api('api/status');
    const k = data.kernel || {};
    const root = !!k.isRootAvailable;
    $('#home-status').textContent = root ? '已获得临时 root' : '未 root';
    $('#home-badge').textContent = root ? 'ROOT' : '未 root';
    $('#home-hint').textContent = root
      ? '临时 root 已生效；重启后失效，需要重新越狱并 late-load。'
      : '请先通过越狱入口（Magica）或手动 ksud late-load 获取临时 root。';
    $('#st-kmi').textContent = kmiOf(k.kernelVersion);
    $('#st-kernel').textContent = k.kernelVersion ? `${k.kernelVersion.major}.${k.kernelVersion.patchLevel}.${k.kernelVersion.subLevel}` : '—';
    $('#st-late').textContent = k.isLateLoadMode ? '是' : '否';
    $('#st-hook').textContent = k.hookType || '—';
    $('#st-version').textContent = 'v' + (data.version || '?') + ' (' + (data.versionCode || '?') + ')';
    $('#home-detail').textContent = JSON.stringify(k, null, 2);
    setConn(true);
  }

  async function loadModules() {
    const modules = await api('api/modules');
    $('#module-count').textContent = modules.length + ' 个';
    const list = $('#module-list');
    list.innerHTML = '';
    if (!modules.length) { list.innerHTML = '<p class="muted">没有已安装模块。</p>'; return; }
    for (const mod of modules) {
      const badges = [];
      if (mod.enabled) badges.push('<span class="badge ok">已启用</span>');
      if (mod.update) badges.push('<span class="badge warn">有更新</span>');
      if (mod.remove) badges.push('<span class="badge">待移除</span>');
      if (mod.metamodule) badges.push('<span class="badge">metamodule</span>');
      const item = document.createElement('div');
      item.className = 'item';
      item.innerHTML = `
        <div class="item-main">
          <div class="item-title">${esc(mod.name || mod.id)} ${badges.join('')}</div>
          <div class="item-sub">${esc(mod.id)} · v${esc(mod.version || '?')} · ${esc(mod.author || '未知作者')}</div>
          <div class="item-sub">${esc((mod.description || '').slice(0, 240))}</div>
        </div>`;
      const actions = document.createElement('div');
      actions.className = 'item-actions';
      const mk = (label, action, danger = false) => {
        const b = document.createElement('button');
        b.textContent = label;
        b.className = danger ? 'danger' : 'ghost';
        b.onclick = async () => {
          try { await api(`api/modules/${encodeURIComponent(mod.id)}/${action}`, { method: 'POST' }); toast(label + '成功'); await loadModules(); }
          catch (e) { toast(e.message, true); }
        };
        actions.appendChild(b);
      };
      if (mod.enabled) mk('禁用', 'disable'); else mk('启用', 'enable');
      if (mod.remove) mk('恢复', 'restore'); else mk('移除', 'remove', true);
      item.appendChild(actions);
      list.appendChild(item);
    }
  }

  async function loadSuperUser() {
    const groups = await api('api/superuser');
    $('#su-count').textContent = groups.length + ' 个';
    const list = $('#superuser-list');
    list.innerHTML = '';
    if (!groups.length) { list.innerHTML = '<p class="muted">没有应用申请过 root。</p>'; return; }
    for (const group of groups) {
      const apps = group.apps || [];
      const primary = apps.find((a) => a.packageName === group.primaryPackageName) || apps[0] || {};
      const profile = group.profile || {};
      const allowed = !!(profile.rootAllowed ?? profile.allowRoot);
      const item = document.createElement('div');
      item.className = 'item';
      item.innerHTML = `
        <div class="item-main">
          <div class="item-title">${esc(primary.label || group.primaryPackageName)} <span class="badge ${allowed ? 'ok' : ''}">${allowed ? '已授权' : '未授权'}</span></div>
          <div class="item-sub">uid ${group.uid} · ${esc(group.primaryPackageName)}</div>
          <div class="item-sub">umount: ${group.shouldUmount ? '是' : '否'}</div>
        </div>`;
      list.appendChild(item);
    }
  }

  async function loadLogs() {
    const data = await api(`api/logs?source=${encodeURIComponent(logSource)}`);
    logsRaw = data.text || '';
    renderLogs();
  }

  function renderLogs() {
    const query = ($('#log-search')?.value || '').trim().toLowerCase();
    const lines = logsRaw.split('\n');
    const filtered = query ? lines.filter((line) => line.toLowerCase().includes(query)) : lines;
    $('#log-text').textContent = filtered.length ? filtered.join('\n') : '（无日志）';
  }

  async function loadSettings() {
    const data = await api('api/settings');
    $('#set-enabled').checked = !!data.enabled;
    $('#set-random').checked = !!data.randomPort;
    $('#set-url').textContent = data.boundPort > 0 ? `http://127.0.0.1:${data.boundPort}/` : '（未运行）';
    $('#set-token').textContent = token;
    $('#about-version').textContent = `v${data.version || '?'} (${data.versionCode || '?'})`;
    setConn(true);
  }

  async function patchWebUi(patch) {
    const state = await api('api/settings/webui', { method: 'POST', body: JSON.stringify(patch) });
    return state;
  }

  function bind() {
    document.querySelectorAll('[data-page]').forEach((el) => el.addEventListener('click', () => navigate(el.dataset.page)));
    $('#btn-refresh').addEventListener('click', () => navigate(current));
    $('#btn-modules-refresh').addEventListener('click', () => loadModules().catch((e) => toast(e.message, true)));

    $('#set-enabled').addEventListener('change', async (e) => {
      try { await patchWebUi({ enabled: e.target.checked }); toast('已更新'); setTimeout(() => loadSettings().catch(() => {}), 800); }
      catch (err) { toast(err.message, true); }
    });
    $('#set-random').addEventListener('change', async (e) => {
      try { await patchWebUi({ randomPort: e.target.checked }); toast('已更新'); setTimeout(() => loadSettings().catch(() => {}), 1200); }
      catch (err) { toast(err.message, true); }
    });
    const copy = (text) => navigator.clipboard?.writeText(text).then(() => toast('已复制'), () => toast('复制失败', true));
    $('#btn-copy-url').addEventListener('click', () => copy($('#set-url').textContent));
    $('#btn-copy-token').addEventListener('click', () => copy(token));

    document.querySelectorAll('#log-sources .chip').forEach((chip) => chip.addEventListener('click', () => {
      document.querySelectorAll('#log-sources .chip').forEach((c) => c.classList.remove('active'));
      chip.classList.add('active');
      logSource = chip.dataset.source;
      loadLogs().catch((e) => toast(e.message, true));
    }));
    $('#log-search').addEventListener('input', renderLogs);
    $('#btn-log-load').addEventListener('click', () => loadLogs().catch((e) => toast(e.message, true)));
    $('#btn-log-download').addEventListener('click', () => {
      const blob = new Blob([logsRaw], { type: 'text/plain' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `nekosu-${logSource.toLowerCase()}.log`;
      a.click();
      URL.revokeObjectURL(a.href);
    });

    const savedTheme = localStorage.getItem('neko-theme');
    if (savedTheme) document.documentElement.dataset.theme = savedTheme;

    setInterval(async () => {
      try { await api('api/status'); setConn(true); } catch { setConn(false); }
    }, 15000);
  }

  bind();
  navigate('home');
})();
