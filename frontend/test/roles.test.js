import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import App from '../src/App.jsx';
import Users from '../src/features/account/Users.jsx';
import People from '../src/features/people/People.jsx';
import Rates from '../src/features/rates/Rates.jsx';
import Subscriptions from '../src/features/subscriptions/Subscriptions.jsx';
import LedgerDetails from '../src/features/ledger/LedgerDetails.jsx';

const event = () => ({ preventDefault() {} });
const render = async (element) => {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
};
const text = (renderer) => JSON.stringify(renderer.toJSON());
const button = (renderer, label) => renderer.root.findAllByType('button').find((node) => node.children.includes(label));
const subscription = {
    id: 1, title: 'Ноябрь', description: 'Описание', capacity: 8, status: 'FINALIZED',
    members: [{ id: 1, personId: 1, personName: 'Алия', usesKayak: true }],
    sessions: [{ id: 10, startsAt: '2026-11-05T19:00:00' }],
};

test('read-only screens preserve club details and navigation while removing mutation forms', async () => {
    const people = await render(h(People, { readOnly: true, people: [{ id: 1, fullName: 'Алия', phone: '700', note: 'Заметка', active: true }] }));
    assert.equal(button(people, 'Добавить каякера'), undefined);
    await act(async () => people.root.findByProps({ className: 'person' }).props.onClick());
    assert.ok(text(people).includes('Алия'));
    assert.ok(text(people).includes('Активен'));
    assert.ok(!text(people).includes('Заметка'));
    assert.ok(!text(people).includes('700'));
    assert.ok(!text(people).includes('Телефон не указан'));
    assert.ok(!text(people).includes('Заметок нет'));
    assert.equal(people.root.findAllByType('form').length, 0);

    const rates = await render(h(Rates, { readOnly: true, rates: [{ id: 1, code: 'POOL_RENTAL', amount: 500, effectiveFrom: '2026-11-01' }] }));
    assert.equal(rates.root.findAllByType('button').length, 0);
    assert.equal(rates.root.findAllByType('form').length, 0);

    const subscriptions = await render(h(Subscriptions, {
        readOnly: true, people: [], subscriptions: [subscription],
    }));
    await act(async () => subscriptions.root.findByProps({ className: 'subscription ' }).props.onClick());
    assert.ok(text(subscriptions).includes('Описание'));
    assert.equal(button(subscriptions, 'Редактировать'), undefined);
    assert.equal(button(subscriptions, 'Создать абонемент'), undefined);
    assert.equal(button(subscriptions, 'Завершить абонемент'), undefined);
    assert.equal(button(subscriptions, 'Перекличка'), undefined);
    assert.ok(text(subscriptions).includes('Расписание'));
});

test('read-only people search uses names only even when private details are supplied', async () => {
    const renderer = await render(h(People, { readOnly: true, people: [
        { id: 1, fullName: 'Алия', phone: '+77001234567', note: 'Личный комментарий', active: true },
    ] }));
    const search = renderer.root.findByProps({ placeholder: 'Имя' });
    assert.ok(!text(renderer).includes('+77001234567'));
    await act(async () => search.props.onChange({ target: { value: '1234567' } }));
    assert.equal(renderer.root.findAllByProps({ className: 'person' }).length, 0);
    await act(async () => search.props.onChange({ target: { value: 'АЛИЯ' } }));
    assert.equal(renderer.root.findAllByProps({ className: 'person' }).length, 1);
    await act(async () => renderer.root.findByProps({ className: 'person' }).props.onClick());
    assert.ok(!text(renderer).includes('Личный комментарий'));
    assert.ok(!text(renderer).includes('+77001234567'));
});

test('admin people search and editing retain contacts and personal notes', async () => {
    const renderer = await render(h(People, { people: [
        { id: 1, fullName: 'Алия', phone: '+77001234567', note: 'Личный комментарий', active: true },
    ] }));
    assert.ok(text(renderer).includes('+77001234567'));
    await act(async () => renderer.root.findByProps({ placeholder: 'Имя или телефон' }).props.onChange({ target: { value: '1234567' } }));
    assert.equal(renderer.root.findAllByProps({ className: 'person' }).length, 1);
    await act(async () => renderer.root.findByProps({ className: 'person' }).props.onClick());
    const editForm = renderer.root.findAllByType('form').find((form) => form.findAllByProps({ name: 'note' }).length);
    assert.equal(editForm.findByProps({ name: 'phone' }).props.defaultValue, '+77001234567');
    assert.equal(renderer.root.findByProps({ name: 'note' }).props.defaultValue, 'Личный комментарий');
    assert.ok(button(renderer, 'Сохранить'));
});


