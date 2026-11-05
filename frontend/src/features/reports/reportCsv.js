import { chargeLabels, statusLabels } from '../../shared/labels.js';

function personTotals(payments) {
    const people = new Map();
    for (const payment of payments) {
        const person = people.get(payment.personId) || { personName: payment.personName, allocated: 0 };
        person.allocated += payment.allocated;
        people.set(payment.personId, person);
    }
    return [...people.values()];
}

const csvCell = (value) => '"' + String(value ?? '').replaceAll('"', '""') + '"';

export function financialReportCsv(report, subscriptionLabel, statusLabel = 'Все') {
    const rows = [
        ['Финансовый отчёт по абонементам'],
        ['Абонементы', subscriptionLabel],
        ['Статус абонементов', statusLabel],
        [],
        ['Сводка', 'Сумма, ₸'],
        ['Начислено', report.summary.charged],
        ['Оплачено', report.summary.allocatedPayments],
        ['Недостача', report.summary.outstanding],
        ['Аренда бассейна', report.summary.poolRentalCost],
        ['Прибыль клуба', report.summary.clubProfit],
        ['Касса сейчас', report.summary.cashBalance],
        [],
        ['Абонементы'],
        [
            'Название',
            'Статус',
            'Участников',
            'Начислено, ₸',
            'Оплачено, ₸',
            'Недостача, ₸',
            'Аренда бассейна, ₸',
            'Прибыль, ₸',
            'Касса сейчас, ₸',
        ],
        ...report.subscriptions.map((item) => [
            item.title || 'Без названия',
            statusLabels[item.status],
            item.memberCount,
            item.charged,
            item.allocatedPayments,
            item.outstanding,
            item.poolRentalCost,
            item.clubProfit,
            item.cashBalance,
        ]),
        [],
        ['Разбивка оплат по абонементам'],
        [
            'Абонемент',
            'Каякер',
            'Оплачено, ₸',
        ],
        ...report.subscriptions.flatMap((subscription) =>
            personTotals(subscription.payments).map((payment) => [
                subscription.title || 'Без названия',
                payment.personName,
                payment.allocated,
            ]),
        ),
        [],
        ['Начисления без абонемента'],
        ['Каякер', 'Тип', 'Описание', 'Начислено, ₸', 'Оплачено, ₸', 'Недостача, ₸'],
        ...report.unassignedCharges.map((item) => [
            item.personName,
            chargeLabels[item.type],
            item.description || '',
            item.amount,
            item.allocated,
            item.outstanding,
        ]),
    ];

    return '\uFEFF' + rows.map((row) => row.map(csvCell).join(',')).join('\n');
}

export function downloadFinancialReport(report, subscriptionLabel, statusLabel = 'Все') {
    const blob = new Blob([financialReportCsv(report, subscriptionLabel, statusLabel)], {
        type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'subscription-financial-report.csv';
    link.click();
    URL.revokeObjectURL(url);
}
