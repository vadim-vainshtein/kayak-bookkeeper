import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import AttendanceDialog from '../src/features/subscriptions/AttendanceDialog.jsx';
import SubscriptionDetails from '../src/features/subscriptions/SubscriptionDetails.jsx';
import Subscriptions from '../src/features/subscriptions/Subscriptions.jsx';
import useClubData from '../src/hooks/useClubData.js';

const session = { id: 10, startsAt: '2026-11-06T19:00:00', attendanceAvailable: true };
const member = { personId: 1, personName: 'Участник', guest: false, present: false, amount: null, allocated: 0 };
const roster = { sessionId: 10, revision: 0, available: true, rateDate: '2026-11-07', dropInAmount: 250, people: [member] };
const people = [{ id: 1, fullName: 'Участник', active: true }, { id: 2, fullName: 'Гость', active: true }, { id: 3, fullName: 'Ещё гость', active: true }];
const text = (node) => typeof node === 'string' ? node : (node?.children || []).map(text).join('');
const button = (renderer, label) => renderer.root.findAllByType('button').find((item) => text(item) === label);
const inputFor = (renderer, label) => renderer.root.findAllByType('label').find((item) => text(item).startsWith(label)).findByType('input');
async function render(options = {}) {
    const calls = [];
    const api = options.api || (async (path, request) => { calls.push([path, request]); return roster; });
    let renderer;
    await act(async () => { renderer = create(h(AttendanceDialog, {
        session, people, api, onSaved: async () => {}, onClose() {}, ...options,
    })); });
    return { renderer, calls };
}

