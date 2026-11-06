import { useEffect, useState } from 'react';
import { statusLabels } from '../../shared/labels.js';
import CreateSubscriptionDialog from './CreateSubscriptionDialog.jsx';
import EditSubscriptionDialog from './EditSubscriptionDialog.jsx';
import SubscriptionDetails from './SubscriptionDetails.jsx';
import AttendanceDialog from './AttendanceDialog.jsx';

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
    api,
    refreshData,
}) {
    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState(false);
    const [selectedId, setSelectedId] = useState(null);
    const [attendanceSession, setAttendanceSession] = useState(null);
    const selected = subscriptions.find((subscription) => subscription.id === selectedId);
    const pendingSessions = subscriptions.some((subscription) => subscription.status !== 'DRAFT'
        && subscription.sessions.some((session) => !session.attendanceAvailable));
    useEffect(() => {
        if (refreshData) refreshData({ quiet: true });
    }, [refreshData]);
    useEffect(() => {
        if (!refreshData) return;
        const refresh = () => refreshData({ quiet: true });
        // Eligibility comes from the club's server clock, never from parsing local
        // schedule timestamps in the browser's potentially different timezone.
        globalThis.window?.addEventListener('focus', refresh);
        const timer = pendingSessions ? setInterval(refresh, 30_000) : null;
        return () => {
            globalThis.window?.removeEventListener('focus', refresh);
            if (timer != null) clearInterval(timer);
        };
    }, [refreshData, pendingSessions]);
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
                                    onClick={() => {
                                        setSelectedId(subscription.id);
                                        if (refreshData) refreshData({ quiet: true });
                                    }}
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
                    openAttendance={setAttendanceSession}
                />
            )}
            {attendanceSession && <AttendanceDialog
                session={attendanceSession} people={people} api={api} readOnly={readOnly}
                onSaved={() => refreshData({ quiet: true })} onClose={() => setAttendanceSession(null)}
            />}
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
