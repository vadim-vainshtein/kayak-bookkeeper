import Modal from '../../components/Modal.jsx';
import { today } from '../../shared/format.js';

export default function AddChargeDialog({ open, subscriptions, personId, addCharge, submitting, onClose }) {
    async function chargeSubmit(event) {
        if (await addCharge(event, personId)) onClose();
    }
    return (
        <Modal open={open} onClose={onClose} title="Ручное начисление">
            <form onSubmit={chargeSubmit}>
                <label>
                    Сумма, ₸ <span className="required">*</span>
                    <input name="amount" type="number" min="1" required />
                </label>
                <label>
                    Дата <span className="required">*</span>
                    <input name="chargedAt" type="date" defaultValue={today()} required />
                </label>
                <label>
                    Описание <span className="required">*</span>
                    <input name="description" maxLength="1000" required />
                </label>
                <label>
                    Абонемент
                    <select name="subscriptionId" defaultValue="">
                        <option value="">Без привязки</option>
                        {subscriptions.map((subscription) => (
                            <option key={subscription.id} value={subscription.id}>
                                {subscription.title || `Абонемент #${subscription.id}`}
                            </option>
                        ))}
                    </select>
                </label>
                <div className="dialog-actions">
                    <button className="primary" disabled={submitting}>
                        Начислить
                    </button>
                </div>
            </form>
        </Modal>
    );
}
