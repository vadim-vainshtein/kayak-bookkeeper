import { formatDate } from '../../shared/format.js';
import { statusLabels } from '../../shared/labels.js';

export default function SubscriptionDetails({
    readOnly = false,
    selected,
    openEdit,
    openAttendance,
    finalize,
    complete,
    submitting,
}) {
    return (
        <section className="card">
            <div className="line">
                <div>
                    <h2>{selected.title || 'Абонемент без названия'}</h2>
                    <span className="status">{statusLabels[selected.status]}</span>
                </div>
                {!readOnly && <button onClick={openEdit}>Редактировать</button>}
            </div>
            {selected.description && <p>{selected.description}</p>}
            <p className="muted">Вместимость: {selected.capacity}</p>
            <h3>Расписание</h3>
            <ul className="plain-list">
                {selected.sessions.map((session) => (
                    <li key={session.id} className="session-row">
                        <span>{formatDate(session.startsAt)}</span>
                        {selected.status !== 'DRAFT' && (
                            <button type="button" onClick={() => openAttendance(session)}>
                                Перекличка
                            </button>
                        )}
                    </li>
                ))}
            </ul>
            <h3>Участники</h3>
            <ul className="plain-list">
                {selected.members.map((member) => (
                    <li key={member.id}>
                        {member.personName}
                        <small className="muted"> · Каяк: {member.usesKayak ? 'да' : 'нет'} · Весло: {member.usesPaddle ? 'да' : 'нет'} · Юбка: {member.usesSkirt ? 'да' : 'нет'}</small>
                    </li>
                ))}
            </ul>
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
        </section>
    );
}
