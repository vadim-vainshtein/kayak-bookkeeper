import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import PersonBalanceTable from '../src/features/ledger/PersonBalanceTable.jsx';
import { balanceFilterOptions, filteredPersonBalances, UNASSIGNED } from '../src/features/ledger/subscriptionBalances.js';
import { money } from '../src/shared/format.js';

const groups = [
    { subscriptionId: 10, title: 'Ноябрь', charged: 20000, allocated: 15000, balance: 5000 },
    { subscriptionId: 20, title: 'Декабрь', charged: 8000, allocated: 6000, balance: 2000 },
    { subscriptionId: null, title: 'Без абонемента', charged: 2000, allocated: 1000, balance: 1000 },
];
const people = [
    { personId: 1, fullName: 'Иван', active: true, charged: 30000, paid: 25000, unallocated: 3000, balance: 5000, subscriptionBalances: groups },
    { personId: 2, fullName: 'Иван', active: false, charged: 8000, paid: 4000, unallocated: 0, balance: 4000, subscriptionBalances: [
        { subscriptionId: 20, title: 'Декабрь', charged: 8000, allocated: 4000, balance: 4000 },
    ] },
    { personId: 3, fullName: 'Аванс', active: true, charged: 0, paid: 400, unallocated: 400, balance: -400, subscriptionBalances: [] },
    { personId: 4, fullName: 'Без операций', active: true, charged: 0, paid: 0, unallocated: 0, balance: 0, subscriptionBalances: [] },
];
const text = (node) => node.children.map((child) => typeof child === 'string' ? child : text(child)).join('');
const rows = (renderer, className) => renderer.root.findAllByProps({ className });
const cells = (row) => row.findAllByType('td').map(text);
const checkbox = (renderer, title) => renderer.root.findAllByType('label').find((label) => text(label) === title).findByType('input');
const render = async (props = {}) => {
    let renderer;
    await act(async () => { renderer = create(h(PersonBalanceTable, { personBalances: people, selectPerson() {}, ...props })); });
    return renderer;
};
const toggle = async (renderer, title) => act(async () => checkbox(renderer, title).props.onChange());

test('subscription filters aggregate only selected charge groups, separating unassigned from free cash', () => {
    assert.equal(filteredPersonBalances(people, []), people);
    const november = filteredPersonBalances(people, ['10']);
    assert.equal(november.length, 1);
    assert.deepEqual([november[0].charged, november[0].paid, november[0].balance], [20000, 15000, 5000]);
    assert.equal(november[0].unallocated, 3000, 'free cash remains context and does not reduce the selected debt');
    const mixed = filteredPersonBalances(people, ['10', UNASSIGNED]);
    assert.deepEqual([mixed[0].charged, mixed[0].paid, mixed[0].balance], [22000, 16000, 6000]);
    assert.deepEqual(filteredPersonBalances(people, [UNASSIGNED])[0].subscriptionBalances, [groups[2]]);
    assert.deepEqual(filteredPersonBalances(people, ['999']), []);
    assert.deepEqual(balanceFilterOptions([{ id: 10, title: 'Ноябрь' }, { id: 30 }], people).map((option) => option.key).sort(), ['10', '20', '30', UNASSIGNED]);
});

test('independent disclosure buttons keep name navigation and reconcile child payment amounts', async () => {
    const selected = [];
    const renderer = await render({ selectPerson: (id) => selected.push(id) });
    const arrows = () => rows(renderer, 'balance-expand');
    assert.deepEqual(arrows().map((arrow) => arrow.props.disabled), [false, false, false, true]);
    assert.ok(rows(renderer, 'balance-breakdown').every((body) => body.props.hidden));
    await act(async () => arrows()[0].props.onClick());
    await act(async () => arrows()[1].props.onClick());
    assert.deepEqual(arrows().slice(0, 2).map((arrow) => arrow.props['aria-expanded']), [true, true]);
    assert.equal(rows(renderer, 'balance-breakdown')[0].props.id, arrows()[0].props['aria-controls']);
    const details = rows(renderer, 'balance-breakdown')[0];
    assert.deepEqual(details.findAllByProps({ className: 'balance-subscription-row' }).map(cells), [
        ['Ноябрь', money(20000), money(15000), '—', money(-5000)],
        ['Декабрь', money(8000), money(6000), '—', money(-2000)],
        ['Без абонемента', money(2000), money(1000), '—', money(-1000)],
    ]);
    assert.deepEqual(cells(details.findByProps({ className: 'balance-unallocated-row' })), ['Нераспределённые оплаты', '—', money(3000), money(3000), `+${money(3000)}`]);
    await act(async () => rows(renderer, 'person-balance-button')[0].props.onClick());
    assert.deepEqual(selected, [1]);
    assert.equal(arrows()[0].props['aria-expanded'], true);
    await act(async () => arrows()[0].props.onClick());
    assert.equal(arrows()[1].props['aria-expanded'], true);
    await act(async () => arrows()[2].props.onClick());
    assert.equal(rows(renderer, 'balance-breakdown')[2].props.hidden, false);
    assert.equal(rows(renderer, 'balance-breakdown')[2].findAllByProps({ className: 'balance-subscription-row' }).length, 0);
});

