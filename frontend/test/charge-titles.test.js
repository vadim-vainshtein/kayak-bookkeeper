import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Ledger from '../src/features/ledger/Ledger.jsx';
import LedgerDetails from '../src/features/ledger/LedgerDetails.jsx';
import AddPaymentDialog from '../src/features/ledger/AddPaymentDialog.jsx';
import AllocationForm from '../src/features/ledger/AllocationForm.jsx';
import { chargeTitle } from '../src/features/ledger/chargeTitle.js';

const subscriptions = [{ id: 1, title: 'Воскресенье - ноябрь' }, { id: 2, title: 'Воскресенье - декабрь' }];
const charges = [
    { id: 10, type: 'POOL_SHARE', description: 'Доля аренды бассейна за абонемент', subscriptionId: 1 },
    { id: 11, type: 'SKIRT_RENT', description: 'Аренда юбки за абонемент', subscriptionId: 1 },
    { id: 12, type: 'PADDLE_RENT', description: 'Аренда весла за абонемент', subscriptionId: 1 },
    { id: 13, type: 'KAYAK_RENT', description: 'Аренда каяка за абонемент', subscriptionId: 1 },
    { id: 14, type: 'POOL_SHARE', description: 'Доля аренды бассейна за абонемент', subscriptionId: 2 },
].map((charge) => ({ ...charge, amount: 1000, allocated: 0, chargedAt: '2026-11-01' }));
const expected = ['Бассейн', 'Юбка', 'Весло', 'Каяк'].map((title) => `${title} · Воскресенье - ноябрь`);
expected.push('Бассейн · Воскресенье - декабрь');

async function render(element) {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
}

test('charge titles identify subscriptions, retain custom descriptions and handle missing subscription data', () => {
    assert.deepEqual(charges.map((charge) => chargeTitle(charge, subscriptions)), expected);
    assert.equal(chargeTitle({ type: 'MANUAL', description: 'Индивидуальное занятие', subscriptionId: 1 }, subscriptions),
        'Индивидуальное занятие · Воскресенье - ноябрь');
    assert.equal(chargeTitle({ type: 'MANUAL', description: 'Возврат' }, subscriptions), 'Возврат');
    assert.equal(chargeTitle(charges[0]), 'Бассейн · Абонемент #1');
    assert.equal(chargeTitle(charges[0], [{ id: 1, title: '' }]), 'Бассейн · Абонемент #1');
});

test('ledger headings and allocation history show compact names with subscriptions', async () => {
    const renderer = await render(h(LedgerDetails, {
        balance: { charged: 5000, paid: 200, balance: 4800 }, subscriptions, charges,
        payments: [{ id: 1, amount: 200, paidAt: '2026-11-01', method: 'CASH', allocations: [
            { id: 1, chargeId: 10, chargeDescription: charges[0].description, amount: 100 },
            { id: 2, chargeId: 99, chargeDescription: 'Другое начисление', amount: 100 },
        ] }],
    }));
    assert.deepEqual(renderer.root.findAllByProps({ className: 'ledger-item' })
        .map((item) => item.findByType('strong').children.join('')), expected);
    const allocations = renderer.root.findAllByProps({ className: 'allocation' });
    assert.ok(allocations[0].children.join('').includes(expected[0]));
    assert.ok(allocations[1].children.join('').includes('Другое начисление'));
});

test('both payment allocation selectors receive subscription titles through Ledger', async () => {
    const renderer = await render(h(Ledger, {
        personBalances: [{ personId: 7, fullName: 'Каякер', active: true }], personId: 7, subscriptions,
        balance: { charged: 5000, paid: 200, balance: 4800, charges,
            payments: [{ id: 1, amount: 200, method: 'CASH', paidAt: '2026-11-01', allocations: [] }] },
    }));
    const assertOptions = (component) => {
        const options = component.findByProps({ name: 'chargeId' }).findAllByType('option')
            .filter((option) => option.props.value !== '');
        assert.equal(options.length, expected.length);
        options.forEach((option, index) => {
            assert.ok(option.children.join('').startsWith(`${expected[index]} · осталось `));
            assert.equal(option.props.value, charges[index].id);
        });
    };
    assertOptions(renderer.root.findByType(AddPaymentDialog));
    const allocateButton = renderer.root.findAllByType('button').find((item) => item.children.includes('Распределить'));
    await act(async () => allocateButton.props.onClick());
    assertOptions(renderer.root.findByType(AllocationForm));
});
