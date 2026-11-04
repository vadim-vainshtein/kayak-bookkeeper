import { useLayoutEffect, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { money, today } from '../../shared/format.js';
import { methodLabels } from '../../shared/labels.js';
import { suggestedAllocation, validAllocation } from './allocation.js';
import { chargeTitle } from './chargeTitle.js';

export default function AddPaymentDialog({ open, outstanding, subscriptions, personId, addPayment, submitting, onClose }) {
    const [allocationError, setAllocationError] = useState('');
    const [newPaymentAmount, setNewPaymentAmount] = useState('');
    const [newPaymentChargeId, setNewPaymentChargeId] = useState('');
    const [newPaymentAllocationAmount, setNewPaymentAllocationAmount] = useState('');
    useLayoutEffect(() => {
        setAllocationError('');
        if (open) {
            setNewPaymentAmount('');
            setNewPaymentChargeId('');
            setNewPaymentAllocationAmount('');
        }
    }, [open]);
    const suggestNewPaymentAllocation = (amount, chargeId) => {
        setNewPaymentAllocationAmount(
            suggestedAllocation(
                amount,
                outstanding.find((item) => item.id === Number(chargeId)),
            ),
        );
    };
    async function paymentSubmit(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const charge = outstanding.find((item) => item.id === Number(form.get('chargeId')));
        if (
            form.get('chargeId') &&
            !validAllocation(form.get('allocationAmount'), form.get('amount'), charge)
        ) {
            setAllocationError('Сумма распределения не должна превышать сумму оплаты и остаток начисления.');
            return;
        }
        if (await addPayment(event, personId)) onClose();
    }
    return (
        <Modal open={open} onClose={onClose} title="Зарегистрировать оплату">
            <form onSubmit={paymentSubmit}>
                <p className="muted">Оплата и распределение сохраняются одной операцией.</p>
                <label>
                    Сумма, ₸ <span className="required">*</span>
                    <input
                        name="amount"
                        type="number"
                        min="1"
                        value={newPaymentAmount}
                        onChange={(event) => {
                            const amount = event.target.value;
                            setNewPaymentAmount(amount);
                            suggestNewPaymentAllocation(amount, newPaymentChargeId);
                            setAllocationError('');
                        }}
                        required
                    />
                </label>
                <label>
                    Дата оплаты <span className="required">*</span>
                    <input name="paidAt" type="date" defaultValue={today()} required />
                </label>
                <label>
                    Способ оплаты
                    <select name="method" defaultValue="CASH">
                        {Object.entries(methodLabels).map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </label>
                <label>
                    Заметка
                    <input name="note" maxLength="1000" />
                </label>
                <label>
                    Распределить на начисление
                    <select
                        name="chargeId"
                        value={newPaymentChargeId}
                        onChange={(event) => {
                            const chargeId = event.target.value;
                            setNewPaymentChargeId(chargeId);
                            suggestNewPaymentAllocation(newPaymentAmount, chargeId);
                            setAllocationError('');
                        }}
                    >
                        <option value="">Не распределять</option>
                        {outstanding.map((charge) => (
                            <option key={charge.id} value={charge.id}>
                                {chargeTitle(charge, subscriptions)} · осталось {money(charge.amount - charge.allocated)}
                            </option>
                        ))}
                    </select>
                </label>
                <label>
                    Сумма распределения, ₸
                    <input
                        name="allocationAmount"
                        type="number"
                        min="1"
                        value={newPaymentAllocationAmount}
                        aria-invalid={Boolean(allocationError)}
                        aria-describedby={allocationError ? 'payment-allocation-error' : undefined}
                        onChange={(event) => {
                            setNewPaymentAllocationAmount(event.target.value);
                            setAllocationError('');
                        }}
                    />
                    {allocationError && (
                        <small id="payment-allocation-error" className="validation-message" role="alert">
                            {allocationError}
                        </small>
                    )}
                </label>
                <div className="dialog-actions">
                    <button className="primary" disabled={submitting}>
                        Зарегистрировать
                    </button>
                </div>
            </form>
        </Modal>
    );
}
