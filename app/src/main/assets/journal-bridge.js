(function () {
  if (window.__tjCaptureInstalled) return true;
  window.__tjCaptureInstalled = true;
  let epoch = 0;
  let auth = '';
  let base = 'https://msapi.top-academy.ru/api/v2';
  let account = '';
  let language = '';
  let withCredentials = false;
  let recent = [];
  let verifiedAuth = '';
  let sessionState = 'pending';
  let probeFor = '';
  let probeRunning = false;
  let probeAttempts = 0;
  let nextProbeAt = 0;
  let responses = {};
  let jobs = {};
  let fileJobs = {};
  let evaluationJobs = {};
  const submittedEvaluations = new Set();
  const inFlight = new Map();
  const controllers = new Set();
  const wanted = u => ['/dashboard/', '/schedule/', '/progress/', '/homework/', '/count/homework', '/settings/user-info',
    '/library/operations/list', '/news/operations/', '/profile/statistic/student-achievements', '/reviews/index/list',
    '/feedback/students/evaluate-lesson-list', '/public/translations']
    .some(part => String(u || '').includes(part));
  const apiUrl = u => {
    try {
      const url = new URL(u, location.href);
      return url.protocol === 'https:' && ['msapi.top-academy.ru', 'journal.top-academy.ru'].includes(url.hostname)
        && url.pathname.startsWith('/api/v2/') ? url : null;
    } catch (_) { return null; }
  };
  const academyUrl = u => {
    try {
      const url = new URL(u, location.href);
      return url.protocol === 'https:' && (url.hostname === 'top-academy.ru' || url.hostname.endsWith('.top-academy.ru'))
        ? url : null;
    } catch (_) { return null; }
  };
  const observeAuth = value => {
    value = String(value || '').trim();
    if (/^Bearer(?:\s+(?:undefined|null)?)?$/i.test(value)) value = '';
    if (value !== auth) {
      auth = value;
      verifiedAuth = '';
      sessionState = 'pending';
      probeFor = '';
      probeAttempts = 0;
      nextProbeAt = 0;
    }
  };
  const observeSession = (url, status, data, version, credential) => {
    if (version !== epoch || !credential || credential !== auth || !apiUrl(url)) return;
    if (status === 401) { verifiedAuth = ''; sessionState = 'expired'; probeFor = credential; }
    if (new URL(url, location.href).pathname === '/api/v2/settings/user-info') {
      const profile = data?.data || data;
      if (status === 200 && profile && profile.student_id != null && typeof profile.full_name === 'string') {
        verifiedAuth = credential;
        sessionState = 'verified';
      } else if (status === 403) {
        verifiedAuth = '';
        sessionState = 'forbidden';
        probeFor = credential;
      }
    }
  };
  const remember = (u, headers) => {
    const url = apiUrl(u);
    if (!url) return;
    base = url.origin + '/api/v2';
    try {
      const values = new Headers(headers || {});
      const value = values.get('authorization'); if (value) observeAuth(value);
      const locale = values.get('accept-language'); if (locale) language = locale;
    } catch (_) {}
  };
  const save = (u, data, version) => {
    if (version !== epoch || !apiUrl(u) || !wanted(u) || data == null) return;
    if (typeof data !== 'object' || data.error || data.success === false) return;
    responses[u] = {time: Date.now(), data};
    const keys = Object.keys(responses);
    if (keys.length > 80) keys.sort((a, b) => responses[a].time - responses[b].time)
      .slice(0, keys.length - 60).forEach(k => delete responses[k]);
  };
  window.__tjReset = function () {
    epoch++;
    controllers.forEach(c => c.abort());
    controllers.clear();
    inFlight.clear();
    responses = {};
    jobs = {};
    fileJobs = {};
    evaluationJobs = {};
    submittedEvaluations.clear();
    auth = '';
    verifiedAuth = '';
    sessionState = 'pending';
    probeFor = '';
    probeRunning = false;
    probeAttempts = 0;
    nextProbeAt = 0;
    account = '';
    language = '';
    withCredentials = false;
    recent = [];
    delete window.__tjProfileName;
  };
  window.__tjAccount = function (name) {
    if (!name) return;
    if (account && account !== name) {
      // Preserve only the current session's credential, never expose it to Android.
      const currentAuth = auth;
      window.__tjReset();
      auth = currentAuth;
    }
    account = name;
  };
  window.__tjEnterLogin = () => {
    // A login request may already have supplied a fresh credential before Android sees
    // the route change. Discard account data, then verify that credential after login.
    const credential = auth;
    window.__tjReset();
    auth = credential;
    return true;
  };
  window.__tjResponses = () => responses;
  const open = XMLHttpRequest.prototype.open;
  const send = XMLHttpRequest.prototype.send;
  const setHeader = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__tjUrl = String(url || '');
    this.__tjEpoch = epoch;
    this.__tjCredential = '';
    remember(this.__tjUrl);
    return open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
    if (apiUrl(this.__tjUrl) && String(name).toLowerCase() === 'authorization') {
      this.__tjCredential = String(value || '').trim();
      observeAuth(value);
    }
    if (apiUrl(this.__tjUrl) && String(name).toLowerCase() === 'accept-language') language = String(value || '');
    return setHeader.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    if (apiUrl(this.__tjUrl) && !this.__tjOwned) withCredentials = this.withCredentials === true;
    if (apiUrl(this.__tjUrl)) this.addEventListener('load', () => {
      let data;
      try { data = this.responseType === 'json' ? this.response : JSON.parse(this.responseText); } catch (_) {}
      observeSession(this.responseURL || this.__tjUrl, this.status, data, this.__tjEpoch, this.__tjCredential);
      if (this.status >= 200 && this.status < 300) save(this.responseURL || this.__tjUrl, data, this.__tjEpoch);
    });
    return send.apply(this, arguments);
  };
  const originalFetch = window.fetch.bind(window);
  window.fetch = function (input, init) {
    const url = typeof input === 'string' ? input : input.url || String(input);
    remember(url, init && init.headers || input && input.headers);
    if (apiUrl(url)) withCredentials = (init && init.credentials || input && input.credentials) === 'include';
    const version = epoch;
    const credential = apiUrl(url) ? auth : '';
    return originalFetch(input, init).then(response => {
      if (!response.ok) observeSession(url, response.status, null, version, credential);
      if (response.ok && wanted(url)) response.clone().json()
        .then(data => { observeSession(url, response.status, data, version, credential); save(response.url || url, data, version); }).catch(() => {});
      return response;
    });
  };
  const fetchPath = path => {
    const url = base + path;
    if (inFlight.has(url)) return inFlight.get(url);
    const version = epoch;
    const credential = auth;
    const xhr = new XMLHttpRequest();
    xhr.__tjOwned = true;
    controllers.add(xhr);
    // Match the site's Angular HttpClient: XHR, observed language, and no forced cross-origin cookies.
    const request = new Promise(resolve => {
      let settled = false;
      let timer;
      const finish = (status, reason = '') => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        if (version === epoch) {
          recent.push({path: new URL(url).pathname.replace('/api/v2', ''), status, reason});
          recent = recent.slice(-24);
        }
        resolve({status, reason});
      };
      xhr.addEventListener('load', () => {
        if (version !== epoch) return finish(0, 'session_changed');
        if (xhr.status < 200 || xhr.status >= 300) return finish(xhr.status, 'http_error');
        try {
          const data = JSON.parse(xhr.responseText);
          if (data == null || typeof data !== 'object' || data.error || data.success === false) return finish(xhr.status, 'invalid_data');
          save(url, data, version);
          finish(200);
        } catch (_) { finish(xhr.status, 'invalid_json'); }
      });
      xhr.addEventListener('error', () => finish(0, 'network_or_cors'));
      xhr.addEventListener('timeout', () => finish(0, 'timeout'));
      xhr.addEventListener('abort', () => finish(0, 'cancelled'));
      timer = setTimeout(() => { finish(0, 'timeout'); xhr.abort(); }, 12000);
      try {
        xhr.open('GET', url, true);
        xhr.withCredentials = withCredentials;
        xhr.timeout = 12000;
        xhr.setRequestHeader('Accept', 'application/json, text/plain, */*');
        xhr.setRequestHeader('Authorization', credential);
        if (language) xhr.setRequestHeader('Accept-Language', language);
        xhr.send();
      } catch (_) { finish(0, 'request_failed'); }
    }).finally(() => {
      controllers.delete(xhr);
      if (inFlight.get(url) === request) inFlight.delete(url);
    });
    inFlight.set(url, request);
    return request;
  };
  // The token and its verification marker never leave this closure.
  window.__tjSessionState = () => {
    const password = document.querySelector('input[type=password]');
    const loginReady = Boolean(password && (typeof password.getClientRects !== 'function' || password.getClientRects().length));
    const login = /\/auth\//.test(location.pathname || '') || loginReady;
    return JSON.stringify({state: login ? 'login' : sessionState, authObserved: Boolean(auth),
      verified: !login && Boolean(auth) && verifiedAuth === auth, probing: probeRunning, loginReady});
  };
  window.__tjVerifySession = (manual = false) => {
    const state = JSON.parse(window.__tjSessionState());
    if (state.state === 'login' || state.verified || !auth || probeRunning) return true;
    if (manual && !['expired', 'forbidden'].includes(sessionState)) { probeFor = ''; probeAttempts = 0; nextProbeAt = 0; }
    if (probeFor === auth || Date.now() < nextProbeAt || probeAttempts >= 3) return true;
    const credential = auth;
    const version = epoch;
    probeFor = credential;
    probeAttempts++;
    probeRunning = true;
    sessionState = 'pending';
    fetchPath('/settings/user-info').then(result => {
      if (version !== epoch || credential !== auth || verifiedAuth === auth) return;
      if (result.status === 401) sessionState = 'expired';
      else if (result.status === 403) sessionState = 'forbidden';
      else {
        sessionState = result.reason || 'invalid_profile';
        if (result.status === 0 || result.status >= 500) {
          probeFor = '';
          nextProbeAt = Date.now() + probeAttempts * 3000;
          if (probeAttempts < 3) sessionState = 'pending';
        }
      }
    }).finally(() => { if (version === epoch) probeRunning = false; });
    return true;
  };
  // evaluateJavascript returns this synchronous acknowledgement, never a Promise.
  window.__tjStartJob = function (id, paths) {
    if (jobs[id]) return true;
    const job = jobs[id] = {done: false, loaded: 0, completed: 0, total: paths.length, errors: []};
    if (!auth) { Object.assign(job, {done: true, reason: 'authorization_pending'}); return true; }
    if (!JSON.parse(window.__tjSessionState()).verified) { Object.assign(job, {done: true, reason: 'session_unverified'}); return true; }
    const queue = paths.slice();
    const version = epoch;
    let forbidden = false;
    const worker = async () => {
      while (queue.length && !forbidden && version === epoch) {
        const result = await fetchPath(queue.shift());
        if (version !== epoch) return;
        job.completed++;
        if (result.status === 200 && !result.reason) job.loaded++;
        else {
          job.errors.push(result.status);
          if (!job.reason) job.reason = result.reason;
          if (result.status === 401 || result.status === 403) forbidden = true;
        }
      }
    };
    Promise.all(Array.from({length: Math.min(4, paths.length)}, worker)).then(() => {
      job.done = true;
      job.ok = job.loaded > 0;
    }).catch(() => { job.done = true; job.reason = 'request_failed'; });
    return true;
  };
  window.__tjReadJob = id => JSON.stringify(jobs[id] || {done: true, reason: 'bridge_pending'});
  window.__tjReadFile = function (rawUrl) {
    const target = academyUrl(rawUrl);
    if (!target) return Promise.reject(new Error('unsafe_file_url'));
    const headers = {};
    if (auth) headers.Authorization = auth;
    return originalFetch(target.toString(), {headers, credentials: 'include'}).then(async response => {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const blob = await response.blob();
      if (blob.size > 30 * 1024 * 1024) throw new Error('file_too_large');
      const bytes = new Uint8Array(await blob.arrayBuffer());
      let binary = '';
      for (let i = 0; i < bytes.length; i += 0x8000)
        binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + 0x8000, bytes.length)));
      return JSON.stringify({mime: blob.type || '', data: btoa(binary)});
    });
  };
  window.__tjStartFileJob = function (id, rawUrl) {
    if (fileJobs[id]) return true;
    const job = fileJobs[id] = {done: false};
    window.__tjReadFile(rawUrl).then(value => {
      if (fileJobs[id] !== job) return;
      try { Object.assign(job, JSON.parse(value), {done: true}); }
      catch (_) { Object.assign(job, {done: true, error: 'invalid_file'}); }
    }).catch(error => {
      if (fileJobs[id] === job) Object.assign(job, {done: true, error: String(error && error.message || 'download_failed')});
    });
    return true;
  };
  window.__tjReadFileJob = id => JSON.stringify(fileJobs[id] || {done: true, error: 'missing_file_job'});
  window.__tjDropFileJob = id => { delete fileJobs[id]; };
  window.__tjEvaluationSubmitted = key => submittedEvaluations.has(String(key || ''));
  window.__tjStartEvaluationJob = function (id, raw) {
    if (evaluationJobs[id]) return true;
    const job = evaluationJobs[id] = {done: false};
    if (!auth) { Object.assign(job, {done: true, reason: 'authorization_pending'}); return true; }
    if (!JSON.parse(window.__tjSessionState()).verified) {
      Object.assign(job, {done: true, reason: 'session_unverified'}); return true;
    }
    const input = raw && typeof raw === 'object' ? raw : {};
    const key = String(input.key || '').trim();
    const markLesson = Number(input.mark_lesson);
    const markTeach = Number(input.mark_teach || 0);
    const commentLesson = String(input.comment_lesson || '').trim();
    const commentTeach = String(input.comment_teach || '').trim();
    const validComment = value => !value || (value.length >= 20 && value.length <= 500);
    if (!key || key.length > 1024 || !Number.isInteger(markLesson) || markLesson < 1 || markLesson > 5 ||
        !Number.isInteger(markTeach) || markTeach < 0 || markTeach > 5 || !validComment(commentLesson) ||
        !validComment(commentTeach) || (markLesson <= 3 && commentLesson.length < 20) ||
        (markTeach > 0 && markTeach <= 3 && commentTeach.length < 20)) {
      Object.assign(job, {done: true, reason: 'invalid_evaluation'}); return true;
    }
    const body = {key, mark_lesson: markLesson, mark_teach: markTeach,
      comment_lesson: commentLesson, comment_teach: commentTeach, tags_lesson: [], tags_teach: []};
    const credential = auth;
    const version = epoch;
    const url = base + '/feedback/students/evaluate-lesson';
    const controller = new AbortController();
    controllers.add(controller);
    const timer = setTimeout(() => controller.abort(), 15000);
    const headers = {'Accept': 'application/json, text/plain, */*', 'Content-Type': 'application/json',
      'Authorization': credential};
    if (language) headers['Accept-Language'] = language;
    originalFetch(url, {method: 'POST', headers, body: JSON.stringify(body),
      credentials: withCredentials ? 'include' : 'omit', signal: controller.signal}).then(response => {
      if (version !== epoch || credential !== auth) {
        Object.assign(job, {done: true, reason: 'session_changed'}); return;
      }
      observeSession(url, response.status, null, version, credential);
      if (response.ok) {
        submittedEvaluations.add(key);
        Object.assign(job, {done: true, ok: true, status: response.status});
      } else Object.assign(job, {done: true, status: response.status, reason: 'http_error'});
    }).catch(error => Object.assign(job, {done: true,
      reason: error && error.name === 'AbortError' ? 'timeout' : 'network_or_cors'})).finally(() => {
      clearTimeout(timer);
      controllers.delete(controller);
    });
    return true;
  };
  window.__tjReadEvaluationJob = id => JSON.stringify(evaluationJobs[id] || {done: true, reason: 'missing_evaluation_job'});
  window.__tjDropEvaluationJob = id => { delete evaluationJobs[id]; };
  window.__tjTryAutoLogin = function () {
    const password = document.querySelector('input[type=password]');
    const form = password && password.closest('form');
    if (!password || !form) return JSON.stringify({ready: false, filled: false, submitted: false});
    const inputs = [...form.querySelectorAll('input')];
    const login = inputs.find(input => input !== password && ['email', 'text', 'tel'].includes((input.type || 'text').toLowerCase()));
    if (login) login.setAttribute('autocomplete', 'username');
    password.setAttribute('autocomplete', 'current-password');
    const filled = Boolean(password.value && (!login || login.value));
    if (!filled || form.dataset.tjAutoSubmitted === '1')
      return JSON.stringify({ready: true, filled, submitted: form.dataset.tjAutoSubmitted === '1'});
    for (const field of [login, password].filter(Boolean)) {
      field.dispatchEvent(new Event('input', {bubbles: true}));
      field.dispatchEvent(new Event('change', {bubbles: true}));
    }
    const submit = form.querySelector('button[type=submit],input[type=submit],button:not([type])');
    if (!submit || submit.disabled) return JSON.stringify({ready: true, filled: true, submitted: false});
    form.dataset.tjAutoSubmitted = '1';
    submit.click();
    return JSON.stringify({ready: true, filled: true, submitted: true});
  };
  window.__tjDropJob = id => { delete jobs[id]; };
  window.__tjDiagnostic = () => JSON.stringify({bridge: 20, authObserved: Boolean(auth), sessionState,
    credentialMode: withCredentials ? 'include' : 'omit', sourceCount: Object.keys(responses).length, recent});
  return true;
})()
