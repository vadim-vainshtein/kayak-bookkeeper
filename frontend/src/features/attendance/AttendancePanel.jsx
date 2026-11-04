import { useEffect, useState } from 'react';
import { attendanceMarks } from './attendanceDraft.js';
import { formatDate } from '../../shared/format.js';

export default function AttendancePanel({
    readOnly = false,
    session,
    roster,
    loading,
    error,
    people,
    submitting,
    onBack,
    onRetry,
    onSaveAttendance,
    onAddDropIn,
}) {
    const [dropInPersonId, setDropInPersonId] = useState('');
    const [attendanceDraft, setAttendanceDraft] = useState({});
    const recordedPeople = new Set(roster?.attendance.map((entry) => entry.personId));
    const eligiblePeople = people.filter((person) => person.active && !recordedPeople.has(person.id));
    const members = roster?.attendance.filter((entry) => entry.type === 'SUBSCRIPTION') || [];
    const dropIns = roster?.attendance.filter((entry) => entry.type === 'DROP_IN') || [];

    useEffect(() => setDropInPersonId(''), [session.id]);
    useEffect(() => {
        if (!roster) return;
        setAttendanceDraft(
            Object.fromEntries(members.map((entry) => [entry.personId, entry.status !== 'ABSENT'])),
        );
    }, [roster]);

    const isPresent = (entry) => attendanceDraft[entry.personId] ?? entry.status !== 'ABSENT';
    const hasUnsavedAttendance = members.some(
        (entry) => entry.status === 'PLANNED' || isPresent(entry) !== (entry.status === 'PRESENT'),
    );
    const saveAttendance = async () => {
        const marks = attendanceMarks(members, attendanceDraft);
        await onSaveAttendance(marks);
    };

    return (
        <section className="card attendance-panel">
            <button type="button" onClick={onBack}>
                ← К абонементу
            </button>
            <div className="line attendance-heading">
                <div>
                    <h2>Перекличка</h2>
                    <p className="muted">{formatDate(session.startsAt)}</p>
                </div>
            </div>
            {loading ? (
                <p className="muted" role="status">
                    Загрузка переклички…
                </p>
            ) : error ? (
                <div className="roster-error" role="alert">
                    <p>{error}</p>
                    <button type="button" onClick={onRetry}>
                        Повторить
                    </button>
                </div>
            ) : (
                roster && (
                    <>
                        <div className="attendance-summary">
                            <strong>Мест: {roster.capacity}</strong>
                            <span className={roster.vacanciesForDropIns > 0 ? 'credit' : 'muted'}>
                                Свободно для разовых: {roster.vacanciesForDropIns}
                            </span>
                        </div>
                        <h3>Участники абонемента</h3>
                        <ul className="attendance-list">
                            {members.map((entry) => (
                                <li key={entry.personId} className="attendance-row">
                                    <strong>{entry.personName}</strong>
                                    {readOnly ? <span className="status">{{ PLANNED: 'Не отмечен', PRESENT: 'Пришёл', ABSENT: 'Отсутствовал' }[entry.status]}</span> : <label className="attendance-check">
                                        <input
                                            type="checkbox"
                                            checked={isPresent(entry)}
                                            disabled={submitting}
                                            onChange={(event) =>
                                                setAttendanceDraft((draft) => ({
                                                    ...draft,
                                                    [entry.personId]: event.target.checked,
                                                }))
                                            }
                                        />
                                        Пришёл
                                    </label>}
                                </li>
                            ))}
                        </ul>
                        {!readOnly && <div className="attendance-save">
                            <button
                                type="button"
                                className="primary"
                                disabled={submitting || !hasUnsavedAttendance}
                                onClick={saveAttendance}
                            >
                                Сохранить отметки
                            </button>
                            <small className="muted">
                                Неотмеченные каякеры будут сохранены как отсутствующие.
                            </small>
                        </div>}
                        <section className="drop-in-panel">
                            <h3>Разовые посетители</h3>
                            {dropIns.length > 0 && (
                                <ul className="plain-list">
                                    {dropIns.map((entry) => (
                                        <li key={entry.personId}>
                                            {entry.personName} <span className="status">пришёл</span>
                                        </li>
                                    ))}
                                </ul>
                            )}
                            {!readOnly && (hasUnsavedAttendance ? (
                                <p className="muted">
                                    Сохраните отметки, чтобы обновить свободные места для разовых посетителей.
                                </p>
                            ) : roster.vacanciesForDropIns > 0 ? (
                                eligiblePeople.length > 0 ? (
                                    <div className="compact-form">
                                        <label>
                                            Каякер
                                            <select
                                                value={dropInPersonId}
                                                onChange={(event) => setDropInPersonId(event.target.value)}
                                            >
                                                <option value="">Выберите каякера</option>
                                                {eligiblePeople.map((person) => (
                                                    <option key={person.id} value={person.id}>
                                                        {person.fullName}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <button
                                            type="button"
                                            className="primary"
                                            disabled={!dropInPersonId || submitting}
                                            onClick={async () => {
                                                if (await onAddDropIn(Number(dropInPersonId)))
                                                    setDropInPersonId('');
                                            }}
                                        >
                                            Добавить разового
                                        </button>
                                    </div>
                                ) : (
                                    <p className="muted">Нет доступных активных каякеров.</p>
                                )
                            ) : (
                                <p className="muted">Нет мест, освобождённых отмеченным отсутствием.</p>
                            ))}
                            <p className="muted">
                                Для разового посетителя применяется действующий тариф разовой тренировки.
                            </p>
                        </section>
                    </>
                )
            )}
        </section>
    );
}
