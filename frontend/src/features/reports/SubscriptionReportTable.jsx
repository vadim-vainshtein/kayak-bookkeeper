import { Fragment, useId, useState } from 'react';
import { money } from '../../shared/format.js';
import { statusLabels } from '../../shared/labels.js';
import SubscriptionPaymentTable from './SubscriptionPaymentTable.jsx';

export default function SubscriptionReportTable({ report }) {
    const [expandedSubscriptionIds, setExpandedSubscriptionIds] = useState([]);
    const tableId = useId();
    const allExpanded = report.subscriptions.every((subscription) => expandedSubscriptionIds.includes(subscription.subscriptionId));
    const anyExpanded = report.subscriptions.some((subscription) => expandedSubscriptionIds.includes(subscription.subscriptionId));
    const toggleDetails = (id) =>
        setExpandedSubscriptionIds((ids) =>
            ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id],
        );
    return (
        <section className="card">
            <div className="report-subscription-heading">
                <h2>Абонементы</h2>
                <div className="button-group report-subscription-controls" role="group" aria-label="Раскрытие абонементов">
                    <button type="button" disabled={allExpanded}
                        onClick={() => setExpandedSubscriptionIds(report.subscriptions.map((subscription) => subscription.subscriptionId))}>
                        Развернуть все
                    </button>
                    <button type="button" disabled={!anyExpanded} onClick={() => setExpandedSubscriptionIds([])}>
                        Свернуть все
                    </button>
                </div>
            </div>
            <div className="balance-table-wrap">
                <table className="balance-table report-table">
                    <thead>
                        <tr>
                            <th scope="col">Абонемент</th>
                            <th scope="col">Участников</th>
                            <th scope="col">Начислено</th>
                            <th scope="col">Оплачено</th>
                            <th scope="col">Недостача</th>
                            <th scope="col">Бассейн</th>
                            <th scope="col">Прибыль</th>
                            <th scope="col">Касса сейчас</th>
                        </tr>
                    </thead>
                    <tbody>
                        {report.subscriptions.length ? (
                            report.subscriptions.map((subscription) => {
                                const expanded = expandedSubscriptionIds.includes(
                                    subscription.subscriptionId,
                                );
                                const detailsId = `${tableId}-subscription-payments-${subscription.subscriptionId}`;
                                const title = subscription.title || `Абонемент #${subscription.subscriptionId}`;
                                return (
                                    <Fragment key={subscription.subscriptionId}>
                                        <tr className="report-subscription-row">
                                            <td>
                                                <div className="balance-person-heading">
                                                    <button type="button" className="balance-expand"
                                                        aria-label={`${expanded ? 'Свернуть' : 'Развернуть'} оплаты: ${title}`}
                                                        aria-expanded={expanded} aria-controls={detailsId}
                                                        onClick={() => toggleDetails(subscription.subscriptionId)}>
                                                        <span aria-hidden="true">{expanded ? '▾' : '▸'}</span>
                                                    </button>
                                                    <div>
                                                        <strong>{title}</strong>
                                                        <small>{statusLabels[subscription.status]}</small>
                                                    </div>
                                                </div>
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
                                            <td className={subscription.cashBalance < 0 ? 'debt' : 'credit'}>
                                                {money(subscription.cashBalance)}
                                            </td>
                                        </tr>
                                        <tr id={detailsId} hidden={!expanded} className="report-payment-details">
                                            <td colSpan="8">
                                                {expanded && (
                                                    <SubscriptionPaymentTable
                                                        subscriptionId={subscription.subscriptionId}
                                                        payments={subscription.payments}
                                                    />
                                                )}
                                            </td>
                                        </tr>
                                    </Fragment>
                                );
                            })
                        ) : (
                            <tr>
                                <td colSpan="8" className="muted">
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
