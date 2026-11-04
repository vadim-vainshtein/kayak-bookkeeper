import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Ledger from '../src/features/ledger/Ledger.jsx';
import EditPaymentDialog from '../src/features/ledger/EditPaymentDialog.jsx';
import AllocationForm from '../src/features/ledger/AllocationForm.jsx';
import { createLedgerActions } from '../src/features/ledger/ledgerActions.js';
import { createBalanceSelection } from '../src/features/ledger/balanceSelection.js';

const payment = {
    id: 4, amount: 100, paidAt: '2026-11-02', method: 'TRANSFER', note: 'Банк',
    allocations: [{ id: 9, chargeId: 3, chargeDescription: 'Бассейн', amount: 60 }],
};
const charges = [{ id: 3, type: 'POOL_SHARE', subscriptionId: 2, amount: 100, allocated: 60, chargedAt: '2026-11-02' }];
const subscriptions = [{ id: 2, title: 'Ноябрь' }];
const button = (renderer, label) => renderer.root.findAllByType('button').find((item) => item.children.includes(label));
const paymentItem = (renderer) => renderer.root.findByProps({ className: 'ledger-item payment-item' });
const event = () => ({ preventDefault() {} });
async function render(element) {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
}

test('payment edit rejects amounts below allocated total and saves the exact minimum', async () => {
    const saved = [];
    let closed = false;
    const renderer = await render(h(EditPaymentDialog, {
        payment, charges, subscriptions, savePayment: async (...args) => { saved.push(args.slice(0, 2)); return true; },
        onClose: () => { closed = true; },
    }));
    assert.equal(renderer.root.findByProps({ name: 'amount' }).props.min, 60);
    for (const amount of ['59', '0', '-1', '1.5', '']) {
        await act(async () => renderer.root.findByProps({ name: 'amount' }).props.onChange({ target: { value: amount } }));
        await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
        assert.equal(saved.length, 0);
        assert.ok(renderer.root.findByProps({ role: 'alert' }).children.join('').includes('не меньше'));
    }
    await act(async () => renderer.root.findByProps({ name: 'amount' }).props.onChange({ target: { value: '60' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.deepEqual(saved, [[4, { amount: 60, paidAt: payment.paidAt, note: payment.note }]]);
    assert.equal(closed, true);
});

test('payment date and comment are editable, required and clearable', async () => {
    const saved = [];
    const renderer = await render(h(EditPaymentDialog, {
        payment, charges, subscriptions, onClose() {},
        savePayment: async (_, values) => { saved.push(values); return true; },
    }));
    const change = async (name, value) => act(async () => renderer.root.findByProps({ name }).props.onChange({ target: { value } }));
    assert.equal(renderer.root.findByProps({ name: 'paidAt' }).props.required, true);
    assert.equal(renderer.root.findByProps({ name: 'note' }).props.maxLength, '1000');
    await change('paidAt', '');
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(saved.length, 0);
    await change('paidAt', '2026-11-04');
    await change('note', 'a'.repeat(1001));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(saved.length, 0);
    await change('note', 'Исправленный комментарий');
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    await change('note', '');
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.deepEqual(saved, [
        { amount: 100, paidAt: '2026-11-04', note: 'Исправленный комментарий' },
        { amount: 100, paidAt: '2026-11-04', note: '' },
    ]);
});

test('payment item opens editing while the separate allocation action opens only allocation', async () => {
    const renderer = await render(h(Ledger, {
        personId: 7, personBalances: [{ personId: 7, fullName: 'Каякер' }], subscriptions,
        balance: { charged: 100, paid: 100, balance: 0, charges, payments: [payment] },
    }));
    const item = paymentItem(renderer);
    assert.equal(item.type, 'button');
    assert.equal(item.props.type, 'button');
    assert.equal(button(renderer, 'Редактировать'), undefined);
    const allocation = button(renderer, 'Распределить');
    assert.equal(item.findAllByType('button').includes(allocation), false);
    await act(async () => allocation.props.onClick());
    assert.equal(renderer.root.findAllByType(EditPaymentDialog).length, 0);
    assert.equal(renderer.root.findAllByType(AllocationForm).length, 1);
    await act(async () => renderer.root.findByType(AllocationForm).props.close());
    await act(async () => paymentItem(renderer).props.onClick());
    assert.equal(renderer.root.findByType(EditPaymentDialog).props.payment.id, payment.id);
    assert.equal(renderer.root.findAllByType(AllocationForm).length, 0);
});

test('payment dialog displays server failures locally and keeps failed edits open', async () => {
    let closed = false;
    const serverMessage = 'Сумма оплаты не может быть меньше распределённой суммы: 80 ₸.';
    const renderer = await render(h(EditPaymentDialog, {
        payment, charges, subscriptions,
        savePayment: async (_, __, onError) => { onError(serverMessage); return false; },
        cancelAllocation: async (_, __, onError) => { onError('Распределение уже изменено.'); return false; },
        onClose: () => { closed = true; },
    }));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(renderer.root.findByProps({ role: 'alert' }).children.join(''), serverMessage);
    assert.equal(closed, false);
    await act(async () => button(renderer, 'Отменить распределение').props.onClick());
    assert.equal(renderer.root.findByProps({ role: 'alert' }).children.join(''), 'Распределение уже изменено.');
    assert.equal(closed, false);
});

test('cancelling an allocation refreshes the open payment and its current amount minimum', async () => {
    let renderer;
    const cancelled = [];
    const props = {
        personId: 7, personBalances: [{ personId: 7, fullName: 'Каякер' }], subscriptions,
        balance: { charged: 100, paid: 100, balance: 0, charges, payments: [payment] },
        savePayment: async () => true,
        cancelAllocation: async (paymentId, allocationId) => {
            cancelled.push([paymentId, allocationId]);
            renderer.update(h(Ledger, {
                ...props,
                balance: { ...props.balance, charges: [{ ...charges[0], allocated: 0 }], payments: [{ ...payment, allocations: [] }] },
            }));
            return true;
        },
    };
    renderer = await render(h(Ledger, props));
    await act(async () => paymentItem(renderer).props.onClick());
    const dialog = renderer.root.findByType(EditPaymentDialog);
    assert.equal(dialog.findByProps({ name: 'amount' }).props.min, 60);
    assert.ok(button(renderer, 'Отменить распределение').props['aria-label'].includes('Бассейн · Ноябрь'));
    await act(async () => button(renderer, 'Отменить распределение').props.onClick());
    assert.deepEqual(cancelled, [[4, 9]]);
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'amount' }).props.min, 1);
    assert.equal(button(renderer, 'Отменить распределение'), undefined);
    assert.ok(JSON.stringify(renderer.toJSON()).includes('У оплаты нет распределений.'));
    assert.ok(button(renderer, 'Распределить'));
});

test('real payment refresh retains the editor and unsaved amount while its balance request is pending', async () => {
    let renderer, finishRefresh, requests = 0, currentBalance;
    const base = {
        personId: 7, personBalances: [{ personId: 7, fullName: 'Каякер' }], subscriptions,
    };
    const initialBalance = { charged: 100, paid: 100, balance: 0, charges, payments: [payment] };
    const api = async (path) => {
        if (path === '/api/people/7/balance') {
            if (++requests === 1) return initialBalance;
            return new Promise((resolve) => { finishRefresh = resolve; });
        }
    };
    const selection = createBalanceSelection({
        api, setPersonId() {}, setError(error) { assert.fail(error); },
        setBalance(balance) {
            currentBalance = balance;
            renderer?.update(h(Ledger, { ...base, ...actions, balance }));
        },
    });
    const actions = createLedgerActions({
        api, personId: 7, loadBalance: selection.loadBalance, refreshBalances: async () => {},
        run: async (action) => { await action(); return true; },
    });
    await selection.selectPerson(7);
    renderer = await render(h(Ledger, { ...base, ...actions, balance: currentBalance }));
    await act(async () => paymentItem(renderer).props.onClick());
    await act(async () => renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'amount' }).props.onChange({ target: { value: '77' } }));
    await act(async () => renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'paidAt' }).props.onChange({ target: { value: '2026-11-04' } }));
    await act(async () => renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'note' }).props.onChange({ target: { value: 'Несохранённый комментарий' } }));
    let cancellation;
    await act(async () => { cancellation = button(renderer, 'Отменить распределение').props.onClick(); });
    assert.equal(currentBalance, initialBalance);
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'amount' }).props.value, '77');
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'paidAt' }).props.value, '2026-11-04');
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'note' }).props.value, 'Несохранённый комментарий');
    await act(async () => {
        finishRefresh({ ...initialBalance, charges: [{ ...charges[0], allocated: 0 }], payments: [{ ...payment, allocations: [] }] });
        await cancellation;
    });
    const input = renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'amount' });
    assert.equal(input.props.min, 1);
    assert.equal(input.props.value, '77');
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'paidAt' }).props.value, '2026-11-04');
    assert.equal(renderer.root.findByType(EditPaymentDialog).findByProps({ name: 'note' }).props.value, 'Несохранённый комментарий');
    assert.ok(JSON.stringify(renderer.toJSON()).includes('Распределение отменено.'));
});

