import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Modal from '../src/components/Modal.jsx';
import EditSubscriptionDialog from '../src/features/subscriptions/EditSubscriptionDialog.jsx';
import AllocationForm from '../src/features/ledger/AllocationForm.jsx';
import SubscriptionReportTable from '../src/features/reports/SubscriptionReportTable.jsx';
import usePersonBalance from '../src/hooks/usePersonBalance.js';

const event = () => ({ preventDefault() {} });
const render = async (element, options) => {
    let renderer;
    await act(async () => {
        renderer = create(element, options);
    });
    return renderer;
};
const finalized = {
    id: 1,
    status: 'FINALIZED',
    capacity: 1,
    title: 'Ноябрь',
    description: '',
    sessions: [{ id: 10, startsAt: '2026-11-10T19:00:00' }],
    members: [{ personId: 2, personName: 'Каякер', usesSkirt: false, usesPaddle: false, usesKayak: true }],
};

test('modal state closes suppress native callbacks while user close stops propagation', async () => {
    let closed = 0,
        stopped = 0;
    const node = {
        open: false,
        showModal() {
            this.open = true;
        },
        close() {
            this.open = false;
        },
    };
    const renderer = await render(h(Modal, { open: true, title: 'Диалог', onClose: () => closed++ }), {
        createNodeMock: (element) => (element.type === 'dialog' ? node : null),
    });
    assert.equal(node.open, true);
    await act(async () =>
        renderer.update(h(Modal, { open: false, title: 'Диалог', onClose: () => closed++ })),
    );
    assert.equal(node.open, false);
    renderer.root.findByType('dialog').props.onClose({
        stopPropagation() {
            stopped++;
        },
    });
    assert.equal(closed, 0);
    renderer.root.findByType('dialog').props.onClose({
        stopPropagation() {
            stopped++;
        },
    });
    assert.equal(closed, 1);
    assert.equal(stopped, 2);
});

test('finalized edits require preview and explicit confirmation before saving', async (t) => {
    const saved = [];
    let closed = 0,
        confirmations = 0,
        accept = false;
    const previousWindow = globalThis.window;
    globalThis.window = {
        confirm() {
            confirmations++;
            return accept;
        },
    };
    t.after(() => {
        if (previousWindow === undefined) delete globalThis.window;
        else globalThis.window = previousWindow;
    });
    const renderer = await render(
        h(EditSubscriptionDialog, {
            selected: finalized,
            people: [],
            submitting: false,
            preview: async () => ({
                requiresConfirmation: true,
                allocatedPayments: [{ paidAt: '2026-11-01', allocatedAmount: 500 }],
            }),
            update: async (_, values) => {
                saved.push(values);
                return true;
            },
            onClose: () => closed++,
        }),
    );
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(confirmations, 1);
    assert.equal(saved.length, 0);
    accept = true;
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(saved.length, 1);
    assert.equal(saved[0].confirmAllocatedPayments, true);
    assert.deepEqual(saved[0].startsAt, ['2026-11-10T19:00']);
    assert.equal(closed, 1);
});

test('failed preview keeps edit dialog open and never submits the update', async () => {
    let submitted = false;
    const renderer = await render(
        h(EditSubscriptionDialog, {
            selected: finalized,
            people: [],
            preview: async () => null,
            update: async () => {
                submitted = true;
            },
            onClose: () => assert.fail('closed'),
        }),
    );
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(submitted, false);
});


test('existing payment allocation rejects overflow and closes only after successful allocation', async () => {
    const saved = [];
    let closed = 0;
    const renderer = await render(
        h(AllocationForm, {
            payment: { id: 3 },
            outstanding: [{ id: 5, description: 'Бассейн', amount: 100, allocated: 40 }],
            paymentRemaining: () => 80,
            allocate: async (...args) => {
                saved.push(args);
                return true;
            },
            close: () => closed++,
        }),
    );
    await act(async () => renderer.root.findByType('input').props.onChange({ target: { value: '61' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.equal(saved.length, 0);
    assert.equal(renderer.root.findByType('input').props['aria-invalid'], true);
    await act(async () => renderer.root.findByType('input').props.onChange({ target: { value: '60' } }));
    await act(async () => renderer.root.findByType('form').props.onSubmit(event()));
    assert.deepEqual(saved, [[3, 5, 60]]);
    assert.equal(closed, 1);
});

test('report subscription buttons expose native keyboard semantics and toggle payment details', async () => {
    const renderer = await render(
        h(SubscriptionReportTable, {
            report: {
                subscriptions: [
                    {
                        ...finalized,
                        subscriptionId: 1,
                        payments: [
                            {
                                paymentId: 2,
                                personId: 2,
                                chargeId: 3,
                                personName: 'Каякер',
                                paidAt: '2026-11-01',
                                allocated: 10,
                                method: 'CASH',
                            },
                        ],
                    },
                ],
            },
        }),
    );
    const button = () => renderer.root.findByProps({ className: 'report-subscription-row' }).findByType('button');
    assert.equal(button().props.type, 'button');
    assert.equal(button().props.onKeyDown, undefined);
    assert.equal(button().props['aria-expanded'], false);
    await act(async () => button().props.onClick());
    assert.equal(button().props['aria-expanded'], true);
    assert.equal(renderer.root.findAllByType('table').length, 2);
    await act(async () => button().props.onClick());
    assert.equal(button().props['aria-expanded'], false);
});

test('selected balance keeps refreshing with the latest API after credentials change', async () => {
    let state;
    const firstApi = async () => ({ version: 1 });
    const secondApi = async () => ({ version: 2 });
    function Harness({ api }) {
        state = usePersonBalance(api, () => {});
        return null;
    }
    const renderer = await render(h(Harness, { api: firstApi }));
    await act(async () => state.selectPerson(7));
    assert.equal(state.balance.version, 1);
    await act(async () => renderer.update(h(Harness, { api: secondApi })));
    await act(async () => state.loadBalance(7));
    assert.equal(state.personId, 7);
    assert.equal(state.balance.version, 2);
});
