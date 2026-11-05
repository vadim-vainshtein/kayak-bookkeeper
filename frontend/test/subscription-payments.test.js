import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import SubscriptionReportTable from '../src/features/reports/SubscriptionReportTable.jsx';
import Reports from '../src/features/reports/Reports.jsx';
import { formatDate, money } from '../src/shared/format.js';

const payments = [
    { personId: 1, personName: 'Тамара', paymentId: 10, chargeId: 20, paidAt: '2026-11-01', method: 'CASH', allocated: 7500, paymentNote: 'Половина абонемента', chargeDescription: 'Бассейн' },
    { personId: 2, personName: 'Тамара', paymentId: 11, chargeId: 21, paidAt: '2026-11-02', method: 'TRANSFER', allocated: 1300 },
    { personId: 1, personName: 'Тамара', paymentId: 10, chargeId: 22, paidAt: '2026-11-01', method: 'CASH', allocated: 650, paymentNote: 'Половина абонемента', chargeDescription: 'Аренда юбки' },
    { personId: 1, personName: 'Тамара', paymentId: 12, chargeId: 23, paidAt: '2026-11-03', method: 'TRANSFER', allocated: 400, chargeDescription: 'Аренда весла' },
];
const report = {
    subscriptions: [1, 2].map((subscriptionId) => ({ subscriptionId, title: `Абонемент ${subscriptionId}`, status: 'FINALIZED', memberCount: 2, charged: 10000, allocatedPayments: 9850, outstanding: 150, poolRentalCost: 7000, clubProfit: 2850, cashBalance: 5850, payments })),
};
const render = async () => {
    let renderer;
    await act(async () => { renderer = create(h(SubscriptionReportTable, { report })); });
    return renderer;
};
const rows = (renderer, className) => renderer.root.findAllByProps({ className });
const visibleRows = (renderer, className) => rows(renderer, className).filter((row) => !row.props.hidden);
const text = (node) => node.children.map((child) => typeof child === 'string' ? child : text(child)).join('');
const arrow = (row) => row.findByProps({ className: 'balance-expand' });
const openSubscription = async (renderer, index = 0) => {
    await act(async () => arrow(rows(renderer, 'report-subscription-row')[index]).props.onClick());
};
const controls = (details) => details.findAllByType('button').filter((button) => button.props.className !== 'balance-expand');
const subscriptionControls = (renderer) => renderer.root.findByProps({ className: 'button-group report-subscription-controls' }).findAllByType('button');

test('report subscriptions use ledger disclosure buttons while numeric columns and semantic rows stay unchanged', async () => {
    const renderer = await render();
    const subscriptions = () => rows(renderer, 'report-subscription-row');
    const disclosure = () => subscriptions().map(arrow);
    const originalNumbers = subscriptions()[0].findAllByType('td').slice(1).map(text);
    assert.deepEqual(originalNumbers, ['2', money(10000), money(9850), money(150), money(7000), money(2850), money(5850)]);
    for (const row of subscriptions()) {
        assert.equal(row.props.role, undefined);
        assert.equal(row.props.tabIndex, undefined);
        assert.equal(row.props.onClick, undefined);
        assert.equal(row.props.onKeyDown, undefined);
        assert.ok(!text(row).includes('Показать оплаты'));
        const button = arrow(row);
        assert.equal(button.type, 'button');
        assert.equal(button.props.type, 'button');
        assert.equal(button.props.onKeyDown, undefined, 'Enter and Space use native button semantics');
        assert.equal(button.props['aria-expanded'], false);
        assert.ok(button.props['aria-label'].includes('Развернуть оплаты: Абонемент'));
        assert.equal(text(button), '▸');
        const target = renderer.root.findByProps({ id: button.props['aria-controls'] });
        assert.equal(target.props.hidden, true, 'collapsed disclosure targets remain in the DOM');
    }
    await openSubscription(renderer, 0);
    await openSubscription(renderer, 1);
    assert.deepEqual(disclosure().map((button) => button.props['aria-expanded']), [true, true]);
    assert.deepEqual(disclosure().map(text), ['▾', '▾']);
    const firstId = disclosure()[0].props['aria-controls'];
    await openSubscription(renderer, 0);
    assert.deepEqual(disclosure().map((button) => button.props['aria-expanded']), [false, true]);
    await openSubscription(renderer, 0);
    assert.equal(disclosure()[0].props['aria-controls'], firstId);
    assert.deepEqual(subscriptions()[0].findAllByType('td').slice(1).map(text), originalNumbers);
});

