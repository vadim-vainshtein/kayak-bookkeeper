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


test('CSV exports per-person subscription totals without IDs and correctly escapes commas, quotes and newlines', () => {
    const csv = financialReportCsv(
        {
            summary: {
                charged: 100,
                allocatedPayments: 30,
                outstanding: 70,
                poolRentalCost: 20,
                clubProfit: 10,
                cashBalance: 25,
            },
            subscriptions: [
                {
                    subscriptionId: 41001,
                    title: 'Клуб, "Весло"\nНоябрь',
                    status: 'FINALIZED',
                    memberCount: 1,
                    charged: 100,
                    allocatedPayments: 30,
                    outstanding: 70,
                    poolRentalCost: 20,
                    clubProfit: 10,
                    cashBalance: 25,
                    payments: [
                        {
                            paymentId: 42002,
                            chargeId: 43003,
                            personName: 'Каякер',
                            paidAt: '2026-11-02',
                            method: 'CASH',
                            paymentNote: 'Первая, "часть"\nоплаты',
                            chargeDescription: 'Бассейн',
                            allocated: 30,
                        },
                    ],
                },
                {
                    subscriptionId: 44004,
                    title: null,
                    status: 'FINALIZED',
                    memberCount: 1,
                    charged: 100,
                    allocatedPayments: 30,
                    outstanding: 70,
                    poolRentalCost: 20,
                    clubProfit: 10,
                    cashBalance: 25,
                    payments: [
                        {
                            paymentId: 45005,
                            chargeId: 46006,
                            personName: 'Другой каякер',
                            paidAt: '2026-11-03',
                            method: 'TRANSFER',
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
    assert.ok(csv.includes('"Абонемент","Каякер","Оплачено, ₸"'));
    assert.ok(csv.includes('"Клуб, ""Весло""\nНоябрь","Каякер","30"'));
    assert.ok(csv.includes('"Без названия","Рассчитан","1","100","30","70","20","10","25"'));
    assert.ok(csv.includes('"Без названия","Другой каякер","30"'));
    assert.doesNotMatch(csv, /Дата оплаты|Способ оплаты|Примечание к оплате|Первая|2026-11-02|Бассейн|ID|41001|42002|43003|44004|45005|46006|Абонемент #|Начисление #/);
    assert.ok(csv.includes('"Ручное начисление","","5","0","5"'));
});
