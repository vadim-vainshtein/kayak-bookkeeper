import test from 'node:test';
import assert from 'node:assert/strict';
import { financialReportCsv } from '../src/features/reports/reportCsv.js';

test('CSV sums all allocations per person and subscription while keeping same-named people separate', () => {
    const csv = financialReportCsv({
        summary: { cashBalance: 170 },
        subscriptions: [
            {
                title: 'Первый', status: 'FINALIZED', cashBalance: 120,
                payments: [
                    { personId: 1, personName: 'Алия', allocated: 50, paidAt: '2026-11-01', method: 'CASH', paymentNote: 'Детали', chargeDescription: 'Бассейн' },
                    { personId: 2, personName: 'Алия', allocated: 20 },
                    { personId: 1, personName: 'Алия', allocated: 30 },
                    { personId: 1, personName: 'Алия', allocated: 20 },
                ],
            },
            { title: 'Второй', status: 'FINALIZED', cashBalance: 50, payments: [{ personId: 1, personName: 'Алия', allocated: 50 }] },
        ],
        unassignedCharges: [],
    }, 'Все');
    assert.ok(csv.includes('"Касса сейчас","170"'));
    assert.ok(csv.includes('"Касса сейчас, ₸"'));
    const breakdown = csv.split('"Разбивка оплат по абонементам"\n')[1].split('\n\n')[0];
    assert.equal(breakdown, [
        '"Абонемент","Каякер","Оплачено, ₸"',
        '"Первый","Алия","100"',
        '"Первый","Алия","20"',
        '"Второй","Алия","50"',
    ].join('\n'));
    assert.doesNotMatch(csv, /Детали|2026-11-01|CASH|Бассейн|Дата оплаты|Способ оплаты|Начисление"/);
});