test('subscription payments group by person ID and reveal original allocation details through separate arrows', async () => {
    const renderer = await render();
    await openSubscription(renderer);
    const people = () => rows(renderer, 'report-person-row');
    assert.equal(people().length, 2, 'different people with the same name stay separate');
    assert.match(text(people()[0]), new RegExp(money(8550)));
    assert.match(text(people()[1]), new RegExp(money(1300)));
    assert.equal(visibleRows(renderer, 'report-person-details').length, 0);
    for (const row of people()) {
        assert.equal(row.props.onClick, undefined);
        assert.equal(row.props.role, undefined);
        assert.equal(row.props.tabIndex, undefined);
        assert.equal(arrow(row).props.type, 'button');
        assert.equal(arrow(row).props.onKeyDown, undefined);
        assert.equal(text(arrow(row)), '▸');
    }
    await act(async () => arrow(people()[0]).props.onClick());
    assert.equal(arrow(people()[0]).props['aria-expanded'], true);
    assert.equal(arrow(people()[1]).props['aria-expanded'], false);
    const details = visibleRows(renderer, 'report-person-details')[0];
    assert.equal(details.props.id, arrow(people()[0]).props['aria-controls']);
    assert.equal(details.findAllByType('tbody')[0].findAllByType('tr').length, 3);
    for (const expected of [formatDate('2026-11-01'), formatDate('2026-11-03'), 'Наличные', 'Перевод', 'Половина абонемента', 'Бассейн', 'Аренда юбки', 'Аренда весла', money(7500), money(650), money(400)]) {
        assert.ok(text(details).includes(expected), expected);
    }
    await act(async () => arrow(people()[0]).props.onClick());
    assert.equal(arrow(people()[0]).props['aria-expanded'], false);
    await act(async () => arrow(people()[1]).props.onClick());
    assert.equal(arrow(people()[1]).props['aria-expanded'], true);
    assert.ok(text(visibleRows(renderer, 'report-person-details')[0]).includes('Начисление #21'));
});

test('expand all and collapse all affect only people in their subscription and follow individual toggles', async () => {
    const renderer = await render();
    await openSubscription(renderer, 0);
    await openSubscription(renderer, 1);
    const details = () => visibleRows(renderer, 'report-payment-details');
    const people = (index) => details()[index].findAllByProps({ className: 'report-person-row' });
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, true]);
    await act(async () => controls(details()[0])[0].props.onClick());
    assert.ok(people(0).every((row) => arrow(row).props['aria-expanded']));
    assert.ok(people(1).every((row) => !arrow(row).props['aria-expanded']));
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [true, false]);
    assert.notEqual(arrow(people(0)[0]).props['aria-controls'], arrow(people(1)[0]).props['aria-controls']);
    await act(async () => arrow(people(0)[0]).props.onClick());
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, false]);
    await act(async () => controls(details()[1])[0].props.onClick());
    await act(async () => controls(details()[0])[1].props.onClick());
    assert.ok(people(0).every((row) => !arrow(row).props['aria-expanded']));
    assert.ok(people(1).every((row) => arrow(row).props['aria-expanded']));
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, true]);
});

test('subscription without allocated payments keeps its empty message without group controls', async () => {
    let renderer;
    await act(async () => { renderer = create(h(SubscriptionReportTable, { report: { subscriptions: [{ subscriptionId: 3, payments: [] }] } })); });
    await openSubscription(renderer);
    const details = visibleRows(renderer, 'report-payment-details')[0];
    assert.ok(text(details).includes('На начисления этого абонемента оплат пока не распределено.'));
    assert.equal(controls(details).length, 0);
    assert.equal(renderer.root.findAllByType('button').length, 3, 'the subscription disclosure and subscription bulk controls remain available');
});

