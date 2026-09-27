const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {test} = require('node:test');
const bridge = fs.readFileSync(__dirname + '/../app/src/main/assets/journal-bridge.js', 'utf8');
const snapshot = fs.readFileSync(__dirname + '/../app/src/main/assets/journal-snapshot.js', 'utf8');
const API = 'https://msapi.top-academy.ru/api/v2';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function environment(fetcher = async () => response([])) {
  const calls = [];
  let now = Date.now();
  class XHR {
    constructor() { this.listeners = {}; this.headers = {}; this.withCredentials = false; }
    open(method, url) { this.url = url; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send() {
      if (!this.__tjOwned) return;
      calls.push(this.url);
      Promise.resolve(fetcher(this.url, {headers: this.headers, credentials: this.withCredentials ? 'include' : 'omit'}))
        .then(async result => {
          this.status = result.status;
          this.responseText = JSON.stringify(await result.json());
          this.emit('load');
        }).catch(() => this.emit('error'));
    }
    addEventListener(name, callback) {
      (this.listeners[name] ||= []).push(callback);
      this.callback = () => this.emit('load');
    }
    emit(name) { for (const listener of this.listeners[name] || []) listener(); }
    abort() { this.emit('abort'); }
  }
  const context = {URL, Headers, AbortController, XMLHttpRequest: XHR, Date: class extends Date { static now() { return now; } }, location: {href: 'https://journal.top-academy.ru/'},
    btoa: value => Buffer.from(value, 'binary').toString('base64'),
    document: {querySelector: () => null},
    setTimeout: (fn, ms) => setTimeout(fn, ms === 12000 ? 100 : ms), clearTimeout,
    fetch: async (url, init) => { calls.push(String(url)); return fetcher(url, init); }};
  context.window = context;
  vm.createContext(context);
  vm.runInContext(bridge + ';' + snapshot, context);
  const auth = (url = API + '/dashboard/a', verified = true, token = 'test-session') => {
    const xhr = new context.XMLHttpRequest();
    xhr.open('GET', url); xhr.setRequestHeader('Authorization', token);
    if (verified && url.startsWith(API)) {
      const profile = new context.XMLHttpRequest();
      profile.open('GET', API + '/settings/user-info'); profile.setRequestHeader('Authorization', token); profile.send();
      profile.status = 200; profile.responseType = 'json'; profile.response = {student_id: 11, full_name: 'Test Student'}; profile.callback();
    }
  };
  const save = (path, data, status = 200) => {
    const xhr = new context.XMLHttpRequest();
    xhr.open('GET', API + path); xhr.send();
    xhr.status = status; xhr.responseType = 'json'; xhr.response = data; xhr.callback();
  };
  return {context, calls, auth, save, advance: ms => { now += ms; }, snapshot: route => JSON.parse(context.__tjSnapshot(route))};
}
function response(data, status = 200) { return {ok: status >= 200 && status < 300, status, json: async () => data, clone() { return this; }}; }
async function finished(env, id) {
  for (let n = 0; n < 100; n++) {
    const job = JSON.parse(env.context.__tjReadJob(id));
    if (job.done) return job;
    await delay(5);
  }
  throw Error('job did not settle');
}
test('synchronous acknowledgement keeps slow fetch pending until actual completion', async () => {
  let resolve;
  const env = environment(() => new Promise(r => { resolve = r; }));
  env.auth();
  assert.equal(env.context.__tjStartJob('1', ['/schedule/operations/get-by-date-range']), true);
  assert.equal(JSON.parse(env.context.__tjReadJob('1')).done, false);
  assert.equal(env.snapshot('schedule').items, undefined);
  resolve(response([{subject_name: 'Math'}]));
  assert.equal((await finished(env, '1')).loaded, 1);
  assert.equal(env.snapshot('schedule').items[0].title, 'Math');
});
test('missing authorization makes no network calls and remains retryable', async () => {
  const env = environment();
  env.context.__tjStartJob('1', ['/dashboard/a']);
  assert.equal((await finished(env, '1')).reason, 'authorization_pending');
  assert.equal(env.calls.length, 0);
});
test('same endpoint is deduplicated across concurrent jobs', async () => {
  let resolve;
  const env = environment(() => new Promise(r => { resolve = r; })); env.auth();
  env.context.__tjStartJob('1', ['/dashboard/a']); env.context.__tjStartJob('2', ['/dashboard/a']);
  assert.equal(env.calls.length, 1); resolve(response([]));
  assert.equal((await finished(env, '1')).loaded, 1); assert.equal((await finished(env, '2')).loaded, 1);
});
test('a 403 stops queued work without retrying and does not replace cached data', async () => {
  const env = environment(async () => response({error: 'forbidden'}, 403)); env.auth();
  env.save('/dashboard/progress/leader-group', [{full_name: 'Student', amount: 42}]);
  env.context.__tjStartJob('1', Array.from({length: 11}, (_, i) => '/dashboard/test-' + i));
  const result = await finished(env, '1');
  assert.equal(result.loaded, 0); assert.ok(env.calls.length <= 4);
  assert.equal(env.snapshot('home').leaders[0].amount, '42');
  await delay(15); assert.ok(env.calls.length <= 4);
});
test('a hanging response body settles at the deadline', async () => {
  const env = environment(async () => ({ok: true, status: 200, json: () => new Promise(() => {})})); env.auth();
  env.context.__tjStartJob('1', ['/dashboard/a']);
  const result = await finished(env, '1'); assert.equal(result.done, true); assert.equal(result.errors[0], 0);
});
test('reset rejects late responses from the previous session', async () => {
  let resolve; const env = environment(() => new Promise(r => { resolve = r; })); env.auth();
  env.context.__tjStartJob('1', ['/schedule/operations/get-by-date-range']);
  env.context.__tjReset(); resolve(response([{subject_name: 'Private old data'}])); await delay(15);
  assert.deepEqual(env.snapshot('schedule'), {_sources: {}});
  assert.equal(JSON.parse(env.context.__tjReadJob('1')).reason, 'bridge_pending');
});
test('fetch Request headers are captured, unrelated origins cannot supply credentials', async () => {
  const env = environment(async url => response(String(url).endsWith('/settings/user-info') ? {student_id: 11, full_name: 'Test Student'} : [])); env.auth('https://unrelated.example/api/v2/dashboard/a');
  env.context.__tjStartJob('1', ['/dashboard/a']); assert.equal((await finished(env, '1')).reason, 'authorization_pending');
  await env.context.fetch({url: API + '/dashboard/a', headers: new Headers({authorization: 'test-session'})});
  env.context.__tjVerifySession(); await delay(10);
  env.context.__tjStartJob('2', ['/dashboard/b']); assert.equal((await finished(env, '2')).loaded, 1);
});
test('an app shell and a captured token do not prove a signed-in session', () => {
  const env = environment(); env.context.document.querySelector = selector => selector === 'mystat' ? {} : null;
  env.auth(undefined, false);
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, false);
  env.context.__tjStartJob('1', ['/dashboard/a']);
  assert.equal(JSON.parse(env.context.__tjReadJob('1')).reason, 'session_unverified');
  assert.equal(env.calls.length, 0);
});
test('one successful authenticated profile request unlocks native data', async () => {
  const env = environment(async () => response({student_id: 11, full_name: 'Student A'})); env.auth(undefined, false);
  env.context.__tjVerifySession(); env.context.__tjVerifySession(); await delay(10);
  assert.equal(env.calls.length, 1); assert.equal(JSON.parse(env.context.__tjSessionState()).verified, true);
});
test('401 expires the session; repeated checks do not loop or masquerade as 403', async () => {
  const env = environment(async () => response({}, 401)); env.auth(undefined, false);
  env.context.__tjVerifySession(); await delay(10);
  assert.equal(JSON.parse(env.context.__tjSessionState()).state, 'expired');
  env.context.__tjVerifySession(); await delay(10); assert.equal(env.calls.length, 1);
});
test('403 is a forbidden profile response, not an assertion of logout', async () => {
  const env = environment(async () => response({}, 403)); env.auth(undefined, false);
  env.context.__tjVerifySession(); await delay(10);
  const session = JSON.parse(env.context.__tjSessionState());
  assert.equal(session.state, 'forbidden'); assert.equal(session.verified, false);
  env.context.__tjVerifySession(); assert.equal(env.calls.length, 1);
});
test('a fresh credential is verified independently after token refresh', async () => {
  const env = environment(async () => response({student_id: 11, full_name: 'Student A'})); env.auth();
  env.auth(undefined, false, 'new-session');
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, false);
  env.context.__tjVerifySession(); await delay(10);
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, true);
});
test('login route exposes sign-in even before the password input appears', () => {
  const env = environment(); env.auth(); env.context.location.pathname = '/ru/auth/login/index';
  const session = JSON.parse(env.context.__tjSessionState());
  assert.equal(session.state, 'login'); assert.equal(session.verified, false);
  env.context.__tjStartJob('login-race', ['/dashboard/a']);
  assert.equal(JSON.parse(env.context.__tjReadJob('login-race')).reason, 'session_unverified');
  assert.equal(env.calls.length, 0);
});
test('late 401 for an old token cannot invalidate the refreshed session', () => {
  const env = environment(); env.auth();
  const old = new env.context.XMLHttpRequest(); old.open('GET', API + '/dashboard/a');
  old.setRequestHeader('Authorization', 'test-session'); old.send();
  env.auth(undefined, true, 'new-session');
  old.status = 401; old.responseType = 'json'; old.response = {}; old.callback();
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, true);
});
test('HTTP failures captured through fetch never become successful snapshots', async () => {
  const env = environment(async () => response({items: []}, 403));
  await env.context.fetch(API + '/schedule/operations/get-by-date-range'); await delay(5);
  assert.deepEqual(env.snapshot('schedule'), {_sources: {}});
});
test('home loading is field based; homework counters are captured', () => {
  const env = environment(); assert.deepEqual(env.snapshot('home'), {_sources: {}});
  env.save('/count/homework', {all_count: 14, current_count: 3, done_count: 8, inspection_count: 2, expired_count: 1});
  const result = env.snapshot('home'); assert.equal(result.tasks.total, '14'); assert.equal(result.tasks.open, '3');
  assert.equal(result.score, undefined); assert.equal(result.exams, undefined);
});
test('empty list is loaded data; absent or unknown schema stays missing', () => {
  const env = environment(); env.save('/homework/operations/list', {data: {items: []}});
  assert.deepEqual(env.snapshot('tasks').items, []);
  env.save('/progress/operations/student-visits', {unexpected: 'value'});
  assert.equal(env.snapshot('grades').items, undefined);
});
test('nested envelopes and zero marks are handled without fabricated grades', () => {
  const env = environment(); env.save('/progress/operations/student-visits', {data: {items: [
    {spec_name: 'Math', class_work_mark: 10, home_work_mark: 12, status_was: 1},
    {spec_name: 'Physics', class_work_mark: null, status_was: 0}]}});
  const result = env.snapshot('grades'); assert.deepEqual(result.series, [11]); assert.equal(result.items[1].average, null);
});
test('partial success exposes successful sections before another request completes', async () => {
  let resolve;
  const env = environment(url => url.endsWith('/count/homework') ? Promise.resolve(response({all_count: 4})) : new Promise(r => { resolve = r; }));
  env.auth(); env.context.__tjStartJob('1', ['/count/homework', '/dashboard/info/future-exams']); await delay(5);
  assert.equal(env.snapshot('home').tasks.total, '4'); assert.equal(env.snapshot('home').exams, undefined);
  assert.equal(JSON.parse(env.context.__tjReadJob('1')).done, false);
  resolve(response([])); await finished(env, '1'); assert.deepEqual(env.snapshot('home').exams, []);
});
test('account change clears old responses; tokens never occur in snapshots or job results', () => {
  const env = environment(); env.auth(); env.context.__tjAccount('Student A');
  env.save('/count/homework', {all_count: 2}); env.context.__tjAccount('Student B');
  assert.deepEqual(env.snapshot('home'), {_sources: {}});
  assert.equal(JSON.stringify(env.snapshot('home')).includes('test-session'), false);
  assert.equal(env.context.__tjReadJob('missing').includes('test-session'), false);
});
test('background XHR does not force credentialed CORS and retains site language', async () => {
  let options;
  const env = environment(async (url, init) => { options = init; return response([]); }); env.auth();
  const siteRequest = new env.context.XMLHttpRequest();
  siteRequest.open('GET', API + '/settings/user-info');
  siteRequest.setRequestHeader('Accept-Language', 'Russian, ru'); siteRequest.send();
  env.context.__tjStartJob('1', ['/schedule/operations/get-by-date-range']);
  assert.equal((await finished(env, '1')).loaded, 1);
  assert.equal(options.credentials, 'omit');
  assert.equal(options.headers['Accept-Language'], 'Russian, ru');
});
test('models in the live Journal bundle map counters, attendance, progress and exams', () => {
  const env = environment();
  env.save('/count/homework', [{counter_type: 0, counter: 1}, {counter_type: 1, counter: 20},
    {counter_type: 2, counter: 3}, {counter_type: 3, counter: 4}, {counter_type: 4, counter: 28}]);
  env.save('/dashboard/progress/academic-performance', {totalAllTime: 10.5, totalMonth: 11, maxAllowedPoint: 12});
  env.save('/dashboard/progress/attendance-statistic', {statTotal: 97, statMonth: 100});
  env.save('/dashboard/info/future-exams', [{spec: 'Android', date: '2026-09-09'}]);
  env.save('/dashboard/progress/activity', [{achievements_name: 'Homework checked', current_point: 5, date: '2026-09-06'}]);
  const data = env.snapshot('home');
  assert.deepEqual(data.tasks, {total: '28', open: '4', done: '20', inspection: '3', expired: '1'});
  assert.equal(data.score, '10.5'); assert.equal(data.attendance, '97');
  assert.equal(data.exams[0].title, 'Android'); assert.equal(data.activity[0].amount, '5');
});
test('user-info supplies header balances and rating resolves by student id', () => {
  const env = environment();
  env.save('/settings/user-info', {student_id: 11, full_name: 'Student A', gaming_points: [
    {new_gaming_point_types__id: 1, points: 300}, {new_gaming_point_types__id: 2, points: 40}]});
  env.save('/dashboard/progress/leader-group', [{id: 11, full_name: 'Student A', amount: 740}]);
  const data = JSON.parse(env.context.__tjHeader());
  assert.equal(data.score, '740'); assert.equal(data.topCoins, '300'); assert.equal(data.gems, '40');
});
test('network diagnostics contain a reason and route, never auth or response content', async () => {
  const env = environment(async () => { throw Error('private payload'); }); env.auth();
  env.context.__tjStartJob('1', ['/dashboard/a?private=value']);
  assert.equal((await finished(env, '1')).reason, 'network_or_cors');
  const report = env.context.__tjDiagnostic();
  assert.equal(JSON.parse(report).recent[0].path, '/dashboard/a');
  assert.equal(report.includes('test-session'), false); assert.equal(report.includes('private'), false);
});