test('keeping the editor during refresh still exposes failed balance reloads', async () => {
    let fail = false, current;
    const selection = createBalanceSelection({
        api: async () => { if (fail) throw Error('Баланс не обновлён.'); return payment; },
        setPersonId() {}, setBalance(value) { current = value; }, setError() {},
    });
    await selection.selectPerson(7);
    fail = true;
    await assert.rejects(selection.loadBalance(7, { keepCurrent: true }), /Баланс не обновлён/);
    assert.equal(current, payment);
});

test('payment controls stay disabled during mutation and absent for read-only ledger', async () => {
    const renderer = await render(h(EditPaymentDialog, { payment, charges, subscriptions, submitting: true }));
    assert.equal(button(renderer, 'Сохранить оплату').props.disabled, true);
    assert.equal(renderer.root.findByProps({ name: 'paidAt' }).props.disabled, true);
    assert.equal(renderer.root.findByProps({ name: 'note' }).props.disabled, true);
    assert.equal(button(renderer, 'Отменить распределение').props.disabled, true);
    const ledger = await render(h(Ledger, {
        readOnly: true, personId: 7, personBalances: [{ personId: 7 }], subscriptions,
        balance: { charged: 100, paid: 100, balance: 0, charges, payments: [payment] },
    }));
    assert.equal(button(ledger, 'Редактировать'), undefined);
    assert.equal(paymentItem(ledger).type, 'div');
    assert.equal(paymentItem(ledger).props.onClick, undefined);
    assert.equal(ledger.root.findAllByType(EditPaymentDialog).length, 0);
});

