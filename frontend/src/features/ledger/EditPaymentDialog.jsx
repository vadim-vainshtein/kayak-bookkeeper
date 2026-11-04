import { useId, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { money } from '../../shared/format.js';
import { methodLabels } from '../../shared/labels.js';
import { chargeTitle } from './chargeTitle.js';

export default function EditPaymentDialog({
    payment, charges, subscriptions, savePayment, cancelAllocation, submitting, onClose,
}) {
    const [amount, setAmount] = useState(() => String(payment.amount));
    const [paidAt, setPaidAt] = useState(() => payment.paidAt);
    const [note, setNote] = useState(() => payment.note || '');
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const errorId = useId();
    const allocated = payment.allocations.reduce((sum, allocation) => sum + allocation.amount, 0);
    const minimum = Math.max(1, allocated);

    async function save(event) {
        event.preventDefault();
        setError('');
        setMessage('');
        const value = Number(amount);
        if (!Number.isSafeInteger(value) || value < minimum) {
            setError(`Сумма оплаты должна быть целым числом не меньше ${money(minimum)}.`);
            return;
        }
        if (!paidAt) {
            setError('Укажите дату оплаты.');
            return;
        }
        if (note.length > 1000) {
            setError('Комментарий не должен превышать 1000 символов.');
            return;
        }
        let failure = 'Не удалось изменить оплату. Попробуйте ещё раз.';
        const ok = await savePayment(payment.id, { amount: value, paidAt, note }, (text) => { failure = text; });
        if (ok) onClose();
        else setError(failure);
    }

    async function cancel(allocation) {
        setError('');
        setMessage('');
        let failure = 'Не удалось отменить распределение. Попробуйте ещё раз.';
        const ok = await cancelAllocation(payment.id, allocation.id, (text) => { failure = text; });
        if (ok) setMessage('Распределение отменено. Сумма снова доступна для распределения.');
        else setError(failure);
    }

    return (
        <Modal open onClose={onClose} title="Редактировать оплату">
            <p className="muted">
                Способ оплаты: {methodLabels[payment.method]}
            </p>
            {error && <p id={errorId} className="validation-message" role="alert">{error}</p>}
            {message && <p className="notice success" role="status">{message}</p>}
            <form onSubmit={save}>
                <label>
                    Сумма оплаты, ₸ <span className="required">*</span>
                    <input
                        name="amount" type="number" min={minimum} step="1" required
                        value={amount} disabled={submitting}
                        aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined}
                        onChange={(event) => { setAmount(event.target.value); setError(''); }}
                    />
                </label>
                <p className="muted">Уже распределено: {money(allocated)}. Сумма оплаты не может быть меньше.</p>
                <label>
                    Дата оплаты <span className="required">*</span>
                    <input
                        name="paidAt" type="date" required value={paidAt} disabled={submitting}
                        onChange={(event) => { setPaidAt(event.target.value); setError(''); }}
                    />
                </label>
                <label>
                    Комментарий
                    <input
                        name="note" maxLength="1000" value={note} disabled={submitting}
                        onChange={(event) => { setNote(event.target.value); setError(''); }}
                    />
                </label>
                <div className="dialog-actions">
                    <button className="primary" disabled={submitting}>Сохранить оплату</button>
                </div>
            </form>
            <h3>Распределения</h3>
            {payment.allocations.length ? (
                <ul className="list">
                    {payment.allocations.map((allocation) => {
                        const charge = charges.find((item) => item.id === allocation.chargeId);
                        const title = charge ? chargeTitle(charge, subscriptions) : allocation.chargeDescription;
                        return (
                            <li key={allocation.id}>
                                <div className="line"><strong>{title}</strong><span>{money(allocation.amount)}</span></div>
                                <div className="list-actions">
                                    <button
                                        type="button" disabled={submitting}
                                        aria-label={`Отменить распределение: ${title}, ${money(allocation.amount)}`}
                                        onClick={() => cancel(allocation)}
                                    >Отменить распределение</button>
                                </div>
                            </li>
                        );
                    })}
                </ul>
            ) : <p className="muted">У оплаты нет распределений.</p>}
        </Modal>
    );
}