test('transient profile failure retries with backoff then verifies without navigating', async () => {
  let attempt = 0;
  const env = environment(async () => ++attempt === 1 ? response({}, 503) : response({student_id: 11, full_name: 'Student'}));
  env.auth(undefined, false); env.context.__tjVerifySession(); await delay(10);
  assert.equal(JSON.parse(env.context.__tjSessionState()).state, 'pending');
  env.context.__tjVerifySession(); assert.equal(env.calls.length, 1);
  env.advance(3100); env.context.__tjVerifySession(); await delay(10);
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, true);
  assert.equal(env.calls.length, 2);
});

test('unavailable profile stops after three attempts; manual retry remains possible', async () => {
  const env = environment(async () => { throw Error('offline'); }); env.auth(undefined, false);
  for (let i = 0; i < 5; i++) { env.context.__tjVerifySession(); await delay(10); env.advance(10000); }
  assert.equal(env.calls.length, 3);
  assert.equal(JSON.parse(env.context.__tjSessionState()).state, 'network_or_cors');
  env.context.__tjVerifySession(true); await delay(10); assert.equal(env.calls.length, 4);
});

test('login route is different from a mounted login form', () => {
  const env = environment(); env.context.location.pathname = '/ru/auth/login/index';
  assert.equal(JSON.parse(env.context.__tjSessionState()).loginReady, false);
  env.context.document.querySelector = () => ({getClientRects: () => []});
  assert.equal(JSON.parse(env.context.__tjSessionState()).loginReady, false);
  env.context.document.querySelector = () => ({getClientRects: () => [{}]});
  assert.equal(JSON.parse(env.context.__tjSessionState()).loginReady, true);
});