test('multi-selection recomputes parent rows, hides free-payment rows, and resets to original totals', async () => {
    const renderer = await render();
    await toggle(renderer, 'Ноябрь');
    let summaries = rows(renderer, 'balance-person-row');
    assert.equal(summaries.length, 1);
    assert.deepEqual(cells(summaries[0]).slice(1), [money(20000), money(15000), '—', money(-5000)]);
    assert.ok(text(summaries[0]).includes('По выбранным абонементам'));
    assert.ok(text(summaries[0]).includes(`На счёте не распределено: ${money(3000)}`));
    assert.equal(rows(renderer, 'balance-unallocated-row').length, 0);
    await toggle(renderer, 'Декабрь');
    summaries = rows(renderer, 'balance-person-row');
    assert.equal(summaries.length, 2, 'people sharing a name remain separate');
    assert.deepEqual(cells(summaries[0]).slice(1), [money(28000), money(21000), '—', money(-7000)]);
    await toggle(renderer, 'Без абонемента');
    assert.deepEqual(cells(rows(renderer, 'balance-person-row')[0]).slice(1), [money(30000), money(22000), '—', money(-8000)]);
    const reset = renderer.root.findAllByType('button').find((button) => text(button) === 'Все абонементы');
    await act(async () => reset.props.onClick());
    assert.equal(rows(renderer, 'balance-person-row').length, 4);
    assert.deepEqual(cells(rows(renderer, 'balance-person-row')[0]).slice(1), [money(30000), money(25000), money(3000), money(-5000)]);
});

test('search narrows choices without changing current selection or totals, including unnamed options', async () => {
    const renderer = await render({ subscriptions: [{ id: 30, title: null }] });
    await toggle(renderer, 'Ноябрь');
    const search = renderer.root.findByProps({ type: 'search' });
    await act(async () => search.props.onChange({ target: { value: '  ДЕКАБРЬ  ' } }));
    assert.deepEqual(renderer.root.findAllByProps({ type: 'checkbox' }).map((input) => input.props.checked), [false]);
    assert.equal(rows(renderer, 'balance-person-row').length, 1);
    await toggle(renderer, 'Декабрь');
    assert.equal(rows(renderer, 'balance-person-row').length, 2);
    await act(async () => search.props.onChange({ target: { value: '' } }));
    await toggle(renderer, 'Ноябрь');
    await toggle(renderer, 'Декабрь');
    await toggle(renderer, 'Абонемент #30');
    assert.equal(rows(renderer, 'balance-person-row').length, 0);
    assert.ok(text(renderer.root).includes('Нет начислений по выбранным абонементам.'));
    await act(async () => search.props.onChange({ target: { value: 'не существует' } }));
    assert.ok(text(renderer.root).includes('Абонементы не найдены.'));
});

test('balance refresh updates selected totals and free-credit context without losing disclosure state', async () => {
    const renderer = await render();
    await toggle(renderer, 'Ноябрь');
    await act(async () => rows(renderer, 'balance-expand')[0].props.onClick());
    const updated = [{ ...people[0], unallocated: 1000, subscriptionBalances: [{ ...groups[0], allocated: 17000, balance: 3000 }] }];
    await act(async () => renderer.update(h(PersonBalanceTable, { personBalances: updated, selectPerson() {} })));
    assert.deepEqual(cells(rows(renderer, 'balance-person-row')[0]).slice(1), [money(20000), money(17000), '—', money(-3000)]);
    assert.equal(rows(renderer, 'balance-breakdown')[0].props.hidden, false);
    assert.ok(text(rows(renderer, 'balance-person-row')[0]).includes(`На счёте не распределено: ${money(1000)}`));
});