test('subscription bulk controls track individual rows and stay independent from nested people controls', async () => {
    const renderer = await render();
    const states = () => subscriptionControls(renderer).map((button) => button.props.disabled);
    const subscriptions = () => rows(renderer, 'report-subscription-row').map(arrow);
    assert.deepEqual(states(), [false, true]);
    await openSubscription(renderer, 0);
    assert.deepEqual(states(), [false, false]);
    await act(async () => subscriptionControls(renderer)[0].props.onClick());
    assert.deepEqual(states(), [true, false]);
    assert.ok(subscriptions().every((button) => button.props['aria-expanded']));
    assert.ok(rows(renderer, 'report-person-row').every((row) => !arrow(row).props['aria-expanded']), 'opening subscriptions does not open people');
    assert.equal(renderer.root.findAllByType('h3').length, 0, 'the redundant expanded heading is removed');
    const firstDetails = visibleRows(renderer, 'report-payment-details')[0];
    await act(async () => controls(firstDetails)[0].props.onClick());
    assert.deepEqual(states(), [true, false], 'nested expansion does not change outer states');
    assert.ok(firstDetails.findAllByProps({ className: 'report-person-row' }).every((row) => arrow(row).props['aria-expanded']));
    await openSubscription(renderer, 1);
    assert.deepEqual(states(), [false, false]);
    await act(async () => subscriptionControls(renderer)[1].props.onClick());
    assert.deepEqual(states(), [false, true]);
    assert.ok(subscriptions().every((button) => !button.props['aria-expanded']));
    assert.equal(visibleRows(renderer, 'report-payment-details').length, 0);
});

test('subscription bulk controls use only current report rows after filtering or refresh and disable on empty reports', async () => {
    const renderer = await render();
    const states = () => subscriptionControls(renderer).map((button) => button.props.disabled);
    await openSubscription(renderer, 0);
    const nextReport = { subscriptions: [report.subscriptions[1]] };
    await act(async () => renderer.update(h(SubscriptionReportTable, { report: nextReport })));
    assert.deepEqual(states(), [false, true], 'stale expanded subscription IDs do not enable collapse');
    await act(async () => subscriptionControls(renderer)[0].props.onClick());
    assert.deepEqual(states(), [true, false]);
    assert.equal(visibleRows(renderer, 'report-payment-details').length, 1);
    await act(async () => renderer.update(h(SubscriptionReportTable, { report: { subscriptions: [] } })));
    assert.deepEqual(states(), [true, true]);
    assert.ok(text(renderer.root).includes('Абонементы не найдены.'));
    await act(async () => renderer.update(h(SubscriptionReportTable, { report: { subscriptions: [report.subscriptions[0]] } })));
    assert.deepEqual(states(), [false, true], 'expand all recorded only the then-current visible IDs');
});

test('subscription bulk disclosure state is isolated between report instances', async () => {
    const first = await render();
    const second = await render();
    await act(async () => subscriptionControls(first)[0].props.onClick());
    assert.equal(visibleRows(first, 'report-payment-details').length, 2);
    assert.equal(visibleRows(second, 'report-payment-details').length, 0);
    assert.deepEqual(subscriptionControls(second).map((button) => button.props.disabled), [false, true]);
});

test('CSV download omits the selected unnamed subscription ID while preserving its UI label', async (t) => {
    const previousDocument = globalThis.document;
    let downloadedBlob;
    let clicks = 0;
    globalThis.document = { createElement: () => ({ click() { clicks++; } }) };
    t.after(() => { globalThis.document = previousDocument; });
    t.mock.method(URL, 'createObjectURL', (blob) => { downloadedBlob = blob; return 'blob:report'; });
    t.mock.method(URL, 'revokeObjectURL', () => {});
    let renderer;
    await act(async () => {
        renderer = create(h(Reports, {
            reportState: {
                subscriptionIds: [47007],
                data: { summary: {}, subscriptions: [], unassignedCharges: [] },
            },
            subscriptions: [{ id: 47007, title: null }],
            loadReport: async () => {},
        }));
    });
    t.after(() => act(() => renderer.unmount()));
    assert.ok(JSON.stringify(renderer.toJSON()).includes('Абонемент #47007'));
    await act(async () => renderer.root.findAllByType('button').find((button) => button.children.includes('Скачать CSV')).props.onClick());
    assert.equal(clicks, 1);
    const csv = await downloadedBlob.text();
    assert.ok(csv.includes('"Абонементы","Без названия"'));
    assert.doesNotMatch(csv, /47007|Абонемент #/);
});
