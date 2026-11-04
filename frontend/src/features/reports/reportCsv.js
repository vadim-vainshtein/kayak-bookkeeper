import { chargeLabels, methodLabels, statusLabels } from '../../shared/labels.js';

const csvCell = (value) => '"' + String(value ?? '').replaceAll('"', '""') + '"';

export function financialReportCsv(report, subscriptionLabel) {
    const rows = [
        ['Финансовый отчёт по абонементам'],
        ['Абонементы', subscriptionLabel],
        [],
        ['Сводка', 'Сумма, ₸'],
        ['Начислено', report.summary.charged],
        ['Оплачено', report.summary.allocatedPayments],
        ['Недостача', report.summary.outstanding],
        ['Аренда бассейна', report.summary.poolRentalCost],
        ['Прибыль клуба', report.summary.clubProfit],
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
        ],
        ...report.subscriptions.map((item) => [
            item.title || `Абонемент #${item.subscriptionId}`,
            statusLabels[item.status],
            item.memberCount,
            item.charged,
            item.allocatedPayments,
            item.outstanding,
            item.poolRentalCost,
            item.clubProfit,
        ]),
        [],
        ['Разбивка оплат по абонементам'],
        [
            'Абонемент',
            'ID абонемента',
            'Каякер',
            'Дата оплаты',
            'Способ оплаты',
            'Примечание к оплате',
            'Начисление',
            'ID начисления',
            'ID оплаты',
            'Оплачено, ₸',
        ],
        ...report.subscriptions.flatMap((subscription) =>
            subscription.payments.map((payment) => [
                subscription.title || `Абонемент #${subscription.subscriptionId}`,
                subscription.subscriptionId,
                payment.personName,
                payment.paidAt,
                methodLabels[payment.method],
                payment.paymentNote || '',
                payment.chargeDescription || `Начисление #${payment.chargeId}`,
                payment.chargeId,
                payment.paymentId,
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

export function downloadFinancialReport(report, subscriptionLabel) {
    const blob = new Blob([financialReportCsv(report, subscriptionLabel)], {
        type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'subscription-financial-report.csv';
    link.click();
    URL.revokeObjectURL(url);
}
