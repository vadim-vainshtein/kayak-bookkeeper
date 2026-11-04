import { money } from '../../shared/format.js';

export default function ChargeEditForm({ charge, draft, setDraft, subscriptions, onConfirm }) {
    const change = (field) => (event) =>
        setDraft({
            ...draft,
            [field]:
                field === 'amount' || field === 'subscriptionId'
                    ? event.target.value
                        ? Number(event.target.value)
                        : null
                    : event.target.value,
        });
    return (
        <form
            onSubmit={(event) => {
                event.preventDefault();
                onConfirm();
            }}
        >
            <label>
                Сумма, ₸
                <input
                    name="amount"
                    type="number"
                    min={Math.max(1, charge.allocated)}
                    value={draft.amount}
                    onChange={change('amount')}
                    required
                />
            </label>
            <label>
                Дата
                <input
                    name="chargedAt"
                    type="date"
                    value={draft.chargedAt}
                    onChange={change('chargedAt')}
                    required
                />
            </label>
            <label>
                Описание
                <input
                    name="description"
                    value={draft.description}
                    onChange={change('description')}
                    required
                />
            </label>
            <label>
                Абонемент
                <select
                    name="subscriptionId"
                    value={draft.subscriptionId || ''}
                    onChange={change('subscriptionId')}
                >
                    <option value="">Без привязки</option>
                    {subscriptions.map((subscription) => (
                        <option key={subscription.id} value={subscription.id}>
                            {subscription.title || `Абонемент #${subscription.id}`}
                        </option>
                    ))}
                </select>
            </label>
            <p className="muted">Уже распределено: {money(charge.allocated)}.</p>
            <div className="dialog-actions">
                <button className="primary">Сохранить начисление</button>
            </div>
        </form>
    );
}
