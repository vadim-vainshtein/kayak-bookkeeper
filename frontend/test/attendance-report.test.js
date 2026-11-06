import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h, useState } from 'react';
import { act, create } from 'react-test-renderer';
import AttendanceReports from '../src/features/reports/AttendanceReports.jsx';
import AttendanceReportTable from '../src/features/reports/AttendanceReportTable.jsx';
import ReportsPage from '../src/features/reports/ReportsPage.jsx';
import useAttendanceReport from '../src/hooks/useAttendanceReport.js';
import { attendanceReportCsv } from '../src/features/reports/attendanceReportCsv.js';

const summary = { scheduledSessions: 4, startedSessions: 3, recordedSessions: 2, memberVisits: 1, guestVisits: 1, totalVisits: 2, uniqueVisitors: 2 };
const subscription = {
    subscriptionId: 7, title: 'Ноябрь', status: 'FINALIZED', memberCount: 2, summary,
    sessions: [
        { sessionId: 10, startsAt: '2026-11-01T19:00:00', started: true, recorded: true, totalVisits: 2 },
        { sessionId: 11, startsAt: '2026-11-02T19:00:00', started: true, recorded: false, totalVisits: 0 },
        { sessionId: 12, startsAt: '2026-11-10T19:00:00', started: false, recorded: false, totalVisits: 0 },
        { sessionId: 13, startsAt: '2026-11-03T19:00:00', started: true, recorded: true, totalVisits: 0 },
    ],
    people: [
        { personId: 1, personName: 'Участник', currentMember: true, memberVisits: 1, guestVisits: 0, totalVisits: 1, visits: [{ sessionId: 10, guest: false }] },
        { personId: 2, personName: 'Гость', currentMember: false, memberVisits: 0, guestVisits: 1, totalVisits: 1, visits: [{ sessionId: 10, guest: true }] },
        { personId: 3, personName: 'Без посещений', currentMember: true, memberVisits: 0, guestVisits: 0, totalVisits: 0, visits: [] },
    ],
};
const report = (totalVisits = 2) => ({ generatedAt: '2026-11-07T12:00:00', timeZone: 'Asia/Qyzylorda', summary: { ...summary, totalVisits }, subscriptions: [subscription] });
const subscriptions = [{ id: 7, title: 'Ноябрь', status: 'FINALIZED' }, { id: 8, title: null, status: 'COMPLETED' }, { id: 9, title: 'Черновик', status: 'DRAFT' }];
const text = (node) => typeof node === 'string' ? node : (node?.children || []).map(text).join('');
const button = (renderer, label) => renderer.root.findAllByType('button').find((item) => text(item) === label);
const apply = (renderer) => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} });
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

async function mount(api) {
    let state, renderer;
    function Harness({ visible = true }) {
        state = useAttendanceReport(api);
        return visible ? h(AttendanceReports, { reportState: state.attendanceReport, loading: state.attendanceLoading,
            error: state.attendanceError, loadReport: state.loadAttendanceReport, subscriptions }) : null;
    }
    await act(async () => { renderer = create(h(Harness)); });
    return { renderer, Harness, state: () => state };
}

test('matrix preserves member/guest roles, zero-visit people, empty recordings and future/unrecorded distinctions', async () => {
    let renderer;
    await act(async () => { renderer = create(h(AttendanceReportTable, { subscription })); });
    const rows = renderer.root.findByType('tbody').findAllByType('tr');
    assert.equal(rows.length, 3);
    assert.equal(rows[0].findAllByType('td')[0].findByType('span').props['aria-label'], 'По абонементу');
    assert.equal(text(rows[1].findAllByType('td')[0]), 'Гость');
    assert.equal(text(rows[2].findAllByType('td')[0]), '—');
    assert.equal(rows[2].findAllByType('td')[0].props.title, 'Нет отметки посещения');
    assert.equal(text(rows[2].findAllByType('td')[1]), 'Не отмечено');
    assert.equal(text(rows[2].findAllByType('td')[2]), 'Ещё не началась');
    assert.equal(text(rows[2].findAllByType('td')[3]), '—');
    assert.equal(text(rows[2].findAllByType('td').at(-1)), '0');
    assert.match(text(rows[1]), /Вне текущего состава/);
    assert.match(text(renderer.root.findByType('tfoot')), /Всего пришли2/);
    await act(async () => renderer.unmount());
});

