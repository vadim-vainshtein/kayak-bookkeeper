import { useState } from 'react';
import { statusLabels } from '../../shared/labels.js';
import CreateSubscriptionDialog from './CreateSubscriptionDialog.jsx';
import EditSubscriptionDialog from './EditSubscriptionDialog.jsx';
import SubscriptionDetails from './SubscriptionDetails.jsx';

export default function Subscriptions({
    readOnly = false,
    people,
    subscriptions,
    create,
    update,
    preview,
    finalize,
    complete,
    submitting,
}) {
    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState(false);
    const [selectedId, setSelectedId] = useState(null);
    const selected = subscriptions.find((subscription) => subscription.id === selectedId);
    return (
        <div className="grid subscriptions-grid">
            <section className="card">
                <div className="line">
                    <h2>Абонементы</h2>
                    {!readOnly && <button className="primary" onClick={() => setCreating(true)}>
                        Создать абонемент
                    </button>}
                </div>
                {subscriptions.length ? (
                    <ul className="list">
                        {subscriptions.map((subscription) => (
                            <li key={subscription.id}>
                                <button
                                    className={`subscription ${selectedId === subscription.id ? 'selected' : ''}`}
                                    onClick={() => setSelectedId(subscription.id)}
                                >
                                    <div className="line">
                                        <strong>{subscription.title || 'Без названия'}</strong>
                                        <span className="status">{statusLabels[subscription.status]}</span>
                                    </div>
                                    <small className="muted">
                                        {subscription.members.length}/{subscription.capacity} участников ·{' '}
                                        {subscription.sessions.length} тренировок
                                    </small>
                                </button>
                            </li>
                        ))}
                    </ul>
                ) : (
                    <p className="muted">Абонементов пока нет.</p>
                )}
            </section>

            {selected && (
                <SubscriptionDetails
                    readOnly={readOnly}
                    selected={selected}
                    openEdit={() => setEditing(true)}
                    finalize={finalize}
                    complete={complete}
                    submitting={submitting}
                />
            )}
            {!readOnly && <CreateSubscriptionDialog
                open={creating}
                people={people}
                create={create}
                submitting={submitting}
                onClose={() => setCreating(false)}
            />}
            {!readOnly && editing && selected && (
                <EditSubscriptionDialog
                    key={selected.id}
                    selected={selected}
                    people={people}
                    update={update}
                    preview={preview}
                    submitting={submitting}
                    onClose={() => setEditing(false)}
                />
            )}
        </div>
    );
}
