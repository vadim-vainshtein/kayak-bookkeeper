import test from 'node:test';
import assert from 'node:assert/strict';
import { createSubscriptionActions } from '../src/features/subscriptions/subscriptionActions.js';
import { createLedgerActions } from '../src/features/ledger/ledgerActions.js';
import { createAttendanceActions } from '../src/features/attendance/attendanceActions.js';

function setupForm(t, fields) {
    t.mock.property(
        globalThis,
        'FormData',
        class {
            get(name) {
                return fields[name] ?? null;
            }
        },
    );
    return { preventDefault() {}, currentTarget: { reset() {} } };
}

test('subscription creation sends numeric members and schedule fields then refreshes data', async (t) => {
    const requests = [];
    let loaded = 0;
    const actions = createSubscriptionActions({
        api: async (path, options) => requests.push([path, JSON.parse(options.body)]),
        submit: async (_, action) => {
            await action();
            return true;
        },
        load: async () => loaded++,
    });
    const event = setupForm(t, {
        startDate: '2026-11-10',
        dayOfWeek: 'TUESDAY',
        startTime: '19:00',
        sessionCount: '4',
        capacity: '8',
        title: '',
        description: '',
    });
    assert.equal(
        await actions.createSubscription(event, { 7: { skirt: true, paddle: false, kayak: true } }),
        true,
    );
    assert.deepEqual(requests, [
        [
            '/api/subscriptions/recurring-with-members',
            {
                startDate: '2026-11-10',
                dayOfWeek: 'TUESDAY',
                startTime: '19:00',
                sessionCount: 4,
                capacity: 8,
                title: null,
                description: null,
                members: [{ personId: 7, usesSkirt: true, usesPaddle: false, usesKayak: true }],
            },
        ],
    ]);
    assert.equal(loaded, 1);
});

test('payment registration preserves atomic allocation payload and refreshes balances', async (t) => {
    const requests = [],
        refreshes = [];
    const actions = createLedgerActions({
        api: async (path, options) => requests.push([path, JSON.parse(options.body)]),
        submit: async (_, action) => {
            await action();
            return true;
        },
        loadBalance: async (id) => refreshes.push(id),
        refreshBalances: async () => refreshes.push('all'),
    });
    await actions.addPayment(
        setupForm(t, {
            amount: '100',
            paidAt: '2026-11-10',
            method: 'CASH',
            chargeId: '9',
            allocationAmount: '60',
        }),
        7,
    );
    assert.deepEqual(requests, [
        [
            '/api/payments/with-allocation',
            {
                personId: 7,
                amount: 100,
                paidAt: '2026-11-10',
                method: 'CASH',
                note: null,
                chargeId: 9,
                allocationAmount: 60,
            },
        ],
    ]);
    assert.deepEqual(refreshes, [7, 'all']);
});

test('attendance saves marks sequentially and stops after a failure', async () => {
    const saved = [];
    const actions = createAttendanceActions({
        run: async (action) => {
            try {
                await action();
                return true;
            } catch {
                return false;
            }
        },
        api: async (_, options) => {
            const mark = JSON.parse(options.body);
            saved.push(mark.personId);
            if (mark.personId === 2) throw Error('rejected');
        },
    });
    assert.equal(
        await actions.markAttendance(5, [
            { personId: 1, status: 'ABSENT' },
            { personId: 2, status: 'PRESENT' },
            { personId: 3, status: 'PRESENT' },
        ]),
        false,
    );
    assert.deepEqual(saved, [1, 2]);
});