test('report filters apply explicitly and CSV uses the last successful snapshot rather than draft filters', async (t) => {
    let downloaded;
    const oldDocument = globalThis.document;
    globalThis.document = { createElement: () => ({ click() {} }) };
    t.after(() => { globalThis.document = oldDocument; });
    t.mock.method(URL, 'createObjectURL', (blob) => { downloaded = blob; return 'blob:attendance-report'; });
    t.mock.method(URL, 'revokeObjectURL', () => {});
    const urls = [];
    const { renderer, state } = await mount(async (url) => { urls.push(url); return report(url.includes('FINALIZED') ? 20 : 2); });
    assert.equal(urls[0], '/api/reports/attendance');
    assert.equal(renderer.root.findAllByProps({ type: 'checkbox' }).length, 2); // No drafts.
    assert.deepEqual(renderer.root.findByType('select').findAllByType('option').map((option) => option.props.value), ['', 'FINALIZED', 'COMPLETED']);
    await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: 'FINALIZED' } }));
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[0].props.onChange());
    assert.equal(urls.length, 1);
    await act(async () => button(renderer, 'Скачать CSV').props.onClick());
    assert.match(await downloaded.text(), /"Абонементы","Все абонементы"/);
    await act(async () => apply(renderer));
    assert.equal(urls.at(-1), '/api/reports/attendance?subscriptionId=7&status=FINALIZED');
    assert.equal(state().attendanceReport.data.summary.totalVisits, 20);
    await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: 'COMPLETED' } }));
    await act(async () => button(renderer, 'Все абонементы').props.onClick());
    await act(async () => button(renderer, 'Скачать CSV').props.onClick());
    const csv = await downloaded.text();
    assert.match(csv, /"Абонементы","Ноябрь"/);
    assert.match(csv, /"Статус абонементов","Рассчитанные"/);
    assert.match(csv, /"Всего посещений","20"/);
    assert.doesNotMatch(csv, /Абонемент #7|Завершённые/);
    await act(async () => renderer.unmount());
});

test('failed apply keeps report and applied labels and restores drafts; empty intersections retain the selected name', async () => {
    const { renderer, state } = await mount(async (url) => {
        if (url.includes('COMPLETED')) throw new Error('Ошибка отчёта');
        return report();
    });
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[0].props.onChange());
    await act(async () => apply(renderer));
    const previous = state().attendanceReport;
    await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: 'COMPLETED' } }));
    await act(async () => apply(renderer));
    assert.equal(state().attendanceReport, previous);
    assert.equal(renderer.root.findByType('select').props.value, '');
    assert.match(text(renderer.root.findByProps({ role: 'alert' })), /Ошибка отчёта/);
    assert.match(text(renderer.root), /Показано: Ноябрь/);
    await act(async () => renderer.unmount());
    let empty;
    await act(async () => { empty = create(h(AttendanceReports, {
        reportState: { data: { ...report(), subscriptions: [] }, subscriptionIds: [7], status: 'COMPLETED' },
        loadReport: async () => report(), subscriptions,
    })); });
    assert.match(text(empty.root), /Показано: Ноябрь/);
    assert.match(text(empty.root), /Абонементы не найдены/);
    await act(async () => empty.unmount());
});

test('loading disables apply/filter/export and retains previous snapshot until the response arrives', async () => {
    const request = deferred();
    let calls = 0;
    const { renderer } = await mount(async () => ++calls === 1 ? report() : request.promise);
    let loading;
    await act(async () => { loading = apply(renderer); });
    assert.equal(button(renderer, 'Скачать CSV').props.disabled, true);
    assert.equal(button(renderer, 'Показать').props.disabled, true);
    assert.equal(renderer.root.findByType('select').props.disabled, true);
    assert.ok(renderer.root.findAllByProps({ type: 'checkbox' }).every((item) => item.props.disabled));
    assert.ok(renderer.root.findByType(AttendanceReportTable));
    await act(async () => { request.resolve(report(3)); await loading; });
    assert.equal(button(renderer, 'Скачать CSV').props.disabled, false);
    await act(async () => renderer.unmount());
});

test('hook copies applied IDs, aborts obsolete requests and suppresses their responses/errors', async () => {
    const requests = [];
    let state, renderer;
    const api = async (url, options) => {
        const request = deferred(); requests.push({ ...request, url, signal: options.signal }); return request.promise;
    };
    function Probe() { state = useAttendanceReport(api); return null; }
    await act(async () => { renderer = create(h(Probe)); });
    const ids = [7, 8];
    let old, latest;
    await act(async () => { old = state.loadAttendanceReport(ids, 'COMPLETED'); });
    ids.push(9);
    await act(async () => { latest = state.loadAttendanceReport([7], 'FINALIZED'); });
    assert.equal(requests[0].url, '/api/reports/attendance?subscriptionId=7&subscriptionId=8&status=COMPLETED');
    assert.equal(requests[0].signal.aborted, true);
    await act(async () => { requests[1].resolve(report(20)); await latest; });
    await act(async () => { requests[0].reject(new Error('Устаревшая ошибка')); await old; });
    assert.equal(state.attendanceReport.data.summary.totalVisits, 20);
    assert.deepEqual(state.attendanceReport.subscriptionIds, [7]);
    assert.equal(state.attendanceReport.status, 'FINALIZED');
    assert.equal(state.attendanceError, '');
    assert.equal(state.attendanceLoading, false);
    let staleSuccess, newer;
    await act(async () => { staleSuccess = state.loadAttendanceReport(); newer = state.loadAttendanceReport([8]); });
    await act(async () => { requests[3].resolve(report(4)); await newer; requests[2].resolve(report(99)); await staleSuccess; });
    assert.equal(state.attendanceReport.data.summary.totalVisits, 4);
    let cancelled;
    await act(async () => { cancelled = state.loadAttendanceReport(); });
    await act(async () => renderer.unmount());
    assert.equal(requests[4].signal.aborted, true);
    requests[4].resolve(report(100));
    await cancelled;
});