test('attendance defaults to absent, adds guests without capacity limits, and saves edited prices', async () => {
    let closed = 0;
    const { renderer, calls } = await render({ onClose: () => closed++ });
    assert.equal(renderer.root.findByProps({ type: 'checkbox' }).props.checked, false);
    await act(async () => renderer.root.findByProps({ type: 'checkbox' }).props.onChange({ target: { checked: true } }));
    for (const id of ['2', '3']) {
        await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: id } }));
        await act(async () => button(renderer, 'Добавить выбранного гостя').props.onClick());
    }
    assert.equal(inputFor(renderer, 'Начисление, ₸ · Гость').props.value, '250');
    await act(async () => inputFor(renderer, 'Начисление, ₸ · Гость').props.onChange({ target: { value: '175' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    assert.equal(closed, 1);
    assert.equal(calls[1][0], '/api/sessions/10/attendance');
    assert.deepEqual(JSON.parse(calls[1][1].body), { revision: 0, memberIds: [1], guests: [{ personId: 2, amount: 175 }, { personId: 3, amount: 250 }] });
});

test('new guest is created only together with attendance and its editable charge', async () => {
    const { renderer, calls } = await render();
    await act(async () => inputFor(renderer, 'Имя нового гостя').props.onChange({ target: { value: 'Новый человек' } }));
    await act(async () => inputFor(renderer, 'Телефон нового гостя').props.onChange({ target: { value: '700' } }));
    await act(async () => button(renderer, 'Добавить нового гостя').props.onClick());
    assert.equal(calls.length, 1);
    await act(async () => inputFor(renderer, 'Начисление, ₸ · Новый человек').props.onChange({ target: { value: '0' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    assert.deepEqual(JSON.parse(calls[1][1].body).guests, [{ fullName: 'Новый человек', phone: '700', amount: 0 }]);
});

test('missing tariff requires an explicit guest amount and failed save retains the draft', async () => {
    const requests = [];
    const { renderer } = await render({ api: async (path, request) => {
        if (!request) return { ...roster, dropInAmount: null };
        requests.push(request);
        throw new Error('Посещения уже изменены. Откройте окно снова.');
    } });
    await act(async () => renderer.root.findByType('select').props.onChange({ target: { value: '2' } }));
    await act(async () => button(renderer, 'Добавить выбранного гостя').props.onClick());
    await act(async () => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    assert.equal(requests.length, 0);
    assert.match(text(renderer.root.findByProps({ role: 'alert' })), /Укажите целую сумму/);
    await act(async () => inputFor(renderer, 'Начисление, ₸ · Гость').props.onChange({ target: { value: '120' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} }));
    assert.equal(requests.length, 1);
    assert.equal(inputFor(renderer, 'Начисление, ₸ · Гость').props.value, '120');
    assert.match(text(renderer.root.findByProps({ role: 'alert' })), /уже изменены/);
});

test('saved guest keeps its price when tariff changes and allocated guests cannot be removed', async () => {
    const { renderer } = await render({ api: async () => ({ ...roster, revision: 1, dropInAmount: 500,
        people: [member, { personId: 2, personName: 'Гость', guest: true, present: true, amount: 175, allocated: 100 }] }) });
    assert.equal(inputFor(renderer, 'Начисление, ₸ · Гость').props.value, '175');
    assert.equal(inputFor(renderer, 'Начисление, ₸ · Гость').props.min, 100);
    assert.equal(renderer.root.findByProps({ 'aria-label': 'Убрать гостя Гость' }).props.disabled, true);
    assert.deepEqual(renderer.root.findAllByType('option').map((item) => item.props.value), ['', 3]);
});

test('read-only attendance exposes saved marks without mutation controls', async () => {
    const { renderer } = await render({ readOnly: true });
    assert.equal(renderer.root.findByType('fieldset').props.disabled, true);
    assert.equal(renderer.root.findAllByType('select').length, 0);
    assert.equal(button(renderer, 'Сохранить посещения'), undefined);
});

test('pending attendance save blocks repeat requests and dismissal', async () => {
    let resolve;
    let writes = 0;
    const { renderer } = await render({ api: async (path, request) => {
        if (!request) return roster;
        writes++;
        return new Promise((done) => { resolve = done; });
    } });
    const submit = renderer.root.findByType('form').props.onSubmit;
    let first;
    await act(async () => { first = submit({ preventDefault() {} }); });
    await act(async () => submit({ preventDefault() {} }));
    assert.equal(writes, 1);
    assert.equal(renderer.root.findByProps({ 'aria-label': 'Закрыть окно' }).props.disabled, true);
    await act(async () => { resolve(roster); await first; });
});

test('schedule uses server eligibility and shows the actual attendance count', async () => {
    let selected;
    let renderer;
    const subscription = { status: 'FINALIZED', capacity: 1, members: [], sessions: [
        { ...session, attendedCount: 4, attendanceRecorded: true },
        { id: 11, startsAt: '2026-11-08T19:00:00', attendanceAvailable: false, attendedCount: 0 },
    ] };
    await act(async () => { renderer = create(h(SubscriptionDetails, { selected: subscription, openAttendance: (value) => { selected = value; } })); });
    const attendanceButtons = renderer.root.findAllByType('button').filter((item) => text(item) === 'Отметить посещения');
    assert.equal(attendanceButtons.length, 1);
    assert.match(text(renderer.root), /Пришли: 4/);
    await act(async () => attendanceButtons[0].props.onClick());
    assert.equal(selected.id, 10);
});

test('subscription reopening, window focus and bounded polling refresh server eligibility without local time assumptions', async (t) => {
    const originalWindow = globalThis.window;
    const originalInterval = globalThis.setInterval;
    const originalClear = globalThis.clearInterval;
    let focus, tick, interval, cleared = 0, requests = 0, eligible = false, renderer;
    globalThis.window = { addEventListener: (name, fn) => { if (name === 'focus') focus = fn; },
        removeEventListener: () => { focus = undefined; } };
    globalThis.setInterval = (fn, delay) => { tick = fn; interval = delay; return 42; };
    globalThis.clearInterval = () => { cleared++; };
    t.after(async () => {
        await act(async () => renderer.unmount());
        globalThis.window = originalWindow;
        globalThis.setInterval = originalInterval;
        globalThis.clearInterval = originalClear;
    });
    const props = () => ({
        people: [], subscriptions: [{ id: 1, title: 'Группа', status: 'FINALIZED', capacity: 1, members: [],
            sessions: [{ id: 10, startsAt: '2099-11-07T12:00:00', attendanceAvailable: eligible }] }],
        refreshData,
    });
    async function refreshData() {
        requests++;
        if (eligible) renderer.update(h(Subscriptions, props()));
    }
    await act(async () => { renderer = create(h(Subscriptions, props())); });
    assert.equal(requests, 1);
    assert.equal(interval, 30_000);
    await act(async () => renderer.root.findAllByType('button').find((item) => item.props.className?.startsWith('subscription ')).props.onClick());
    assert.equal(requests, 2);
    assert.equal(button(renderer, 'Отметить посещения'), undefined);
    await act(async () => focus());
    assert.equal(requests, 3);
    eligible = true;
    await act(async () => tick());
    assert.equal(requests, 4);
    assert.ok(button(renderer, 'Отметить посещения'));
    assert.ok(cleared >= 1);
});

test('an older club-data refresh cannot overwrite newer attendance counts or keep loading stuck', async () => {
    let data, renderer, calls = 0;
    const resolvers = [[], []];
    const api = async () => new Promise((resolve) => resolvers[Math.floor(calls++ / 4)].push(resolve));
    const setError = () => {};
    function Probe() { data = useClubData(api, null, setError); return null; }
    await act(async () => { renderer = create(h(Probe)); });
    let oldLoad, newLoad;
    await act(async () => { oldLoad = data.load(); newLoad = data.load({ quiet: true }); });
    await act(async () => {
        resolvers[1].forEach((resolve, index) => resolve(index === 2 ? [{ id: 1, sessions: [{ attendedCount: 3 }] }] : []));
        await newLoad;
    });
    assert.equal(data.loading, false);
    assert.equal(data.subscriptions[0].sessions[0].attendedCount, 3);
    await act(async () => {
        resolvers[0].forEach((resolve, index) => resolve(index === 2 ? [{ id: 1, sessions: [{ attendedCount: 0 }] }] : []));
        await oldLoad;
    });
    assert.equal(data.subscriptions[0].sessions[0].attendedCount, 3);
    await act(async () => renderer.unmount());
});
