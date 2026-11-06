import { useEffect, useState } from 'react';
import { formatDate } from '../../shared/format.js';
import AttendanceReportTable from './AttendanceReportTable.jsx';
import { downloadAttendanceReport } from './attendanceReportCsv.js';
import { attendanceReportStatusLabels, attendanceSubscriptionLabel } from './attendanceReportModel.js';

export default function AttendanceReports({ reportState, loading, error, loadReport, subscriptions }) {
    const report = reportState?.data;
    const appliedIds = reportState?.subscriptionIds || [];
    const appliedStatus = reportState?.status || '';
    const [subscriptionIds, setSubscriptionIds] = useState(() => appliedIds);
    const [status, setStatus] = useState(() => appliedStatus);
    useEffect(() => { loadReport(appliedIds, appliedStatus); }, [loadReport]);
    const eligibleSubscriptions = subscriptions.filter((subscription) => subscription.status !== 'DRAFT');
    const draftLabel = attendanceSubscriptionLabel(subscriptionIds, eligibleSubscriptions);
    const appliedLabel = attendanceSubscriptionLabel(appliedIds, [...(report?.subscriptions || []), ...subscriptions]);
    const summary = report?.summary;

    async function apply(event) {
        event.preventDefault();
        if (loading) return;
        const next = await loadReport(subscriptionIds, status);
        if (!next) { setSubscriptionIds(appliedIds); setStatus(appliedStatus); }
    }
    return (
        <div className="reports">
            <section className="card">
                <div className="line"><div><h2>Отчёт по посещениям</h2>
                    <p className="muted">Текущий состав и все ранее пришедшие, включая гостей. Будущие тренировки не входят в итоги посещений.</p>
                </div><button disabled={loading || !report} onClick={() => downloadAttendanceReport(report, appliedLabel, attendanceReportStatusLabels[appliedStatus])}>Скачать CSV</button></div>
                <form className="report-period report-status-period" onSubmit={apply}>
                    <details className="report-subscription-picker"><summary>Абонементы: <strong>{draftLabel}</strong></summary>
                        <div className="report-filters">
                            <button type="button" disabled={loading || !subscriptionIds.length} onClick={() => setSubscriptionIds([])}>Все абонементы</button>
                            {eligibleSubscriptions.map((subscription) => <label className="checkbox" key={subscription.id}>
                                <input type="checkbox" disabled={loading} checked={subscriptionIds.includes(subscription.id)}
                                    onChange={() => setSubscriptionIds((ids) => ids.includes(subscription.id) ? ids.filter((id) => id !== subscription.id) : [...ids, subscription.id])} />
                                {subscription.title || 'Без названия'}
                            </label>)}
                        </div>
                    </details>
                    <label>Статус абонемента<select disabled={loading} value={status} onChange={(event) => setStatus(event.target.value)}>
                        {Object.entries(attendanceReportStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select></label>
                    <button className="primary" disabled={loading}>Показать</button>
                </form>
                {report && <p className="muted report-applied-filters">Показано: {appliedLabel} · Статус: {attendanceReportStatusLabels[appliedStatus]}<br />
                    Сформирован: {formatDate(report.generatedAt)} · Часовой пояс: {report.timeZone}</p>}
            </section>
            {error && <p className="notice" role="alert">{error}</p>}
            {loading && <p className="muted" role="status">Отчёт по посещениям загружается…</p>}
            {report && <>
                <section className="report-summary" aria-label="Сводка посещений">
                    <article><span>Всего посещений</span><strong>{summary.totalVisits}</strong><small>По абонементу: {summary.memberVisits} · Гостевые: {summary.guestVisits}</small></article>
                    <article><span>Уникальных посетителей</span><strong>{summary.uniqueVisitors}</strong></article>
                    <article><span>Тренировки начались</span><strong>{summary.startedSessions} из {summary.scheduledSessions}</strong></article>
                    <article><span>Сохранены отметки</span><strong>{summary.recordedSessions} из {summary.startedSessions}</strong></article>
                </section>
                <p className="muted attendance-report-help">✓ — посещение по абонементу; «Гость» — разовое посещение; «—» — нет отметки посещения.
                    «Не отмечено» — посещения тренировки ещё не сохраняли, по умолчанию никто не пришёл. История состава группы не фиксируется:
                    отсутствие отметки не означает, что человек входил в состав на ту дату.</p>
                {report.subscriptions.map((subscription) => <AttendanceReportTable key={subscription.subscriptionId} subscription={subscription} />)}
                {!report.subscriptions.length && <p className="muted">Абонементы не найдены.</p>}
            </>}
        </div>
    );
}