test('payment mutations use scoped API routes, refresh balances and forward server errors', async () => {
    const requests = [], refreshed = [], errors = [];
    let fail = false;
    const actions = createLedgerActions({
        personId: 7,
        api: async (path, options) => {
            if (fail) throw Error('Распределённая сумма изменилась.');
            requests.push([path, options]);
        },
        run: async (action) => { try { await action(); return true; } catch { return false; } },
        loadBalance: async (id) => refreshed.push(id), refreshBalances: async () => refreshed.push('all'),
    });
    assert.equal(await actions.savePayment(4, { amount: 60, paidAt: '2026-11-04', note: '' }), true);
    assert.equal(await actions.cancelAllocation(4, 9), true);
    assert.deepEqual(requests, [
        ['/api/payments/4', { method: 'PATCH', body: '{"amount":60,"paidAt":"2026-11-04","note":""}' }],
        ['/api/payments/4/allocations/9', { method: 'DELETE' }],
    ]);
    assert.deepEqual(refreshed, [7, 'all', 7, 'all']);
    fail = true;
    assert.equal(await actions.savePayment(4, { amount: 50 }, (message) => errors.push(message)), false);
    assert.equal(await actions.cancelAllocation(4, 9, (message) => errors.push(message)), false);
    assert.deepEqual(errors, ['Распределённая сумма изменилась.', 'Распределённая сумма изменилась.']);
    assert.equal(refreshed.length, 4);
});
