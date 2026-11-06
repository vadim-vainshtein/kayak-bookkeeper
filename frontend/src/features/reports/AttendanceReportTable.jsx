import { formatDate } from '../../shared/format.js';
import { statusLabels } from '../../shared/labels.js';
import { attendanceCellLabel, attendanceSessionLabel } from './attendanceReportModel.js';

export default function AttendanceReportTable({ subscription }) {
    const summary = subscription.summary;
    return (
        <section className="card attendance-report-subscription">
            <div className="line"><h2>{subscription.title || 'Без названия'}</h2><span className="status">{statusLabels[subscription.status]}</span></div>
            <p className="muted">Тренировок: {summary.scheduledSessions} · Начались: {summary.startedSessions} · Отмечены: {summary.recordedSessions} · Уникальных посетителей: {summary.uniqueVisitors}</p>
            <div className="balance-table-wrap">
                <table className="balance-table report-table attendance-report-table">
                    <caption className="visually-hidden">Посещения: {subscription.title || 'Без названия'}</caption>
                    <thead><tr>
                        <th scope="col">Каякер</th>
                        {subscription.sessions.map((session) => <th scope="col" key={session.sessionId}>
                            {formatDate(session.startsAt)}<small>{attendanceSessionLabel(session)}</small>
                        </th>)}
                        <th scope="col">По абонементу</th><th scope="col">Гостевые</th><th scope="col">Всего</th>
                    </tr></thead>
                    <tbody>
                        {subscription.people.map((person) => {
                            const visits = new Map(person.visits.map((visit) => [visit.sessionId, visit]));
                            return <tr key={person.personId}>
                                <th scope="row"><strong>{person.personName}</strong><small className="muted">{person.currentMember ? 'В текущем составе' : 'Вне текущего состава'}</small></th>
                                {subscription.sessions.map((session) => {
                                    const visit = session.started ? visits.get(session.sessionId) : undefined;
                                    const label = attendanceCellLabel(session, visit);
                                    return <td key={session.sessionId} title={label} className={visit ? 'attendance-report-present' : 'muted'}>
                                        <span aria-label={label}>{visit ? visit.guest ? 'Гость' : '✓' : session.started && session.recorded ? '—' : label}</span>
                                    </td>;
                                })}
                                <td>{person.memberVisits}</td><td>{person.guestVisits}</td><td><strong>{person.totalVisits}</strong></td>
                            </tr>;
                        })}
                        {!subscription.people.length && <tr><td colSpan={subscription.sessions.length + 4}>Посетителей и участников пока нет.</td></tr>}
                    </tbody>
                    <tfoot><tr>
                        <th scope="row">Всего пришли</th>
                        {subscription.sessions.map((session) => <td key={session.sessionId}>{session.started ? session.totalVisits : 'Ещё не началась'}</td>)}
                        <td>{summary.memberVisits}</td><td>{summary.guestVisits}</td><td>{summary.totalVisits}</td>
                    </tr></tfoot>
                </table>
            </div>
        </section>
    );
}
