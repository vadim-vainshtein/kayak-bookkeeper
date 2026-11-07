import { formatDate } from '../../shared/format.js';
import { statusLabels } from '../../shared/labels.js';
import { useId, useLayoutEffect, useRef, useState } from 'react';

export default function SubscriptionDetails({
    readOnly = false,
    selected,
    openEdit,
    finalize,
    complete,
    submitting,
    openAttendance,
}) {
    const [view, setView] = useState('schedule');
    const id = useId();
    const heading = useRef(null);
    useLayoutEffect(() => { heading.current?.focus(); }, []);
    return (
        <section className="card subscription-details">
            <div className="line">
                <div>
                    <h2 ref={heading} tabIndex={-1}>{selected.title || 'Абонемент без названия'}</h2>
                    <span className={`status status-${selected.status.toLowerCase()}`}>{statusLabels[selected.status]}</span>
                </div>
                {!readOnly && <button onClick={openEdit}>Редактировать</button>}
            </div>
            {selected.description && <p>{selected.description}</p>}
            <p className="muted subscription-meta">{selected.members.length} из {selected.capacity} участников · {selected.sessions.length} тренировок</p>
            <nav className="detail-tabs" aria-label="Содержание абонемента">
                <button type="button" aria-pressed={view === 'schedule'} aria-controls={`${id}-schedule`} onClick={() => setView('schedule')}>Расписание</button>
                <button type="button" aria-pressed={view === 'members'} aria-controls={`${id}-members`} onClick={() => setView('members')}>Участники · {selected.members.length}</button>
            </nav>
            <div id={`${id}-schedule`} hidden={view !== 'schedule'}>
            <ul className="plain-list session-list">
                {selected.sessions.map((session) => (
                    <li key={session.id} className="session-row">
                        <span className="session-date">{formatDate(session.startsAt)}</span>
                        <span className="attendance-session-actions">
                            {selected.status !== 'DRAFT' && <small className="muted">
                                Пришли: {session.attendedCount || 0}{!session.attendanceRecorded && ' · ещё не отмечали'}
                            </small>}
                            {session.attendanceAvailable && <button type="button" disabled={submitting}
                                onClick={(event) => openAttendance(session, event?.currentTarget)}>
                                {readOnly ? 'Посещения' : 'Отметить посещения'}
                            </button>}
                            {!session.attendanceAvailable && selected.status !== 'DRAFT' && <small className="muted">Посещения доступны с начала тренировки</small>}
                        </span>
                    </li>
                ))}
            </ul>
            </div>
            <div id={`${id}-members`} hidden={view !== 'members'}>
            <ul className="plain-list subscription-members">
                {selected.members.map((member) => (
                    <li key={member.id}>
                        {member.personName}
                        <small className="muted"> · Каяк: {member.usesKayak ? 'да' : 'нет'} · Весло: {member.usesPaddle ? 'да' : 'нет'} · Юбка: {member.usesSkirt ? 'да' : 'нет'}</small>
                    </li>
                ))}
            </ul>
            </div>
            {selected.status === 'DRAFT' && <p className="context-note">Черновик: начисления появятся после расчёта абонемента.</p>}
            <div className="subscription-footer">
            {!readOnly && selected.status === 'DRAFT' && (
                <button className="primary" disabled={submitting} onClick={() => finalize(selected)}>
                    Рассчитать абонемент
                </button>
            )}
            {!readOnly && selected.status === 'FINALIZED' && (
                <button className="primary" disabled={submitting} onClick={() => complete(selected)}>
                    Завершить абонемент
                </button>
            )}
            </div>
        </section>
    );
}
