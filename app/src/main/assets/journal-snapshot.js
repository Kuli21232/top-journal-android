(function () {
  const latest = (part, exclude) => {
    const query = part.includes('?') ? new URL(part, 'https://journal.top-academy.ru') : null;
    const entries = Object.entries(window.__tjResponses ? window.__tjResponses() : {})
      .filter(([url, value]) => (query ? url.includes(query.pathname) && [...query.searchParams].every(([k, v]) => new URL(url).searchParams.get(k) === v) : url.includes(part))
        && (!exclude || !url.includes(exclude)) && value && value.data != null);
    entries.sort((a, b) => b[1].time - a[1].time);
    return entries.length ? entries[0][1] : null;
  };
  const arr = value => {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== 'object') return null;
    for (const key of ['data', 'items', 'results', 'models', 'homeworks', 'lessons', 'visits']) {
      const rows = arr(value[key]);
      if (rows !== null) return rows;
    }
    return null;
  };
  const clean = value => typeof value === 'object' ? '' : String(value ?? '').replace(/\s+/g, ' ').trim();
  const pick = (obj, keys, fallback = '') => {
    for (const key of keys) { const value = clean(obj?.[key] ?? obj?.data?.[key]); if (value) return value; }
    return fallback;
  };
  const homeworkFiles = obj => {
    const found = [];
    const add = value => {
      if (!value) return;
      if (Array.isArray(value)) { value.forEach(add); return; }
      if (typeof value === 'string') { found.push({url: value, filename: '', mime: ''}); return; }
      if (typeof value !== 'object') return;
      const url = pick(value, ['file_path', 'download_url', 'url', 'path', 'link']);
      if (url) found.push({url, filename: pick(value, ['filename', 'file_name', 'name_file', 'name']),
        mime: pick(value, ['mime_type', 'mimeType', 'content_type', 'file_type', 'type'])});
    };
    for (const key of ['files', 'attachments', 'homework_files', 'file_list', 'materials']) add(obj?.[key]);
    add({file_path: obj?.file_path, filename: obj?.filename || obj?.file_name || obj?.name_file,
      mime_type: obj?.mime_type || obj?.mimeType || obj?.content_type || obj?.file_type});
    const unique = new Map();
    found.forEach(file => { if (file.url && !unique.has(file.url)) unique.set(file.url, file); });
    return [...unique.values()];
  };
  const series = value => {
    const out = [];
    const walk = v => {
      if (Array.isArray(v)) { v.forEach(walk); return; }
      if (!v || typeof v !== 'object') return;
      const raw = v.points ?? v.value ?? v.chart_value ?? v.average ?? v.percent;
      const n = raw == null || raw === '' ? NaN : Number(raw);
      if (Number.isFinite(n)) out.push(n);
      for (const key of ['data', 'items', 'results', 'chart_models', 'models']) if (v[key]) walk(v[key]);
    };
    walk(value);
    return out.slice(-18);
  };
  window.__tjHeader = () => {
    const userSource = latest('/settings/user-info');
    const user = userSource?.data?.data || userSource?.data || {};
    const domText = selector => clean(document.querySelector(selector)?.textContent);
    const profile = pick(user, ['full_name']) || domText('.top-panel__profile-name');
    const points = arr(user.gaming_points) || [];
    const balance = id => { const item = points.find(x => Number(x.new_gaming_point_types__id) === id); return item ? clean(item.points) : ''; };
    const number = selector => domText(selector).match(/-?\d+(?:[.,]\d+)?/)?.[0] || '';
    const normalize = value => clean(value).toLowerCase().replace(/ё/g, 'е');
    let self;
    for (const scope of ['group', 'stream']) {
      const rows = arr(latest('/dashboard/progress/leader-' + scope, '-points')?.data) || [];
      self = rows.find(x => user.student_id != null && String(x.id) === String(user.student_id)) ||
        rows.find(x => profile && normalize(x.full_name) === normalize(profile));
      if (!self) {
        const stat = latest('/dashboard/progress/leader-' + scope + '-points')?.data;
        const position = stat?.studentPosition;
        if (position != null) self = rows.find(x => Number(x.position) === Number(position));
      }
      if (self) break;
    }
    // Journal binds all-christal/top-coin.svg to point type 1 and all-coin to type 2.
    return JSON.stringify({profile, score: self?.amount == null ? '' : clean(self.amount),
      topCoins: balance(1) || number('#all-christal .count-item'), gems: balance(2) || number('#all-coin .count-item'),
      avatar: pick(user, ['photo']) || document.querySelector('.top-panel__avatar.avatar')?.src || ''});
  };
  window.__tjSnapshot = route => {
    const output = {_sources: {}};
    const put = (key, value, source) => { output[key] = value; output._sources[key] = source.time; };
    const scalar = (key, path, keys) => {
      const source = latest(path);
      if (source) put(key, pick(source.data, keys, '—'), source);
    };
    const list = (key, path, map, exclude) => {
      const source = latest(path, exclude);
      const rows = source && arr(source.data);
      if (rows !== null && rows !== undefined) put(key, rows.map(map), source);
    };
    const translated = key => clean(latest('/public/translations')?.data?.[key]) || '';
    if (route === 'home') {
      const counts = latest('/count/homework');
      const counterRows = counts && arr(counts.data);
      if (counterRows && counterRows.every(x => x.counter_type != null && Number.isFinite(Number(x.counter)))) {
        const count = type => String(Number(counterRows.find(x => Number(x.counter_type) === type)?.counter || 0));
        put('tasks', {total: count(4), open: count(3), done: count(1), inspection: count(2), expired: count(0)}, counts);
      } else if (counts && !counterRows) put('tasks', {
        total: pick(counts.data, ['all_count', 'all', 'total_count', 'total', 'count'], '—'),
        open: pick(counts.data, ['open_count', 'opened', 'open', 'current', 'current_count'], '—'),
        done: pick(counts.data, ['done', 'checked', 'completed', 'done_count'], '—'),
        inspection: pick(counts.data, ['inspection', 'on_review', 'review', 'inspection_count'], '—'),
        expired: pick(counts.data, ['expired', 'overdue', 'lose', 'expired_count'], '—')
      }, counts);
      scalar('score', '/dashboard/progress/academic-performance', ['totalAllTime', 'totalMonth', 'average', 'average_mark', 'value', 'points']);
      scalar('attendance', '/dashboard/progress/attendance-statistic', ['statTotal', 'statMonth', 'attendance', 'percent', 'value', 'points']);
      scalar('group', '/dashboard/progress/leader-group-points', ['studentPosition', 'student_position', 'position']);
      scalar('stream', '/dashboard/progress/leader-stream-points', ['studentPosition', 'student_position', 'position']);
      for (const [key, path] of [['scoreSeries', '/dashboard/chart/average-progress'], ['attendanceSeries', '/dashboard/chart/attendance']]) {
        const source = latest(path);
        if (source) {
          const values = series(source.data);
          put(key, values, source);
          if (values.length && key === 'scoreSeries') put('score', String(values[values.length - 1]), source);
        }
      }
      list('exams', '/dashboard/info/future-exams', x => ({date: pick(x, ['date', 'exam_date', 'started_at', 'start_date']),
        title: pick(x, ['spec', 'spec_name', 'subject_name', 'name', 'title'], 'Экзамен')}));
      list('leaders', '/dashboard/progress/leader-group', (x, i) => ({rank: pick(x, ['position', 'rank'], String(i + 1)),
        name: pick(x, ['full_name', 'fullName', 'name']), amount: pick(x, ['amount', 'points', 'score'])}), '-points');
      list('activity', '/dashboard/progress/activity', x => ({date: pick(x, ['date', 'created_at', 'date_create']),
        title: pick(x, ['title', 'name', 'description', 'achievements_name', 'point_types_name'], 'Учебная активность'),
        amount: pick(x, ['current_point', 'amount', 'points', 'score'])}));
      const evaluations = latest('/feedback/students/evaluate-lesson-list');
      const evaluationRows = evaluations && arr(evaluations.data);
      if (evaluationRows !== null && evaluationRows !== undefined) put('evaluations', evaluationRows
        .filter(x => x && x.key && !(window.__tjEvaluationSubmitted && window.__tjEvaluationSubmitted(x.key)))
        .map(x => ({key: clean(x.key), date: pick(x, ['date_visit', 'date']),
          teacher: pick(x, ['fio_teach', 'teacher_name', 'teacher']),
          subject: pick(x, ['spec_name', 'subject_name', 'subject'], 'Занятие')})), evaluations);
      const userInfo = latest('/settings/user-info');
      if (userInfo) {
        const user = userInfo.data?.data || userInfo.data || {};
        const visibility = user.visibility_settings || user.visibilitySettings || {};
        put('schoolBranch', visibility.is_school_branch === true || visibility.isSchoolBranch === true, userInfo);
      }
    } else if (route === 'schedule') {
      list('items', '/schedule/operations/get-by-date-range', x => ({date: pick(x, ['date', 'date_filter']),
        lesson: pick(x, ['lesson', 'lesson_number']), start: pick(x, ['started_at', 'startedAt', 'start_time']),
        end: pick(x, ['finished_at', 'finishedAt', 'end_time']), title: pick(x, ['subject_name', 'subjectName', 'spec_name', 'title']),
        teacher: pick(x, ['teacher_name', 'teacherName', 'teacher']), room: pick(x, ['room_name', 'roomName', 'room'])}));
    } else if (route === 'grades') {
      list('items', '/progress/operations/student-visits', x => {
        const marks = ['home_work_mark', 'lab_work_mark', 'class_work_mark', 'control_work_mark', 'practical_work_mark', 'final_work_mark']
          .map(key => Number(x[key])).filter(n => Number.isFinite(n) && n > 0);
        return {date: pick(x, ['date_visit', 'date']), title: pick(x, ['spec_name', 'subject_name']),
          teacher: pick(x, ['teacher_name', 'teacher']), theme: pick(x, ['lesson_theme', 'theme']),
          status: x.status_was, marks, average: marks.length ? marks.reduce((a, b) => a + b, 0) / marks.length : null};
      });
      if (output.items) {
        output.series = output.items.filter(x => x.average != null).map(x => Number(x.average.toFixed(2))).slice(-18);
        output._sources.series = output._sources.items;
      }
    } else if (route === 'tasks') {
      list('items', '/homework/operations/list', x => ({title: pick(x, ['name_spec', 'spec_name', 'subject_name', 'title']),
        theme: pick(x, ['theme', 'lesson_theme', 'homework', 'task']), deadline: pick(x, ['completion_time', 'date_end', 'deadline']),
        teacher: pick(x, ['fio_teach', 'teacher_name', 'teacher']), status: pick(x, ['status', 'common_status'])}));
      for (let status = 0; status <= 3; status++) for (let page = 1; page <= 30; page++) {
        list('tasks_' + status + '_' + page, '/homework/operations/list?status=' + status + '&type=0&page=' + page, x => ({
          id: x.id, title: pick(x, ['name_spec', 'theme'], 'Задание'), theme: pick(x, ['theme']),
          body: pick(x, ['comment']), deadline: pick(x, ['completion_time']), teacher: pick(x, ['fio_teach']),
          url: pick(x, ['file_path']), filename: pick(x, ['filename', 'file_name', 'name_file']),
          mime: pick(x, ['mime_type', 'mimeType', 'content_type', 'file_type']), attachments: homeworkFiles(x), status
        }));
      }
    } else if (route === 'announcements') {
      list('items', '/news/operations/latest-news', x => ({id: x.id_bbs, title: pick(x, ['theme'], 'Объявление'),
        date: pick(x, ['time']), viewed: x.viewed === true}));
      for (const [url, source] of Object.entries(window.__tjResponses())) {
        if (!url.includes('/news/operations/detail-news?')) continue;
        const id = new URL(url).searchParams.get('news_id');
        const x = source.data?.data || source.data;
        if (id && typeof x?.text_bbs === 'string') put('news_' + id, {body: x.text_bbs}, source);
      }
    } else if (route === 'reviews') {
      list('items', '/reviews/index/list', x => ({title: pick(x, ['full_spec', 'spec'], 'Отзыв преподавателя'),
        teacher: pick(x, ['teacher']), date: pick(x, ['date']), body: pick(x, ['message'])}));
    } else if (route === 'rewards') {
      list('items', '/profile/statistic/student-achievements', x => ({id: x.id,
        title: translated(x.translate_key) || pick(x, ['name', 'title'], 'Достижение №' + x.id),
        earned: x.is_active === true || x.is_active === 1,
        points: (arr(x.achieve_points) || []).filter(p => Number(p.points_count) > 0).map(p => ({type: p.id, amount: p.points_count}))}));
      const translation = latest('/public/translations');
      if (output.items && translation) output._sources.items = Math.max(output._sources.items, translation.time);
    } else if (route === 'materials') {
      for (const type of [2, 4, 5, 8, 3, 7, 6, 1]) for (const recommended of [0, 1]) {
        list('materials_' + type + '_' + recommended,
          '/library/operations/list?material_type=' + type + '&recommended_type=' + recommended,
          x => ({id: x.material_id, title: pick(x, ['theme', 'quiz_title', 'name'], 'Учебный материал'),
            subject: pick(x, ['name_spec']), date: pick(x, ['date']),
            url: pick(x, ['download_url', 'url', 'link']), filename: pick(x, ['filename']), type}));
      }
    }
    return JSON.stringify(output);
  };
  return true;
})()
