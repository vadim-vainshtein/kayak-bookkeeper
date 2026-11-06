import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Ledger from '../src/features/ledger/Ledger.jsx';
import LedgerDetails from '../src/features/ledger/LedgerDetails.jsx';

const charges = [
    { id: 1, amount: 1000, allocated: 0, description: 'Неоплаченное' },
    { id: 2, amount: 1000, allocated: 400, description: 'Частично оплаченное' },
    { id: 3, amount: 1000, allocated: 1000, description: 'Оплаченное' },
].map((charge) => ({ ...charge, type: 'MANUAL', chargedAt: '2026-11-01', subscriptionId: 4 }));
const payments = [
    { id: 1, amount: 100, allocations: [] },
    { id: 2, amount: 1500, allocations: [{ id: 1, chargeId: 3, amount: 1000, chargeDescription: 'Старое название' }] },
    { id: 3, amount: 400, allocations: [{ id: 2, chargeId: 2, amount: 400 }] },
].map((payment) => ({ ...payment, method: 'CASH', paidAt: '2026-11-01' }));
const props = {
    subscriptions: [{ id: 4, title: 'Ноябрь' }], charges, payments,
    balance: { charged: 3000, paid: 2000, balance: -1000, charges, payments },
};
const text = (renderer) => JSON.stringify(renderer.toJSON());
const chargeItems = (renderer) => renderer.root.findAllByProps({ className: 'ledger-item' });
const paymentItems = (renderer) => renderer.root.findAllByProps({ className: 'ledger-item payment-item' });
const checkbox = (renderer) => renderer.root.findByType(LedgerDetails).findByProps({ type: 'checkbox' });
const toggle = async (renderer, checked) => act(async () => checkbox(renderer).props.onChange({ target: { checked } }));
async function render(element) {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
}

test('ledger defaults to outstanding entries, preserves totals and allocation titles, and toggles all entries', async () => {
    const renderer = await render(h(LedgerDetails, props));
    assert.equal(checkbox(renderer).props.checked, false);
    assert.deepEqual(chargeItems(renderer).map((item) => item.findByType('strong').children.join('')), [
        'Неоплаченное · Ноябрь', 'Частично оплаченное · Ноябрь',
    ]);
    assert.equal(paymentItems(renderer).length, 2);
    assert.ok(renderer.root.findByProps({ className: 'allocation' }).children.join('').includes('Оплаченное · Ноябрь'));
    const summary = renderer.root.findByProps({ className: 'balance-summary' }).findAllByType('strong').map((item) => item.children.join(''));
    await toggle(renderer, true);
    assert.equal(chargeItems(renderer).length, 3);
    assert.equal(paymentItems(renderer).length, 3);
    assert.deepEqual(renderer.root.findByProps({ className: 'balance-summary' }).findAllByType('strong').map((item) => item.children.join('')), summary);
    await toggle(renderer, false);
    assert.equal(chargeItems(renderer).length, 2);
    assert.equal(paymentItems(renderer).length, 2);
});

test('ledger distinguishes filtered-empty lists from absent entries and offers the flag in read-only mode', async () => {
    const renderer = await render(h(LedgerDetails, { ...props, readOnly: true, charges: [charges[2]], payments: [payments[2]] }));
    assert.ok(text(renderer).includes('Неоплаченных начислений нет.'));
    assert.ok(text(renderer).includes('Нераспределённых оплат нет.'));
    await toggle(renderer, true);
    assert.equal(chargeItems(renderer).length, 1);
    assert.equal(paymentItems(renderer).length, 1);
    assert.equal(renderer.root.findAllByType('button').length, 0);
    await act(async () => renderer.update(h(LedgerDetails, { ...props, charges: [], payments: [] })));
    assert.ok(text(renderer).includes('Начислений пока нет.'));
    assert.ok(text(renderer).includes('Оплат пока нет.'));
});

test('ledger updates outstanding lists after allocation and resets the flag when selecting or reopening a person', async () => {
    const base = { ...props, personId: 7, personBalances: [{ personId: 7, fullName: 'Каякер' }] };
    const renderer = await render(h(Ledger, base));
    await act(async () => renderer.update(h(Ledger, {
        ...base, balance: { ...props.balance, charges: charges.map((charge) => ({ ...charge, allocated: charge.amount })),
            payments: payments.map((payment) => ({ ...payment, allocations: [{ id: 9, chargeId: 3, amount: payment.amount }] })) },
    })));
    assert.equal(chargeItems(renderer).length, 0);
    assert.equal(paymentItems(renderer).length, 0);
    await toggle(renderer, true);
    await act(async () => renderer.update(h(Ledger, { ...base, personId: 8 })));
    assert.equal(checkbox(renderer).props.checked, false);
    await toggle(renderer, true);
    await act(async () => renderer.update(h(Ledger, { ...base, personId: null, balance: null })));
    await act(async () => renderer.update(h(Ledger, base)));
    assert.equal(checkbox(renderer).props.checked, false);
    assert.equal(chargeItems(renderer).length, 2);
});