test('homework status and page caches cannot bleed into one another', () => {
  const env = environment();
  env.save('/homework/operations/list?status=3&type=0&page=1', [{id: 1, name_spec: 'Math', theme: 'A'}]);
  env.save('/homework/operations/list?page=10&type=0&status=3', [{id: 10, name_spec: 'Page ten'}]);
  env.save('/homework/operations/list?status=2&type=0&page=1', []);
  const data = env.snapshot('tasks');
  assert.equal(data.tasks_3_1[0].id, 1); assert.equal(data.tasks_3_10[0].id, 10);
  assert.deepEqual(data.tasks_2_1, []); assert.equal(data.tasks_0_1, undefined);
});

test('homework snapshot keeps every distinct attachment', () => {
  const env = environment();
  env.save('/homework/operations/list?status=3&type=0&page=1', [{id: 7, name_spec: 'Design',
    files: [{file_path: '/a.pptx', filename: 'brief.pptx'}, {download_url: '/b.jpg', name: 'reference.jpg'}],
    file_path: '/a.pptx', filename: 'brief.pptx'}]);
  const task = env.snapshot('tasks').tasks_3_1[0];
  assert.equal(task.attachments.length, 2);
  assert.equal(task.attachments[0].filename, 'brief.pptx');
  assert.equal(task.attachments[1].filename, 'reference.jpg');
});

