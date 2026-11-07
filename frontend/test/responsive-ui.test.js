import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h, useState } from 'react';
import { act, create } from 'react-test-renderer';
import Navigation, { MoreMenu } from '../src/components/Navigation.jsx';
import { NavigationLock } from '../src/components/NavigationLock.jsx';
import Modal from '../src/components/Modal.jsx';
import Subscriptions from '../src/features/subscriptions/Subscriptions.jsx';
import AttendanceDialog from '../src/features/subscriptions/AttendanceDialog.jsx';
import PersonBalanceTable from '../src/features/ledger/PersonBalanceTable.jsx';
import Ledger from '../src/features/ledger/Ledger.jsx';
import { money } from '../src/shared/format.js';

const text = (node) => typeof node === 'string' ? node : (node?.children || []).map(text).join('');
const button = (root, label) => root.findAllByType('button').find((node) => text(node) === label);
const render = async (element) => {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
};
const subscription = {
    id: 1, title: 'Ноябрь', status: 'FINALIZED', capacity: 8,
    members: [{ id: 7, personId: 7, personName: 'Каякер', usesKayak: true }],
    sessions: [{ id: 10, startsAt: '2026-11-03T19:00:00', attendanceAvailable: true }],
};
const entry = {
    personId: 7, fullName: 'Каякер', active: true, charged: 1000, paid: 700, unallocated: 200, balance: 300,
    subscriptionBalances: [{ subscriptionId: 1, title: 'Ноябрь', charged: 1000, allocated: 500, balance: 500 }],
};

test('mobile navigation exposes four destinations and More preserves secondary sections and role restrictions', async () => {
    const selected = [];
    const renderer = await render(h(Navigation, { tab: 'reports', go: (id) => selected.push(id), user: 'admin', logout() {} }));
    const bottom = renderer.root.findByProps({ className: 'bottom-navigation' });
    assert.deepEqual(bottom.findAllByType('button').map(text), ['Абонементы', 'Каякеры', 'Финансы', 'Ещё']);
    assert.equal(button(bottom, 'Ещё').props['aria-current'], 'page');
    await act(async () => button(bottom, 'Финансы').props.onClick());
    assert.deepEqual(selected, ['ledger']);
    const more = await render(h(MoreMenu, { go: (id) => selected.push(id), readOnly: true, logout() {} }));
    assert.deepEqual(more.root.findAllByType('button').map(text), ['Обзор', 'Отчёты', 'Тарифы', 'Выйти']);
    await act(async () => button(more.root, 'Отчёты').props.onClick());
    assert.equal(selected.at(-1), 'reports');
});

test('subscription detail tabs reset for another subscription and mobile Back returns to the existing list', async () => {
    const renderer = await render(h(Subscriptions, {
        subscriptions: [subscription, { ...subscription, id: 2, title: 'Декабрь' }], people: [], readOnly: true,
    }));
    const cards = () => renderer.root.findAllByType('button').filter((node) => node.props.className?.startsWith('subscription '));
    await act(async () => cards()[0].props.onClick());
    const members = button(renderer.root, 'Участники · 1');
    await act(async () => members.props.onClick());
    assert.equal(button(renderer.root, 'Расписание').props['aria-pressed'], false);
    assert.equal(renderer.root.findByProps({ id: members.props['aria-controls'] }).props.hidden, false);
    await act(async () => cards()[1].props.onClick());
    assert.equal(button(renderer.root, 'Расписание').props['aria-pressed'], true);
    await act(async () => renderer.root.findByProps({ className: 'back-button mobile-back' }).props.onClick());
    assert.equal(renderer.root.findAllByProps({ className: 'subscription-detail-panel' }).length, 0);
    assert.equal(cards().length, 2);
});

test('mobile balance cards keep free funds separate from selected group debt and share disclosures with desktop', async () => {
    const selected = [];
    const renderer = await render(h(PersonBalanceTable, { personBalances: [entry], subscriptions: [subscription], selectPerson: (id) => selected.push(id) }));
    const card = () => renderer.root.findByProps({ className: 'mobile-balance-card' });
    assert.ok(text(card()).includes(`Оплачено${money(700)}`));
    await act(async () => card().findByProps({ className: 'mobile-person-button' }).props.onClick());
    assert.deepEqual(selected, [7]);
    await act(async () => card().findByProps({ className: 'mobile-balance-expand' }).props.onClick());
    assert.equal(renderer.root.findByProps({ className: 'balance-expand' }).props['aria-expanded'], true);
    assert.equal(card().findByProps({ className: 'mobile-balance-breakdown' }).props.hidden, false);
    const filter = renderer.root.findAllByType('label').find((node) => text(node) === 'Ноябрь').findByType('input');
    await act(async () => filter.props.onChange());
    assert.ok(text(card()).includes(`Распределено${money(500)}`));
    assert.ok(text(card()).includes(`Долг: ${money(500)}`));
    assert.ok(text(card()).includes(`Не распределено: ${money(200)} · справочно`));
    assert.equal(card().findByProps({ className: 'mobile-balance-breakdown' }).findAllByType('section').length, 1);
});

test('ledger person opens as a page so payment has only one modal layer', async () => {
    const renderer = await render(h(Ledger, {
        personId: 7, personBalances: [entry], subscriptions: [], showPeople() {},
        balance: { charged: 1000, paid: 0, balance: 1000, charges: [], payments: [] },
    }));
    assert.equal(renderer.root.findAllByType(Modal).filter((node) => node.props.open).length, 0);
    await act(async () => button(renderer.root, 'Зарегистрировать оплату').props.onClick());
    assert.equal(renderer.root.findAllByType(Modal).filter((node) => node.props.open).length, 1);
    assert.ok(button(renderer.root, 'Все каякеры и балансы'));
});

test('attendance page locks all navigation during a pending save and preserves the draft after failure', async () => {
    let failSave;
    const api = async (path, options) => options
        ? new Promise((resolve, reject) => { failSave = reject; })
        : { available: true, revision: 1, rateDate: '2026-11-03', dropInAmount: 100, people: [{ personId: 7, personName: 'Каякер', guest: false, present: false }] };
    function Harness() {
        const [busy, setBusy] = useState(false);
        return h(NavigationLock.Provider, { value: setBusy },
            h(Navigation, { tab: 'subscriptions', busy, user: 'admin', go() {}, logout() {} }),
            h(AttendanceDialog, { page: true, session: subscription.sessions[0], people: [], api, onSaved() {}, onClose() {} }));
    }
    const renderer = await render(h(Harness));
    await act(async () => renderer.root.findByProps({ type: 'checkbox' }).props.onChange({ target: { checked: true } }));
    let saving;
    await act(async () => { saving = renderer.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
    const navigation = renderer.root.findByType(Navigation);
    assert.ok(navigation.findAllByType('button').every((node) => node.props.disabled));
    assert.equal(button(renderer.root, 'К абонементу').props.disabled, true);
    await act(async () => { failSave(new Error('Конфликт сохранения')); await saving; });
    assert.ok(navigation.findAllByType('button').every((node) => !node.props.disabled));
    assert.equal(renderer.root.findByProps({ type: 'checkbox' }).props.checked, true);
    assert.ok(text(renderer.root).includes('Конфликт сохранения'));
});
