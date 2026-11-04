import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import EditChargeDialogs from '../src/features/ledger/EditChargeDialogs.jsx';
import Ledger from '../src/features/ledger/Ledger.jsx';
import Modal from '../src/components/Modal.jsx';
import { createLedgerActions } from '../src/features/ledger/ledgerActions.js';

const charge = { id: 3, type: 'MANUAL', amount: 500, allocated: 0, chargedAt: '2026-11-02', description: 'Ошибочное начисление', subscriptionId: null };
const button = (renderer, label) => (renderer.root || renderer).findAllByType('button').find((node) => node.children.includes(label));
const confirmation = (renderer) => renderer.root.findAllByType(Modal).find((node) => node.props.title === 'Подтвердить удаление начисления');
async function render(element) {
    let renderer;
    await act(async () => { renderer = create(element); });
    return renderer;
}

test('charge deletion requires confirmation and cancellation keeps the charge editor draft', async () => {
    const removed = [];
    let closed = 0;
    const renderer = await render(h(EditChargeDialogs, {
        editingCharge: charge, personId: 7, subscriptions: [],
        deleteCharge: async (...args) => { removed.push(args.slice(0, 2)); return true; },
        onClose: () => closed++,
    }));
    await act(async () => renderer.root.findByProps({ name: 'description' }).props.onChange({ target: { value: 'Черновик' } }));
    assert.equal(confirmation(renderer).props.open, false);
    await act(async () => button(renderer, 'Удалить начисление').props.onClick());
    assert.equal(confirmation(renderer).props.open, true);
    assert.equal(removed.length, 0);
    assert.ok(JSON.stringify(renderer.toJSON()).includes('Ошибочное начисление'));
    await act(async () => button(confirmation(renderer), 'Отмена').props.onClick());
    assert.equal(confirmation(renderer).props.open, false);
    assert.equal(renderer.root.findByProps({ name: 'description' }).props.value, 'Черновик');
    assert.equal(closed, 0);
    await act(async () => button(renderer, 'Удалить начисление').props.onClick());
    await act(async () => button(renderer, 'Подтвердить удаление').props.onClick());
    assert.deepEqual(removed, [[charge, 7]]);
    assert.equal(closed, 1);
});

test('allocated charge explains how to cancel allocation and cannot be deleted', async () => {
    let removed = 0;
    const renderer = await render(h(EditChargeDialogs, {
        editingCharge: { ...charge, allocated: 200 }, personId: 7, subscriptions: [],
        deleteCharge: async () => removed++, onClose() {},
    }));
    await act(async () => button(renderer, 'Удалить начисление').props.onClick());
    assert.equal(button(renderer, 'Подтвердить удаление').props.disabled, true);
    assert.ok(renderer.root.findByProps({ role: 'alert' }).children.join('').includes('Сначала отмените распределение'));
    await act(async () => button(renderer, 'Подтвердить удаление').props.onClick());
    assert.equal(removed, 0);
});

test('failed charge deletion remains open and displays the server explanation', async () => {
    let closed = 0;
    const message = 'Сначала отмените распределение оплаты.';
    const renderer = await render(h(EditChargeDialogs, {
        editingCharge: charge, personId: 7, subscriptions: [],
        deleteCharge: async (_, __, onError) => { onError(message); return false; },
        onClose: () => closed++,
    }));
    await act(async () => button(renderer, 'Удалить начисление').props.onClick());
    await act(async () => button(renderer, 'Подтвердить удаление').props.onClick());
    assert.equal(confirmation(renderer).props.open, true);
    assert.equal(renderer.root.findByProps({ role: 'alert' }).children.join(''), message);
    assert.equal(closed, 0);
    assert.equal(button(renderer, 'Подтвердить удаление').props.disabled, false);
});

test('charge deletion blocks repeat clicks while its request is pending', async () => {
    let finish, removed = 0;
    const props = {
        editingCharge: charge, personId: 7, subscriptions: [], onClose() {},
        deleteCharge: async () => { removed++; return new Promise((resolve) => { finish = resolve; }); },
    };
    const renderer = await render(h(EditChargeDialogs, props));
    await act(async () => button(renderer, 'Удалить начисление').props.onClick());
    let pending;
    await act(async () => { pending = button(renderer, 'Подтвердить удаление').props.onClick(); });
    assert.equal(button(renderer, 'Подтвердить удаление').props.disabled, true);
    assert.equal(confirmation(renderer).props.closeDisabled, true);
    await act(async () => button(renderer, 'Подтвердить удаление').props.onClick());
    assert.equal(removed, 1);
    await act(async () => { finish(true); await pending; });
    await act(async () => renderer.update(h(EditChargeDialogs, { ...props, submitting: true })));
    assert.equal(button(renderer, 'Удалить начисление').props.disabled, true);
    assert.equal(button(renderer, 'Подтвердить удаление').props.disabled, true);
});

test('ledger wires deletion into charge editing and read-only ledger has no delete action', async () => {
    const remove = async () => true;
    const props = {
        personId: 7, personBalances: [{ personId: 7, fullName: 'Каякер' }], subscriptions: [],
        balance: { charged: 500, paid: 0, balance: 500, charges: [charge], payments: [] },
        deleteCharge: remove,
    };
    const renderer = await render(h(Ledger, props));
    await act(async () => renderer.root.findByProps({ className: 'ledger-item' }).props.onClick());
    assert.equal(renderer.root.findByType(EditChargeDialogs).props.deleteCharge, remove);
    assert.ok(button(renderer, 'Удалить начисление'));
    const readOnly = await render(h(Ledger, { ...props, readOnly: true }));
    assert.equal(button(readOnly, 'Удалить начисление'), undefined);
    assert.equal(readOnly.root.findByProps({ className: 'ledger-item' }).props.onClick, undefined);
    assert.equal(readOnly.root.findAllByType(EditChargeDialogs).length, 0);
});

test('pending confirmation cannot be dismissed through Escape, backdrop or close button', async () => {
    let nativeCloses = 0, cancelled = false;
    let renderer;
    await act(async () => {
        renderer = create(h(Modal, { open: true, closeDisabled: true, title: 'Удаление', onClose() {} }), {
            createNodeMock: () => ({ open: true, close() { nativeCloses++; } }),
        });
    });
    const dialog = renderer.root.findByType('dialog');
    dialog.props.onCancel({ preventDefault() { cancelled = true; } });
    const target = {};
    dialog.props.onClick({ target, currentTarget: target });
    button(renderer, 'Закрыть').props.onClick();
    assert.equal(cancelled, true);
    assert.equal(nativeCloses, 0);
    assert.equal(button(renderer, 'Закрыть').props.disabled, true);
});

test('delete action calls scoped API, refreshes both balances and forwards failures', async () => {
    const requests = [], refreshes = [], errors = [];
    let fail = false;
    const actions = createLedgerActions({
        api: async (path, options) => { if (fail) throw Error('Начисление уже оплачено.'); requests.push([path, options]); },
        run: async (action) => { try { await action(); return true; } catch { return false; } },
        loadBalance: async (...args) => refreshes.push(args), refreshBalances: async () => refreshes.push('all'),
    });
    assert.equal(await actions.deleteCharge(charge, 7), true);
    assert.deepEqual(requests, [['/api/charges/3', { method: 'DELETE' }]]);
    assert.deepEqual(refreshes, [[7, { keepCurrent: true }], 'all']);
    fail = true;
    assert.equal(await actions.deleteCharge(charge, 7, (message) => errors.push(message)), false);
    assert.deepEqual(errors, ['Начисление уже оплачено.']);
    assert.equal(refreshes.length, 2);
});
