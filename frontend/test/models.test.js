import test from 'node:test';
import assert from 'node:assert/strict';
import {
    createEditDraft,
    memberRequests,
    prepareSubscriptionEdit,
} from '../src/features/subscriptions/subscriptionDraft.js';
import {
    allocationLimit,
    paymentRemaining,
    suggestedAllocation,
    validAllocation,
} from '../src/features/ledger/allocation.js';
import { attendanceMarks } from '../src/features/attendance/attendanceDraft.js';
import { financialReportCsv } from '../src/features/reports/reportCsv.js';

const subscription = {
    status: 'FINALIZED',
    title: null,
    description: null,
    capacity: 2,
    sessions: [{ startsAt: '2026-11-10T19:00:00' }],
    members: [{ personId: 7, usesSkirt: false, usesPaddle: true, usesKayak: false }],
};

test('subscription drafts preserve equipment and produce exact numeric request fields', () => {
    const draft = createEditDraft(subscription);
    assert.deepEqual(draft.sessions, [{ date: '2026-11-10', time: '19:00' }]);
    assert.deepEqual(prepareSubscriptionEdit(subscription, draft).values, {
        title: null,
        description: null,
        capacity: 2,
        startsAt: ['2026-11-10T19:00'],
        members: subscription.members,
    });
    assert.deepEqual(
        memberRequests({ 7: { skirt: false, paddle: true, kayak: false } }),
        subscription.members,
    );
});

test('subscription validation rejects incomplete, duplicate and over-capacity drafts', () => {
    const draft = createEditDraft(subscription);
    assert.match(
        prepareSubscriptionEdit(subscription, { ...draft, sessions: [{ date: '2026-11-10', time: '' }] })
            .error,
        /дату и время/,
    );
    assert.match(
        prepareSubscriptionEdit(subscription, { ...draft, sessions: [...draft.sessions, ...draft.sessions] })
            .error,
        /повторяться/,
    );
    assert.match(prepareSubscriptionEdit(subscription, { ...draft, capacity: 0 }).error, /вместимость/);
    assert.match(prepareSubscriptionEdit(subscription, { ...draft, members: {} }).error, /участников/);
});

test('completed subscription edits contain only metadata regardless of schedule and members', () => {
    assert.deepEqual(prepareSubscriptionEdit({ status: 'COMPLETED' }, { title: 'Done', description: '' }), {
        values: { title: 'Done', description: null },
    });
});

test('allocation suggestions and validation respect both remaining limits', () => {
    const charge = { amount: 100, allocated: 65 };
    assert.equal(paymentRemaining({ amount: 100, allocations: [{ amount: 20 }, { amount: 30 }] }), 50);
    assert.equal(allocationLimit(80, charge), 35);
    assert.equal(suggestedAllocation('20', charge), '20');
    assert.equal(suggestedAllocation('', charge), '');
    assert.equal(suggestedAllocation('20', null), '');
    assert.equal(validAllocation('35', 80, charge), true);
    for (const amount of [-1, 0, 36, 1.5, 'NaN']) assert.equal(validAllocation(amount, 80, charge), false);
    assert.equal(validAllocation(21, 20, charge), false);
    assert.equal(validAllocation(1, 20, null), false);
});

test('attendance marks exclude unchanged entries and send absences first to free seats', () => {
    const members = [
        { personId: 1, status: 'PLANNED' },
        { personId: 2, status: 'PRESENT' },
        { personId: 3, status: 'ABSENT' },
    ];
    assert.deepEqual(attendanceMarks(members, { 1: true, 2: false, 3: false }), [
        { personId: 2, status: 'ABSENT' },
        { personId: 1, status: 'PRESENT' },
    ]);
    assert.deepEqual(attendanceMarks(members.slice(1), { 2: true, 3: false }), []);
});

test('CSV exports allocation details and correctly escapes commas, quotes and newlines', () => {
    const csv = financialReportCsv(
        {
            summary: {
                charged: 100,
                allocatedPayments: 30,
                outstanding: 70,
                poolRentalCost: 20,
                clubProfit: 10,
            },
            subscriptions: [
                {
                    subscriptionId: 1,
                    title: 'Клуб, "Весло"\nНоябрь',
                    status: 'FINALIZED',
                    memberCount: 1,
                    charged: 100,
                    allocatedPayments: 30,
                    outstanding: 70,
                    poolRentalCost: 20,
                    clubProfit: 10,
                    payments: [
                        {
                            paymentId: 2,
                            chargeId: 3,
                            personName: 'Каякер',
                            paidAt: '2026-11-02',
                            method: 'CASH',
                            allocated: 30,
                        },
                    ],
                },
            ],
            unassignedCharges: [
                {
                    personName: 'Каякер',
                    type: 'MANUAL',
                    description: null,
                    amount: 5,
                    allocated: 0,
                    outstanding: 5,
                },
            ],
        },
        'Все абонементы',
    );
    assert.ok(csv.startsWith('\uFEFF'));
    assert.ok(csv.includes('"Клуб, ""Весло""\nНоябрь"'));
    assert.ok(csv.includes('"Начисление #3","3","2","30"'));
    assert.ok(csv.includes('"Ручное начисление","","5","0","5"'));
});