test('authorized attachment job resolves asynchronously and keeps credentials on Academy hosts', async () => {
  const bytes = Uint8Array.from([0xff, 0xd8, 0xff, 0x01]);
  let options;
  const env = environment(async (url, init) => {
    options = init;
    return {ok: true, status: 200, blob: async () => ({size: bytes.length, type: 'image/jpeg', arrayBuffer: async () => bytes.buffer})};
  });
  env.auth();
  assert.equal(env.context.__tjStartFileJob('photo', 'https://files.top-academy.ru/task/photo'), true);
  let job;
  for (let i = 0; i < 30; i++) {
    job = JSON.parse(env.context.__tjReadFileJob('photo'));
    if (job.done) break;
    await delay(5);
  }
  assert.equal(job.done, true);
  assert.equal(job.mime, 'image/jpeg');
  assert.equal(job.data, '/9j/AQ==');
  assert.equal(options.headers.Authorization, 'test-session');
  assert.equal(options.credentials, 'include');
});

test('background login submits a form only after stored values are present', () => {
  const env = environment();
  let clicks = 0;
  const submit = {disabled: false, click: () => { clicks++; }};
  const login = {type: 'text', value: 'student', setAttribute() {}, dispatchEvent() {}};
  let form;
  const password = {type: 'password', value: 'saved-password', setAttribute() {}, dispatchEvent() {}, closest: () => form};
  form = {dataset: {}, querySelectorAll: () => [login, password], querySelector: () => submit};
  env.context.Event = class { constructor(type, options) { this.type = type; this.options = options; } };
  env.context.document.querySelector = selector => selector === 'input[type=password]' ? password : null;
  assert.equal(JSON.parse(env.context.__tjTryAutoLogin()).submitted, true);
  assert.equal(JSON.parse(env.context.__tjTryAutoLogin()).submitted, true);
  assert.equal(clicks, 1);
});

