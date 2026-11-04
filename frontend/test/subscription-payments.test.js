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
    subscriptions: [1, 2].map((subscriptionId) => ({ subscriptionId, title: `Абонемент ${subscriptionId}`, status: 'FINALIZED', payments })),
};
const render = async () => {
    let renderer;
    await act(async () => { renderer = create(h(SubscriptionReportTable, { report })); });
    return renderer;
};
const rows = (renderer, className) => renderer.root.findAllByProps({ className });
const text = (node) => node.children.map((child) => typeof child === 'string' ? child : text(child)).join('');
const openSubscription = async (renderer, index = 0) => {
    await act(async () => rows(renderer, 'report-subscription-row')[index].props.onClick());
};
const controls = (details) => details.findAllByType('button');

test('subscription payments group by person ID, total all allocations, and reveal original payment details', async () => {
    const renderer = await render();
    await openSubscription(renderer);
    const people = () => rows(renderer, 'report-person-row');
    assert.equal(people().length, 2, 'different people with the same name stay separate');
    assert.match(text(people()[0]), new RegExp(money(8550)));
    assert.match(text(people()[1]), new RegExp(money(1300)));
    assert.equal(rows(renderer, 'report-person-details').length, 0);
    await act(async () => people()[0].props.onClick());
    assert.equal(people()[0].props['aria-expanded'], true);
    assert.equal(people()[1].props['aria-expanded'], false);
    const details = rows(renderer, 'report-person-details')[0];
    assert.equal(details.props.id, people()[0].props['aria-controls']);
    assert.equal(details.findAllByType('tbody')[0].findAllByType('tr').length, 3);
    for (const expected of [formatDate('2026-11-01'), formatDate('2026-11-03'), 'Наличные', 'Перевод', 'Половина абонемента', 'Бассейн', 'Аренда юбки', 'Аренда весла', money(7500), money(650), money(400)]) {
        assert.ok(text(details).includes(expected), expected);
    }
    let prevented = 0;
    await act(async () => people()[0].props.onKeyDown({ key: ' ', preventDefault() { prevented++; } }));
    assert.equal(people()[0].props['aria-expanded'], false);
    await act(async () => people()[1].props.onKeyDown({ key: 'Enter', preventDefault() { prevented++; } }));
    assert.equal(people()[1].props['aria-expanded'], true);
    assert.ok(text(rows(renderer, 'report-person-details')[0]).includes('Начисление #21'));
    assert.equal(prevented, 2);
});

test('expand all and collapse all affect only people in their subscription and follow individual toggles', async () => {
    const renderer = await render();
    await openSubscription(renderer, 0);
    await openSubscription(renderer, 1);
    const details = () => rows(renderer, 'report-payment-details');
    const people = (index) => details()[index].findAllByProps({ className: 'report-person-row' });
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, true]);
    await act(async () => controls(details()[0])[0].props.onClick());
    assert.ok(people(0).every((row) => row.props['aria-expanded']));
    assert.ok(people(1).every((row) => !row.props['aria-expanded']));
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [true, false]);
    assert.notEqual(people(0)[0].props['aria-controls'], people(1)[0].props['aria-controls']);
    await act(async () => people(0)[0].props.onClick());
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, false]);
    await act(async () => controls(details()[1])[0].props.onClick());
    await act(async () => controls(details()[0])[1].props.onClick());
    assert.ok(people(0).every((row) => !row.props['aria-expanded']));
    assert.ok(people(1).every((row) => row.props['aria-expanded']));
    assert.deepEqual(controls(details()[0]).map((button) => button.props.disabled), [false, true]);
});

test('subscription without allocated payments keeps its empty message without group controls', async () => {
    let renderer;
    await act(async () => { renderer = create(h(SubscriptionReportTable, { report: { subscriptions: [{ subscriptionId: 3, payments: [] }] } })); });
    await openSubscription(renderer);
    assert.ok(text(rows(renderer, 'report-payment-details')[0]).includes('На начисления этого абонемента оплат пока не распределено.'));
    assert.equal(renderer.root.findAllByType('button').length, 0);
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
