import { useEffect, useState } from 'react';
import { money } from '../../shared/format.js';
import { chargeTitle } from './chargeTitle.js';

export default function AllocationForm({
    payment,
    outstanding,
    subscriptions,
    paymentRemaining,
    allocate,
    close,
    submitting,
}) {
    const available = paymentRemaining(payment);
    const first = outstanding[0];
    const [chargeId, setChargeId] = useState(() => (first?.id ? String(first.id) : ''));
    const [amount, setAmount] = useState(() =>
        first ? String(Math.min(available, first.amount - first.allocated)) : '',
    );
    const [error, setError] = useState('');
    useEffect(() => {
        setChargeId(first?.id ? String(first.id) : '');
        setAmount(first ? String(Math.min(available, first.amount - first.allocated)) : '');
        setError('');
    }, [payment.id, first?.id, first?.amount, first?.allocated, available]);
    const selectCharge = (event) => {
        const id = event.target.value;
        const charge = outstanding.find((item) => item.id === Number(id));
        setChargeId(id);
        setAmount(charge ? String(Math.min(available, charge.amount - charge.allocated)) : '');
        setError('');
    };

    async function submit(event) {
        event.preventDefault();
        const charge = outstanding.find((item) => item.id === Number(chargeId));
        const value = Number(amount);
        const limit = charge ? Math.min(available, charge.amount - charge.allocated) : 0;
        if (!charge || !value || value > limit) {
            setError('Сумма не должна превышать остаток оплаты и выбранного начисления.');
            return;
        }
        const ok = await allocate(payment.id, charge.id, value);
        if (ok) close();
    }

    return (
        <form onSubmit={submit}>
            <p className="muted">Доступно для распределения: {money(available)}.</p>
            <label>
                Начисление <span className="required">*</span>
                <select name="chargeId" value={chargeId} onChange={selectCharge} required>
                    {outstanding.map((charge) => (
                        <option key={charge.id} value={charge.id}>
                            {chargeTitle(charge, subscriptions)} · осталось {money(charge.amount - charge.allocated)}
                        </option>
                    ))}
                </select>
            </label>
            <label>
                Сумма распределения, ₸ <span className="required">*</span>
                <input
                    name="amount"
                    type="number"
                    min="1"
                    value={amount}
                    onChange={(event) => {
                        setAmount(event.target.value);
                        setError('');
                    }}
                    aria-invalid={Boolean(error)}
                    aria-describedby={error ? 'existing-allocation-error' : undefined}
                    required
                />
                {error && (
                    <small id="existing-allocation-error" className="validation-message" role="alert">
                        {error}
                    </small>
                )}
            </label>
            <div className="dialog-actions">
                <button className="primary" disabled={submitting}>
                    Распределить оплату
                </button>
            </div>
        </form>
    );
}
