import { statusLabels } from '../../shared/labels.js';
import { attendanceCellLabel, attendanceSessionLabel } from './attendanceReportModel.js';

const timestamp = (value) => value?.replace('T', ' ') || '';
const csvCell = (value) => {
    let text = String(value ?? '');
    // Quoting alone does not stop spreadsheet formula evaluation of user text.
    if (typeof value === 'string' && (/^[\s\uFEFF]*[=+\-@]/.test(text) || /^[\t\r\n]/.test(text))) text = "'" + text;
    return '"' + text.replaceAll('"', '""') + '"';
};

export function attendanceReportCsv(report, subscriptionLabel, statusLabel) {
    const summary = report.summary;
    const rows = [
        ['Отчёт по посещениям абонементов'],
        ['Абонементы', subscriptionLabel],
        ['Статус абонементов', statusLabel],
        ['Сформирован', timestamp(report.generatedAt)],
        ['Часовой пояс', report.timeZone],
        [], ['Сводка', 'Количество'],
        ['Запланировано тренировок', summary.scheduledSessions],
        ['Начались', summary.startedSessions],
        ['Сохранены отметки', summary.recordedSessions],
        ['Посещений по абонементу', summary.memberVisits],
        ['Гостевых посещений', summary.guestVisits],
        ['Всего посещений', summary.totalVisits],
        ['Уникальных посетителей', summary.uniqueVisitors],
        [], ['Абонементы'],
        ['Название', 'Статус', 'Текущий состав', 'Запланировано тренировок', 'Начались', 'Сохранены отметки', 'По абонементу', 'Гостевые', 'Всего посещений', 'Уникальных посетителей'],
        ...report.subscriptions.map((subscription) => [subscription.title || 'Без названия', statusLabels[subscription.status], subscription.memberCount,
            subscription.summary.scheduledSessions, subscription.summary.startedSessions, subscription.summary.recordedSessions,
            subscription.summary.memberVisits, subscription.summary.guestVisits, subscription.summary.totalVisits, subscription.summary.uniqueVisitors]),
        [], ['Матрицы посещений'],
        ['Текущий состав и все ранее пришедшие. Отсутствие отметки не определяет исторический состав группы.'],
    ];
    for (const subscription of report.subscriptions) {
        rows.push([], ['Абонемент', subscription.title || 'Без названия'],
            ['Каякер', 'В текущем составе', ...subscription.sessions.map((session) => timestamp(session.startsAt)), 'По абонементу', 'Гостевые', 'Всего'],
            ['Состояние тренировки', '', ...subscription.sessions.map(attendanceSessionLabel), '', '', '']);
        for (const person of subscription.people) {
            const visits = new Map(person.visits.map((visit) => [visit.sessionId, visit]));
            rows.push([person.personName, person.currentMember ? 'Да' : 'Нет',
                ...subscription.sessions.map((session) => attendanceCellLabel(session, visits.get(session.sessionId))),
                person.memberVisits, person.guestVisits, person.totalVisits]);
        }
        rows.push(['Всего пришли', '', ...subscription.sessions.map((session) => session.started ? session.totalVisits : 'Ещё не началась'),
            subscription.summary.memberVisits, subscription.summary.guestVisits, subscription.summary.totalVisits]);
    }
    return '\uFEFF' + rows.map((row) => row.map(csvCell).join(',')).join('\n');
}

export function downloadAttendanceReport(report, subscriptionLabel, statusLabel) {
    const blob = new Blob([attendanceReportCsv(report, subscriptionLabel, statusLabel)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'subscription-attendance-report.csv';
    link.click();
    URL.revokeObjectURL(url);
}
