(() => {
  const token = window.NEKO_WEBUI?.token || '';
  const $ = (sel) => document.querySelector(sel);
  const pages = ['dashboard', 'modules', 'superuser', 'mirror', 'logs', 'server', 'about'];
  const titles = {
    dashboard: '仪表盘', modules: '模块', superuser: '超级用户',
    mirror: '页面镜像', logs: '日志', server: '服务', about: '关于',
  };
  let current = 'dashboard';

  function toast(message, isError = false) {
    const el = $('#toast');
    el.textContent = message;
    el.style.borderLeftColor = isError ? '#ff6b6b' : '#7aa2ff';
    el.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.add('hidden'), 3200);
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        'Authorization': `Bearer ${token}`,
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {}),
      },
    });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) {
      throw new Error(data?.error || `HTTP ${response.status}`);
    }
    return data;
  }

  function showPage(name) {
    current = name;
    pages.forEach((page) => {
      $(`#page-${page}`)?.classList.toggle('active', page === name);
    });
    document.querySelectorAll('.nav').forEach((button) => {
      button.classList.toggle('active', button.dataset.page === name);
    });
    $('#page-title').textContent = titles[name] || name;
    if (name === 'dashboard') loadStatus().catch((e) => toast(e.message, true));
    if (name === 'modules') loadModules().catch((e) => toast(e.message, true));
    if (name === 'superuser') loadSuperUser().catch((e) => toast(e.message, true));
    if (name === 'mirror') {
      const frame = $('#mirror-frame');
      if (frame) frame.src = `/screen?token=${encodeURIComponent(token)}`;
    }
    if (name === 'server') loadServer();
    if (name === 'about') loadAbout().catch((e) => toast(e.message, true));
  }

  function setConn(ok) {
    const dot = $('#conn-dot');
    dot.classList.toggle('ok', ok);
    dot.classList.toggle('bad', !ok);
    $('#conn-text').textContent = ok ? '已连接' : '已断开';
  }

  function fmtKmi(version) {
    if (!version) return '—';
    const { major, patchLevel } = version;
    const map = {
      '4.19': 'android10-4.19', '4.14': 'android9-4.14',
      '4.9': 'android8-4.9', '4.4': 'android7-4.4',
      '5.4': 'android11-5.4', '5.10': 'android12-5.10',
      '5.15': 'android13-5.15', '6.1': 'android14-6.1',
      '6.6': 'android15-6.6', '6.12': 'android16-6.12', '6.18': 'android17-6.18',
    };
    return map[`${major}.${patchLevel}`] || `${major}.${patchLevel}.x`;
  }

  async function loadStatus() {
    const data = await api('api/status');
    const k = data.kernel || {};
    $('#st-root').textContent = k.isRootAvailable ? '已获得临时 root' : '未 root';
    $('#st-kmi').textContent = fmtKmi(k.kernelVersion);
    $('#st-kernel').textContent = k.kernelVersion
      ? `${k.kernelVersion.major}.${k.kernelVersion.patchLevel}.${k.kernelVersion.subLevel}`
      : '—';
    $('#st-late').textContent = k.isLateLoadMode ? '是' : '否';
    $('#st-susfs').textContent = k.hookType || '—';
    $('#st-version').textContent = data.version || '—';
    $('#version').textContent = `v${data.version || '?'} (${data.versionCode || '?'})`;
    $('#st-detail').textContent = JSON.stringify(k, null, 2);
    setConn(true);
  }

  async function loadModules() {
    const modules = await api('api/modules');
    const list = $('#module-list');
    $('#module-count').textContent = `(${modules.length})`;
    list.innerHTML = '';
    if (!modules.length) {
      list.innerHTML = '<p class="muted">没有已安装模块。</p>';
      return;
    }
    for (const mod of modules) {
      const item = document.createElement('div');
      item.className = 'item';
      const badges = [];
      if (mod.enabled) badges.push('<span class="badge ok">已启用</span>');
      if (mod.update) badges.push('<span class="badge">有更新</span>');
      if (mod.remove) badges.push('<span class="badge bad">待移除</span>');
      if (mod.metamodule) badges.push('<span class="badge">metamodule</span>');
      if (mod.hasWebUi) badges.push('<span class="badge">WebUI</span>');
      item.innerHTML = `
        <div>
          <div class="name">${escapeHtml(mod.name || mod.id)}${badges.join('')}</div>
          <div class="meta">${escapeHtml(mod.id)} · v${escapeHtml(mod.version || '?')} · ${escapeHtml(mod.author || '未知作者')}</div>
          <div class="meta">${escapeHtml((mod.description || '').slice(0, 200))}</div>
        </div>`;
      const actions = document.createElement('div');
      actions.className = 'actions';
      const btn = (label, action, danger = false) => {
        const b = document.createElement('button');
        b.textContent = label;
        if (danger) b.className = 'danger';
        b.onclick = async () => {
          try {
            await api(`api/modules/${encodeURIComponent(mod.id)}/${action}`, { method: 'POST' });
            toast(`${label} 成功`);
            await loadModules();
          } catch (e) { toast(e.message, true); }
        };
        actions.appendChild(b);
      };
      if (mod.enabled) btn('禁用', 'disable'); else btn('启用', 'enable');
      if (mod.remove) btn('恢复', 'restore'); else btn('移除', 'remove', true);
      item.appendChild(actions);
      list.appendChild(item);
    }
  }

  async function loadSuperUser() {
    const groups = await api('api/superuser');
    const list = $('#superuser-list');
    list.innerHTML = '';
    if (!groups.length) {
      list.innerHTML = '<p class="muted">没有应用申请过 root。</p>';
      return;
    }
    for (const group of groups) {
      const apps = group.apps || [];
      const primary = apps.find((a) => a.packageName === group.primaryPackageName) || apps[0] || {};
      const profile = group.profile || {};
      const item = document.createElement('div');
      item.className = 'item';
      item.innerHTML = `
        <div>
          <div class="name">${escapeHtml(primary.label || group.primaryPackageName)}</div>
          <div class="meta">uid ${group.uid} · ${escapeHtml(group.primaryPackageName)}</div>
          <div class="meta">root: ${profile.rootAllowed ? '允许' : '拒绝'} · umount: ${group.shouldUmount ? '是' : '否'}</div>
        </div>`;
      list.appendChild(item);
    }
  }

  async function loadLogs() {
    const source = $('#log-source').value;
    const data = await api(`api/logs?source=${encodeURIComponent(source)}`);
    $('#log-text').textContent = data.text || '（空）';
  }

  function loadServer() {
    $('#srv-url').textContent = location.origin + '/';
    $('#srv-token').textContent = token;
  }

  async function loadAbout() {
    const data = await api('api/about');
    $('#about-detail').textContent = JSON.stringify(data, null, 2);
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[c]);
  }

  function bind() {
    document.querySelectorAll('.nav').forEach((button) => {
      button.addEventListener('click', () => showPage(button.dataset.page));
    });
    $('#btn-refresh').addEventListener('click', () => {
      showPage(current);
      toast('已刷新');
    });
    $('#btn-log-load').addEventListener('click', () => loadLogs().catch((e) => toast(e.message, true)));
    $('#btn-log-clear').addEventListener('click', () => { $('#log-text').textContent = '（已清空显示）'; });
    $('#btn-copy-url').addEventListener('click', () => copyText(location.origin + '/'));
    $('#btn-copy-token').addEventListener('click', () => copyText(token));
    $('#btn-stop').addEventListener('click', async () => {
      try { await api('api/stop', { method: 'POST' }); toast('服务已停止'); setConn(false); }
      catch (e) { toast(e.message, true); }
    });
    // periodic connectivity probe
    setInterval(async () => {
      try { await api('api/status'); setConn(true); } catch { setConn(false); }
    }, 15000);
  }

  function copyText(text) {
    navigator.clipboard?.writeText(text).then(
      () => toast('已复制'),
      () => toast('复制失败', true),
    );
  }

  bind();
  showPage('dashboard');
})();