test('report choice and applied attendance filters survive leaving reports and switching through financial reports', async () => {
    const urls = [];
    const api = async (url) => { urls.push(url); return report(); };
    let renderer;
    const financial = { loadReport: async () => null, reportState: null };
    function Harness({ visible = true }) {
        const attendance = useAttendanceReport(api);
        const [reportType, setReportType] = useState('financial');
        return visible ? h(ReportsPage, { reportType, setReportType, financial,
            attendance: { reportState: attendance.attendanceReport, loading: attendance.attendanceLoading, error: attendance.attendanceError, loadReport: attendance.loadAttendanceReport }, subscriptions }) : null;
    }
    await act(async () => { renderer = create(h(Harness)); });
    assert.match(text(renderer.root), /Финансовая отчётность/);
    await act(async () => button(renderer, 'Посещения').props.onClick());
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[1].props.onChange());
    await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: 'COMPLETED' } }));
    await act(async () => apply(renderer));
    await act(async () => renderer.update(h(Harness, { visible: false })));
    await act(async () => renderer.update(h(Harness, { visible: true })));
    assert.equal(button(renderer, 'Посещения').props['aria-pressed'], true);
    assert.equal(renderer.root.findByType('select').props.value, 'COMPLETED');
    assert.equal(urls.at(-1), '/api/reports/attendance?subscriptionId=8&status=COMPLETED');
    await act(async () => button(renderer, 'Финансы').props.onClick());
    assert.match(text(renderer.root), /Финансовая отчётность/);
    await act(async () => button(renderer, 'Посещения').props.onClick());
    assert.equal(renderer.root.findByType('select').props.value, 'COMPLETED');
    await act(async () => renderer.unmount());
});

test('attendance CSV escapes formulas quotes/newlines, distinguishes future cells and contains no internal IDs', async () => {
    const malicious = structuredClone(report());
    const row = malicious.subscriptions[0];
    row.subscriptionId = 900001;
    row.title = '=BAD,"Клуб"';
    row.people[0].personId = 900002;
    row.people[0].personName = '=SUM(1+1)';
    row.people[1].personName = '\t+SUM(2+2)';
    row.people[2].personName = 'Имя,"кавычки"\nВторая строка';
    row.people.push(...[' +CMD()', '-CMD()', '@CMD()', '\rCMD()', '\nCMD()'].map((personName, index) => ({
        personId: 900010 + index, personName, currentMember: false, memberVisits: 0, guestVisits: 0, totalVisits: 0, visits: [],
    })));
    const csv = attendanceReportCsv(malicious, '=FILTER()', 'Рассчитанные');
    assert.ok(csv.startsWith('\uFEFF'));
    assert.match(csv, /"'=FILTER\(\)"/);
    assert.match(csv, /'=BAD,""Клуб""/);
    assert.match(csv, /"'=SUM\(1\+1\)"/);
    assert.match(csv, /"'\t\+SUM\(2\+2\)"/);
    assert.match(csv, /"' \+CMD\(\)"/);
    assert.match(csv, /"'-CMD\(\)"/);
    assert.match(csv, /"'@CMD\(\)"/);
    assert.match(csv, /Имя,""кавычки""\nВторая строка/);
    assert.match(csv, /Ещё не началась/);
    assert.match(csv, /Нет отметки посещения/);
    assert.match(csv, /Не отмечено/);
    assert.doesNotMatch(csv, /900001|900002|900010|subscriptionId|personId|sessionId/);
    let renderer;
    await act(async () => { renderer = create(h(AttendanceReportTable, { subscription: { ...subscription,
        people: [{ ...subscription.people[0], personName: '<img src=x onerror=alert(1)>' }] } })); });
    assert.equal(renderer.root.findAllByType('img').length, 0);
    assert.match(text(renderer.root), /<img src=x onerror=alert\(1\)>/);
    await act(async () => renderer.unmount());
});
