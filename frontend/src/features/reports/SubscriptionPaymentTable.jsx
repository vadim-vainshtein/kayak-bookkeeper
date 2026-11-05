import { Fragment, useId, useState } from 'react';
import { formatDate, money } from '../../shared/format.js';
import { methodLabels } from '../../shared/labels.js';

export default function SubscriptionPaymentTable({ subscriptionId, payments }) {
    const [expandedPersonIds, setExpandedPersonIds] = useState([]);
    const tableId = useId();
    const people = new Map();
    for (const payment of payments) {
        if (!people.has(payment.personId)) {
            people.set(payment.personId, {
                personId: payment.personId,
                personName: payment.personName,
                allocated: 0,
                payments: [],
            });
        }
        const person = people.get(payment.personId);
        person.allocated += payment.allocated;
        person.payments.push(payment);
    }
    const groups = [...people.values()];
    const togglePerson = (id) =>
        setExpandedPersonIds((ids) =>
            ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id],
        );
    const allExpanded = groups.every((person) => expandedPersonIds.includes(person.personId));
    const anyExpanded = groups.some((person) => expandedPersonIds.includes(person.personId));

    if (!groups.length) {
        return <p className="muted">На начисления этого абонемента оплат пока не распределено.</p>;
    }

    return (
        <>
            <div className="button-group report-payment-controls">
                <button
                    type="button"
                    disabled={allExpanded}
                    onClick={() => setExpandedPersonIds(groups.map((person) => person.personId))}
                >
                    Развернуть все
                </button>
                <button
                    type="button"
                    disabled={!anyExpanded}
                    onClick={() => setExpandedPersonIds([])}
                >
                    Свернуть все
                </button>
            </div>
            <div className="balance-table-wrap">
                <table className="balance-table report-table report-payment-table">
                    <thead>
                        <tr>
                            <th scope="col">Каякер</th>
                            <th scope="col">Сумма</th>
                        </tr>
                    </thead>
                    <tbody>
                        {groups.map((person) => {
                            const expanded = expandedPersonIds.includes(person.personId);
                            const detailsId = `${tableId}-subscription-${subscriptionId}-person-${person.personId}-payments`;
                            return (
                                <Fragment key={person.personId}>
                                    <tr className="report-person-row">
                                        <td>
                                            <div className="balance-person-heading">
                                                <button type="button" className="balance-expand"
                                                    aria-label={`${expanded ? 'Свернуть' : 'Развернуть'} оплаты каякера: ${person.personName}`}
                                                    aria-expanded={expanded} aria-controls={detailsId}
                                                    onClick={() => togglePerson(person.personId)}>
                                                    <span aria-hidden="true">{expanded ? '▾' : '▸'}</span>
                                                </button>
                                                <strong>{person.personName}</strong>
                                            </div>
                                        </td>
                                        <td>{money(person.allocated)}</td>
                                    </tr>
                                    <tr id={detailsId} hidden={!expanded} className="report-person-details">
                                        <td colSpan="2">
                                            {expanded && (
                                                <table className="balance-table report-table report-payment-table">
                                                    <thead>
                                                        <tr>
                                                            <th scope="col">Дата оплаты</th>
                                                            <th scope="col">Способ</th>
                                                            <th scope="col">Начисление</th>
                                                            <th scope="col">Сумма</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {person.payments.map((payment) => (
                                                            <tr
                                                                key={`${payment.paymentId}-${payment.chargeId}`}
                                                            >
                                                                <td>
                                                                    {formatDate(payment.paidAt)}
                                                                    {payment.paymentNote && (
                                                                        <small>{payment.paymentNote}</small>
                                                                    )}
                                                                </td>
                                                                <td>{methodLabels[payment.method]}</td>
                                                                <td>
                                                                    {payment.chargeDescription ||
                                                                        `Начисление #${payment.chargeId}`}
                                                                </td>
                                                                <td>{money(payment.allocated)}</td>
                                                            </tr>
                                                        ))}
                                                    </tbody>
                                                </table>
                                            )}
                                        </td>
                                    </tr>
                                </Fragment>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </>
    );
}