test('material categories and recommendation filters stay independent even when query order changes', () => {
  const env = environment();
  env.save('/library/operations/list?recommended_type=0&material_type=2', [{material_id: 4, theme: 'Lesson', name_spec: 'Math', download_url: 'https://example.com/file'}]);
  env.save('/library/operations/list?material_type=4&recommended_type=1', []);
  const data = env.snapshot('materials');
  assert.equal(data.materials_2_0[0].url, 'https://example.com/file');
  assert.deepEqual(data.materials_4_1, []); assert.equal(data.materials_4_0, undefined);
});

test('news detail maps by id; failure leaves successfully loaded news intact', () => {
  const env = environment(); env.save('/news/operations/latest-news', [{id_bbs: 7, theme: 'News', time: '2026-09-07', viewed: false}]);
  env.save('/news/operations/detail-news?news_id=7', {id_bbs: 7, text_bbs: '<p>Text</p>'});
  env.save('/news/operations/detail-news?news_id=8', {}, 403);
  const data = env.snapshot('announcements');
  assert.equal(data.items[0].id, 7); assert.equal(data.news_7.body, '<p>Text</p>'); assert.equal(data.news_8, undefined);
});

test('rewards use actual active flags, point fields and journal translations', () => {
  const env = environment();
  env.save('/profile/statistic/student-achievements', [{id: 1, translate_key: 'attendance5', is_active: true, achieve_points: [{id: 1, points_count: 1}, {id: 2, points_count: 0}]}]);
  env.save('/public/translations?language=ru', {attendance5: '5 посещений подряд без пропусков'});
  const reward = env.snapshot('rewards').items[0];
  assert.equal(reward.title, '5 посещений подряд без пропусков'); assert.equal(reward.earned, true);
  assert.deepEqual(reward.points, [{type: 1, amount: 1}]);
});

