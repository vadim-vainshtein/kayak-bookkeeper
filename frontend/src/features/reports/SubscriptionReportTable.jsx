import { Fragment, useState } from 'react';
import { formatDate, money } from '../../shared/format.js';
import { methodLabels, statusLabels } from '../../shared/labels.js';

export default function SubscriptionReportTable({ report }) {
    const [expandedSubscriptionIds, setExpandedSubscriptionIds] = useState([]);
    const toggleDetails = (id) =>
        setExpandedSubscriptionIds((ids) =>
            ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id],
        );
    return (
        <section className="card">
            <h2>Абонементы</h2>
            <div className="balance-table-wrap">
                <table className="balance-table report-table">
                    <thead>
                        <tr>
                            <th>Абонемент</th>
                            <th>Участников</th>
                            <th>Начислено</th>
                            <th>Оплачено</th>
                            <th>Недостача</th>
                            <th>Бассейн</th>
                            <th>Прибыль</th>
                        </tr>
                    </thead>
                    <tbody>
                        {report.subscriptions.length ? (
                            report.subscriptions.map((subscription) => {
                                const expanded = expandedSubscriptionIds.includes(
                                    subscription.subscriptionId,
                                );
                                const detailsId = `subscription-payments-${subscription.subscriptionId}`;
                                return (
                                    <Fragment key={subscription.subscriptionId}>
                                        <tr
                                            className="report-subscription-row"
                                            tabIndex="0"
                                            role="button"
                                            aria-expanded={expanded}
                                            aria-controls={detailsId}
                                            onClick={() => toggleDetails(subscription.subscriptionId)}
                                            onKeyDown={(event) => {
                                                if (event.key === 'Enter' || event.key === ' ') {
                                                    event.preventDefault();
                                                    toggleDetails(subscription.subscriptionId);
                                                }
                                            }}
                                        >
                                            <td>
                                                <strong>
                                                    {subscription.title ||
                                                        `Абонемент #${subscription.subscriptionId}`}
                                                </strong>
                                                <small>
                                                    {statusLabels[subscription.status]} ·{' '}
                                                    {expanded ? 'Скрыть оплаты' : 'Показать оплаты'}
                                                </small>
                                            </td>
                                            <td>{subscription.memberCount}</td>
                                            <td>{money(subscription.charged)}</td>
                                            <td>{money(subscription.allocatedPayments)}</td>
                                            <td
                                                className={
                                                    subscription.outstanding > 0
                                                        ? 'debt'
                                                        : subscription.outstanding < 0
                                                          ? 'credit'
                                                          : ''
                                                }
                                            >
                                                {money(subscription.outstanding)}
                                            </td>
                                            <td>{money(subscription.poolRentalCost)}</td>
                                            <td className={subscription.clubProfit < 0 ? 'debt' : 'credit'}>
                                                {money(subscription.clubProfit)}
                                            </td>
                                        </tr>
                                        {expanded && (
                                            <tr id={detailsId} className="report-payment-details">
                                                <td colSpan="7">
                                                    <h3>Оплаты, распределённые на начисления абонемента</h3>
                                                    {subscription.payments.length ? (
                                                        <div className="balance-table-wrap">
                                                            <table className="balance-table report-table report-payment-table">
                                                                <thead>
                                                                    <tr>
                                                                        <th>Каякер</th>
                                                                        <th>Дата оплаты</th>
                                                                        <th>Способ</th>
                                                                        <th>Начисление</th>
                                                                        <th>Сумма</th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody>
                                                                    {subscription.payments.map((payment) => (
                                                                        <tr
                                                                            key={`${payment.paymentId}-${payment.chargeId}`}
                                                                        >
                                                                            <td>
                                                                                <strong>
                                                                                    {payment.personName}
                                                                                </strong>
                                                                                {payment.paymentNote && (
                                                                                    <small>
                                                                                        {payment.paymentNote}
                                                                                    </small>
                                                                                )}
                                                                            </td>
                                                                            <td>
                                                                                {formatDate(payment.paidAt)}
                                                                            </td>
                                                                            <td>
                                                                                {methodLabels[payment.method]}
                                                                            </td>
                                                                            <td>
                                                                                {payment.chargeDescription ||
                                                                                    `Начисление #${payment.chargeId}`}
                                                                            </td>
                                                                            <td>
                                                                                {money(payment.allocated)}
                                                                            </td>
                                                                        </tr>
                                                                    ))}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                    ) : (
                                                        <p className="muted">
                                                            На начисления этого абонемента оплат пока не
                                                            распределено.
                                                        </p>
                                                    )}
                                                </td>
                                            </tr>
                                        )}
                                    </Fragment>
                                );
                            })
                        ) : (
                            <tr>
                                <td colSpan="7" className="muted">
                                    Абонементы не найдены.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