test('read-only ledger keeps charges payments and allocations without write controls', async () => {
    const renderer = await render(h(LedgerDetails, {
        readOnly: true, balance: { charged: 500, paid: 300, balance: -200 }, subscriptions: [],
        charges: [{ id: 1, description: 'Тренировка', type: 'MANUAL', amount: 500, allocated: 100, chargedAt: '2026-11-01' }],
        payments: [{ id: 1, amount: 300, paidAt: '2026-11-01', method: 'CASH', note: 'Оплата', allocations: [{ id: 1, chargeDescription: 'Тренировка', amount: 100 }] }],
    }));
    assert.ok(text(renderer).includes('Тренировка'));
    assert.ok(text(renderer).includes('Оплата'));
    assert.ok(text(renderer).includes('Не распределено:'));
    assert.equal(renderer.root.findAllByType('button').length, 0);
});

test('admin creates accounts with selected role, handles duplicates, and clears successful password', async () => {
    const calls = [];
    let fail = true;
    const api = async (path, options) => {
        if (!options) return [{ id: 1, login: 'admin', role: 'ADMIN' }];
        calls.push([path, JSON.parse(options.body)]);
        if (fail) throw Error('Пользователь с таким логином уже существует.');
        return { id: 2, login: 'new-admin', role: 'ADMIN' };
    };
    const renderer = await render(h(Users, { api }));
    assert.equal(renderer.root.findByProps({ name: 'role' }).props.value, 'READ_ONLY');
    await act(async () => {
        renderer.root.findByProps({ name: 'login' }).props.onChange({ target: { value: 'new-admin' } });
        renderer.root.findByProps({ name: 'password' }).props.onChange({ target: { value: 'long-password' } });
        renderer.root.findByProps({ name: 'role' }).props.onChange({ target: { value: 'ADMIN' } });
    });
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.ok(text(renderer).includes('уже существует'));
    assert.equal(renderer.root.findByProps({ name: 'password' }).props.value, 'long-password');
    fail = false;
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.deepEqual(calls[1], ['/api/users', { login: 'new-admin', password: 'long-password', role: 'ADMIN' }]);
    assert.equal(renderer.root.findByProps({ name: 'password' }).props.value, '');
    assert.equal(renderer.root.findByProps({ name: 'role' }).props.value, 'READ_ONLY');
    assert.ok(text(renderer).includes('создана'));
});

test('account creation waits for the initial user list so a stale GET cannot overwrite the created user', async () => {
    let finishLoading;
    let posts = 0;
    const api = async (path, options) => {
        if (!options) return new Promise((resolve) => { finishLoading = resolve; });
        posts++;
        return { id: 2, login: 'member', role: 'READ_ONLY' };
    };
    const renderer = await render(h(Users, { api }));
    assert.equal(button(renderer, 'Создать учётную запись').props.disabled, true);
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(posts, 0);
    await act(async () => finishLoading([{ id: 1, login: 'admin', role: 'ADMIN' }]));
    assert.equal(button(renderer, 'Создать учётную запись').props.disabled, false);
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(posts, 1);
    assert.ok(text(renderer).includes('member'));
    assert.ok(text(renderer).includes('admin'));
});

test('login obtains role from server and logout resets admin navigation before member signs in', async (t) => {
    const previousFetch = globalThis.fetch;
    const previousDocument = globalThis.document;
    globalThis.document = { addEventListener() {}, removeEventListener() {} };
    let role = 'ADMIN';
    const requests = [];
    globalThis.fetch = async (path) => {
        requests.push(path);
        const payload = path === '/api/account' ? { id: role === 'ADMIN' ? 1 : 2, login: role === 'ADMIN' ? 'admin' : 'member', role }
            : path === '/api/reports/financial' ? { summary: {}, subscriptions: [], unassignedCharges: [] } : [];
        return { ok: true, status: 200, json: async () => payload };
    };
    t.after(() => { globalThis.fetch = previousFetch; globalThis.document = previousDocument; });
    const renderer = await render(h(App));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(requests[0], '/api/account');
    assert.ok(button(renderer, 'Пользователи'));
    assert.ok(button(renderer, 'Пароль'));
    await act(async () => button(renderer, 'Пользователи').props.onClick());
    await act(async () => button(renderer, 'Выйти').props.onClick());
    role = 'READ_ONLY';
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(button(renderer, 'Пользователи'), undefined);
    assert.equal(button(renderer, 'Пароль'), undefined);
    assert.ok(text(renderer).includes('Только чтение'));
    await act(async () => button(renderer, 'Отчёты').props.onClick());
    assert.ok(button(renderer, 'Скачать CSV'));
    assert.equal(button(renderer, 'Скачать CSV').props.disabled, false);
    assert.ok(button(renderer, 'Показать'));
    await act(async () => renderer.unmount());
});
