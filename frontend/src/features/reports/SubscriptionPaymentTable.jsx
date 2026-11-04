import { Fragment, useState } from 'react';
import { formatDate, money } from '../../shared/format.js';
import { methodLabels } from '../../shared/labels.js';

export default function SubscriptionPaymentTable({ subscriptionId, payments }) {
    const [expandedPersonIds, setExpandedPersonIds] = useState([]);
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
                            <th>Каякер</th>
                            <th>Сумма</th>
                        </tr>
                    </thead>
                    <tbody>
                        {groups.map((person) => {
                            const expanded = expandedPersonIds.includes(person.personId);
                            const detailsId = `subscription-${subscriptionId}-person-${person.personId}-payments`;
                            return (
                                <Fragment key={person.personId}>
                                    <tr
                                        className="report-person-row"
                                        tabIndex="0"
                                        role="button"
                                        aria-expanded={expanded}
                                        aria-controls={detailsId}
                                        onClick={() => togglePerson(person.personId)}
                                        onKeyDown={(event) => {
                                            if (event.key === 'Enter' || event.key === ' ') {
                                                event.preventDefault();
                                                togglePerson(person.personId);
                                            }
                                        }}
                                    >
                                        <td>
                                            <strong>{person.personName}</strong>
                                            <small>{expanded ? 'Скрыть оплаты' : 'Показать оплаты'}</small>
                                        </td>
                                        <td>{money(person.allocated)}</td>
                                    </tr>
                                    {expanded && (
                                        <tr id={detailsId} className="report-person-details">
                                            <td colSpan="2">
                                                <table className="balance-table report-table report-payment-table">
                                                    <thead>
                                                        <tr>
                                                            <th>Дата оплаты</th>
                                                            <th>Способ</th>
                                                            <th>Начисление</th>
                                                            <th>Сумма</th>
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
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </>
    );
}
