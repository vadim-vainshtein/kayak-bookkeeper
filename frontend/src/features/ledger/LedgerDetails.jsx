import { useState } from 'react';
import { balanceClass, balanceLabel, signedBalance, formatDate, money } from '../../shared/format.js';
import { chargeLabels, methodLabels } from '../../shared/labels.js';
import { paymentRemaining } from './allocation.js';
import { chargeTitle } from './chargeTitle.js';

export default function LedgerDetails({
    readOnly = false,
    balance,
    subscriptions,
    charges,
    payments,
    setChargeDialog,
    setPaymentDialog,
    openEdit,
    openPaymentEdit,
    setAllocationPayment,
}) {
    const [showSettled, setShowSettled] = useState(false);
    const visibleCharges = showSettled ? charges : charges.filter((charge) => charge.amount > charge.allocated);
    const visiblePayments = showSettled ? payments : payments.filter((payment) => paymentRemaining(payment) > 0);
    const ChargeItem = readOnly ? 'div' : 'button';
    const PaymentItem = readOnly ? 'div' : 'button';
    return (
        <>
            <section className="balance-summary">
                <div>
                    <span className="muted">Начислено</span>
                    <strong>{money(balance.charged)}</strong>
                </div>
                <div>
                    <span className="muted">Оплачено</span>
                    <strong>{money(balance.paid)}</strong>
                </div>
                <div>
                    <span className="muted">Баланс</span>
                    <strong
                        className={balanceClass(balance.balance)}
                        aria-label={balanceLabel(balance.balance)}
                    >
                        {signedBalance(balance.balance)}
                    </strong>
                </div>
            </section>
            <section>
                <div className="line">
                    <h2>Операции</h2>
                    {!readOnly && <div className="button-group">
                        <button onClick={() => setChargeDialog(true)}>
                            Добавить начисление
                        </button>
                        <button className="primary" onClick={() => setPaymentDialog(true)}>
                            Зарегистрировать оплату
                        </button>
                    </div>}
                </div>
                <label className="checkbox">
                    <input
                        type="checkbox"
                        checked={showSettled}
                        onChange={(event) => setShowSettled(event.target.checked)}
                    />
                    Показывать все начисления и оплаты
                </label>
            </section>
            <div className="grid">
                <section className="card">
                    <h2>Начисления</h2>
                    {visibleCharges.length ? (
                        <ul className="list">
                            {visibleCharges.map((charge) => {
                                const remaining = Math.max(0, charge.amount - charge.allocated);
                                return (
                                    <li key={charge.id}>
                                        <ChargeItem className="ledger-item" onClick={readOnly ? undefined : () => openEdit(charge)}>
                                            <div className="line">
                                                <strong>{chargeTitle(charge, subscriptions)}</strong>
                                                <span>{money(charge.amount)}</span>
                                            </div>
                                            <small className="muted">
                                                {formatDate(charge.chargedAt)} · {chargeLabels[charge.type]} ·
                                                распределено {money(charge.allocated)}
                                                {!charge.subscriptionId && ' · Без привязки к абонементу'}
                                            </small>
                                            <small
                                                className={`charge-payment-status ${remaining === 0 ? 'paid' : 'unpaid'}`}
                                            >
                                                {remaining === 0
                                                    ? 'Оплачено полностью'
                                                    : 'Не оплачено полностью'}{' '}
                                                · осталось распределить: {money(remaining)}
                                            </small>
                                        </ChargeItem>
                                    </li>
                                );
                            })}
                        </ul>
                    ) : (
                        <p className="muted">{charges.length ? 'Неоплаченных начислений нет.' : 'Начислений пока нет.'}</p>
                    )}
                </section>
                <section className="card">
                    <h2>Оплаты</h2>
                    {visiblePayments.length ? (
                        <ul className="list">
                            {visiblePayments.map((payment) => (
                                <li key={payment.id}>
                                    <PaymentItem
                                        className="ledger-item payment-item"
                                        type={readOnly ? undefined : 'button'}
                                        onClick={readOnly ? undefined : () => openPaymentEdit(payment.id)}
                                    >
                                        <div className="line">
                                            <strong>{money(payment.amount)}</strong>
                                            <span className="muted">{methodLabels[payment.method]}</span>
                                        </div>
                                        <small className="muted">
                                            {formatDate(payment.paidAt)}
                                            {payment.note ? ` · ${payment.note}` : ''}
                                        </small>
                                        {payment.allocations.map((allocation) => {
                                            const charge = charges.find((item) => item.id === allocation.chargeId);
                                            return (
                                                <small className="allocation" key={allocation.id}>
                                                    Распределено: {charge
                                                        ? chargeTitle(charge, subscriptions)
                                                        : allocation.chargeDescription} ·{' '}
                                                    {money(allocation.amount)}
                                                </small>
                                            );
                                        })}
                                    </PaymentItem>
                                    {paymentRemaining(payment) > 0 && (
                                        <div className="payment-remaining">
                                            <small className="muted">
                                                Не распределено: {money(paymentRemaining(payment))}
                                            </small>
                                            {!readOnly && <button onClick={() => setAllocationPayment(payment)}>
                                                Распределить
                                            </button>}
                                        </div>
                                    )}
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className="muted">{payments.length ? 'Нераспределённых оплат нет.' : 'Оплат пока нет.'}</p>
                    )}
                </section>
            </div>
        </>
    );
}
