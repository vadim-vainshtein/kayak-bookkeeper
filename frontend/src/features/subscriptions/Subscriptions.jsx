import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { statusLabels } from '../../shared/labels.js';
import CreateSubscriptionDialog from './CreateSubscriptionDialog.jsx';
import EditSubscriptionDialog from './EditSubscriptionDialog.jsx';
import SubscriptionDetails from './SubscriptionDetails.jsx';
import AttendanceDialog from './AttendanceDialog.jsx';
import Icon from '../../components/Icon.jsx';

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
    const listButtons = useRef(new Map());
    const previousSelection = useRef(null);
    const attendanceTrigger = useRef(null);
    const wasAttendanceOpen = useRef(false);
    useLayoutEffect(() => {
        if (selectedId == null && previousSelection.current != null) listButtons.current.get(previousSelection.current)?.focus();
        previousSelection.current = selectedId;
    }, [selectedId]);
    useLayoutEffect(() => {
        if (!attendanceSession && wasAttendanceOpen.current) attendanceTrigger.current?.focus();
        wasAttendanceOpen.current = Boolean(attendanceSession);
    }, [attendanceSession]);
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
        <>
        <div className={`subscriptions-grid ${selected ? 'has-selection' : ''}`} hidden={Boolean(attendanceSession)}>
            <section className="subscription-list-panel">
                <div className="line">
                    <h2 className="section-label">Все абонементы</h2>
                    {!readOnly && <button className="primary" onClick={() => setCreating(true)}>
                        <Icon name="plus" />
                        Создать абонемент
                    </button>}
                </div>
                {subscriptions.length ? (
                    <ul className="list subscription-list">
                        {subscriptions.map((subscription) => (
                            <li key={subscription.id}>
                                <button
                                    ref={(node) => { if (node) listButtons.current.set(subscription.id, node); else listButtons.current.delete(subscription.id); }}
                                    className={`subscription ${selectedId === subscription.id ? 'selected' : ''}`}
                                    aria-pressed={selectedId === subscription.id}
                                    onClick={() => {
                                        setSelectedId(subscription.id);
                                        if (refreshData) refreshData({ quiet: true });
                                    }}
                                >
                                    <div className="line">
                                        <strong>{subscription.title || 'Без названия'}</strong>
                                        <span className={`status status-${subscription.status.toLowerCase()}`}>{statusLabels[subscription.status]}</span>
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
                <div className="subscription-detail-panel">
                <button type="button" className="back-button mobile-back" onClick={() => setSelectedId(null)}><Icon name="back" />Все абонементы</button>
                <SubscriptionDetails
                    key={selected.id}
                    readOnly={readOnly}
                    selected={selected}
                    openEdit={() => setEditing(true)}
                    finalize={finalize}
                    complete={complete}
                    submitting={submitting}
                    openAttendance={(session, trigger) => { attendanceTrigger.current = trigger; setAttendanceSession(session); }}
                />
                </div>
            )}
            {!selected && <section className="card subscription-placeholder"><Icon name="subscriptions" /><h2>Выберите абонемент</h2><p className="muted">Расписание, участники и посещения появятся здесь.</p></section>}
        </div>
            {attendanceSession && <AttendanceDialog
                page
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
        </>
    );
}