test('pending lesson evaluations use the live Journal field names and branch mode', () => {
  const env = environment();
  env.save('/feedback/students/evaluate-lesson-list', [{key: 'lesson-key', date_visit: '2026-09-08',
    fio_teach: 'Teacher A', spec_name: 'Android'}]);
  env.save('/settings/user-info', {student_id: 11, full_name: 'Student A',
    visibility_settings: {is_school_branch: true}});
  const home = env.snapshot('home');
  assert.deepEqual(home.evaluations[0], {key: 'lesson-key', date: '2026-09-08',
    teacher: 'Teacher A', subject: 'Android'});
  assert.equal(home.schoolBranch, true);
});

test('lesson evaluation is authorized, serialized and removed from the pending snapshot after success', async () => {
  let request;
  const env = environment(async (url, init) => { request = {url: String(url), init}; return response({}, 205); });
  env.auth();
  env.save('/feedback/students/evaluate-lesson-list', [{key: 'lesson-key', fio_teach: 'Teacher A', spec_name: 'Android'}]);
  assert.equal(env.context.__tjStartEvaluationJob('vote', {key: 'lesson-key', mark_lesson: 5, mark_teach: 4,
    comment_lesson: '', comment_teach: '', tags_lesson: [99]}), true);
  let job;
  for (let i = 0; i < 30; i++) {
    job = JSON.parse(env.context.__tjReadEvaluationJob('vote'));
    if (job.done) break;
    await delay(5);
  }
  assert.equal(job.ok, true);
  assert.equal(request.url, API + '/feedback/students/evaluate-lesson');
  assert.equal(request.init.method, 'POST');
  assert.equal(request.init.headers.Authorization, 'test-session');
  assert.deepEqual(JSON.parse(request.init.body), {key: 'lesson-key', mark_lesson: 5, mark_teach: 4,
    comment_lesson: '', comment_teach: '', tags_lesson: [], tags_teach: []});
  assert.deepEqual(env.snapshot('home').evaluations, []);
});

test('a low lesson rating without the required comment is rejected before network access', () => {
  const env = environment(); env.auth();
  env.context.__tjStartEvaluationJob('invalid-vote', {key: 'lesson-key', mark_lesson: 3, mark_teach: 5});
  const job = JSON.parse(env.context.__tjReadEvaluationJob('invalid-vote'));
  assert.equal(job.reason, 'invalid_evaluation');
  assert.equal(env.calls.length, 0);
});

test('teacher reviews distinguish an empty response from an unavailable endpoint', () => {
  const env = environment(); assert.equal(env.snapshot('reviews').items, undefined);
  env.save('/reviews/index/list', []); assert.deepEqual(env.snapshot('reviews').items, []);
  env.save('/reviews/index/list', [{date: '2026-09-07', message: 'Good work', teacher: 'Teacher', full_spec: 'Math'}]);
  assert.equal(env.snapshot('reviews').items[0].body, 'Good work');
});

test('login transition discards account data without losing an already observed credential', async () => {
  const env = environment(async () => response({student_id: 11, full_name: 'Student'})); env.auth();
  env.save('/dashboard/progress/leader-group', [{full_name: 'Student', amount: 42}]);
  env.context.__tjEnterLogin();
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, false);
  assert.equal(JSON.parse(env.context.__tjSessionState()).authObserved, true);
  assert.equal(env.snapshot('home').leaders, undefined);
  env.context.__tjVerifySession(); await delay(10);
  assert.equal(JSON.parse(env.context.__tjSessionState()).verified, true);
});
