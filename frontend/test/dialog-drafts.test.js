import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Modal from '../src/components/Modal.jsx';
import Subscriptions from '../src/features/subscriptions/Subscriptions.jsx';
import CreateSubscriptionDialog from '../src/features/subscriptions/CreateSubscriptionDialog.jsx';
import Ledger from '../src/features/ledger/Ledger.jsx';
import AddChargeDialog from '../src/features/ledger/AddChargeDialog.jsx';
import AddPaymentDialog from '../src/features/ledger/AddPaymentDialog.jsx';

async function render(element) {
    let renderer;
    await act(async () => {
        renderer = create(element);
    });
    return renderer;
}

async function clickButton(root, text) {
    const button = root.findAllByType('button').find((item) => item.children.includes(text));
    assert.ok(button, `Button ${text} exists`);
    await act(async () => button.props.onClick());
}

async function closeDialog(dialog) {
    // Escape, backdrop and the close button reach the same native close handler.
    await act(async () => dialog.findByType('dialog').props.onClose({ stopPropagation() {} }));
}

function uncontrolledFields(dialog, names) {
    return names.map((name) => {
        const field = dialog.findByProps({ name });
        assert.equal(field.props.value, undefined, `${name} retains its browser-managed value`);
        return field;
    });
}

test('subscription close/reopen retains members, equipment and native form fields; success resets members', async (t) => {
    t.mock.property(
        globalThis,
        'FormData',
        class {
            get(name) {
                return name === 'capacity' ? '8' : null;
            }
        },
    );
    let succeed = false,
        resets = 0;
    const renderer = await render(
        h(Subscriptions, {
            people: [{ id: 7, fullName: 'Каякер', active: true }],
            subscriptions: [],
            create: async (event) => {
                if (succeed) event.currentTarget.reset();
                return succeed;
            },
        }),
    );
    const dialog = () => renderer.root.findByType(CreateSubscriptionDialog);
    // The host form remains mounted while hidden, preserving typed uncontrolled inputs.
    const form = dialog().findByType('form');
    const names = ['title', 'description', 'dayOfWeek', 'startTime', 'startDate', 'sessionCount', 'capacity'];
    const fields = uncontrolledFields(dialog(), names);
    await clickButton(renderer.root, 'Создать абонемент');
    await act(async () =>
        dialog()
            .findAllByProps({ type: 'checkbox' })[0]
            .props.onChange({ target: { checked: true } }),
    );
    await act(async () =>
        dialog()
            .findAllByProps({ type: 'checkbox' })[3]
            .props.onChange({ target: { checked: true } }),
    );
    await closeDialog(dialog());
    assert.equal(dialog().findByType(Modal).props.open, false);
    await clickButton(renderer.root, 'Создать абонемент');
    assert.equal(dialog().findByType('form'), form);
    assert.deepEqual(uncontrolledFields(dialog(), names), fields);
    assert.equal(dialog().findAllByProps({ type: 'checkbox' })[0].props.checked, true);
    assert.equal(dialog().findAllByProps({ type: 'checkbox' })[3].props.checked, true);
    assert.equal(resets, 0);
    const submit = () =>
        form.props.onSubmit({
            preventDefault() {},
            currentTarget: {
                reset() {
                    resets++;
                },
            },
        });
    await act(async () => submit());
    assert.equal(dialog().findByType(Modal).props.open, true);
    assert.equal(dialog().findAllByProps({ type: 'checkbox' })[3].props.checked, true);
    succeed = true;
    await act(async () => submit());
    assert.equal(resets, 1);
    assert.equal(dialog().findByType(Modal).props.open, false);
    await clickButton(renderer.root, 'Создать абонемент');
    assert.equal(dialog().findAllByProps({ type: 'checkbox' }).length, 1);
    assert.equal(dialog().findAllByProps({ type: 'checkbox' })[0].props.checked, false);
});

const ledgerProps = {
    personBalances: [{ personId: 7, fullName: 'Каякер', active: true }],
    subscriptions: [],
    personId: 7,
    balance: {
        charged: 100,
        paid: 0,
        balance: 100,
        charges: [{ id: 9, description: 'Бассейн', amount: 100, allocated: 40 }],
        payments: [],
    },
};

test('manual charge close/reopen preserves the native form and every uncontrolled draft field', async () => {
    const renderer = await render(h(Ledger, ledgerProps));
    const dialog = () => renderer.root.findByType(AddChargeDialog);
    const form = dialog().findByType('form');
    const names = ['amount', 'chargedAt', 'description', 'subscriptionId'];
    const fields = uncontrolledFields(dialog(), names);
    await clickButton(renderer.root, 'Добавить начисление');
    await closeDialog(dialog());
    assert.equal(dialog().findByType(Modal).props.open, false);
    await clickButton(renderer.root, 'Добавить начисление');
    assert.equal(dialog().findByType(Modal).props.open, true);
    assert.equal(dialog().findByType('form'), form);
    assert.deepEqual(uncontrolledFields(dialog(), names), fields);
});

test('payment close/reopen retains date, method and note host fields but resets allocation state and errors', async (t) => {
    t.mock.property(
        globalThis,
        'FormData',
        class {
            get(name) {
                return { amount: '100', chargeId: '9', allocationAmount: '61' }[name] ?? null;
            }
        },
    );
    const renderer = await render(h(Ledger, ledgerProps));
    const dialog = () => renderer.root.findByType(AddPaymentDialog);
    const form = dialog().findByType('form');
    const names = ['paidAt', 'method', 'note'];
    const retainedFields = uncontrolledFields(dialog(), names);
    await clickButton(renderer.root, 'Зарегистрировать оплату');
    await act(async () =>
        dialog()
            .findByProps({ name: 'amount' })
            .props.onChange({ target: { value: '100' } }),
    );
    await act(async () =>
        dialog()
            .findByProps({ name: 'chargeId' })
            .props.onChange({ target: { value: '9' } }),
    );
    assert.equal(dialog().findByProps({ name: 'allocationAmount' }).props.value, '60');
    await act(async () => form.props.onSubmit({ preventDefault() {}, currentTarget: {} }));
    assert.equal(dialog().findByProps({ name: 'allocationAmount' }).props['aria-invalid'], true);
    await closeDialog(dialog());
    await clickButton(renderer.root, 'Зарегистрировать оплату');
    assert.equal(dialog().findByType('form'), form);
    assert.deepEqual(uncontrolledFields(dialog(), names), retainedFields);
    for (const name of ['amount', 'chargeId', 'allocationAmount']) {
        assert.equal(dialog().findByProps({ name }).props.value, '');
    }
    assert.equal(dialog().findByProps({ name: 'allocationAmount' }).props['aria-invalid'], false);
});
