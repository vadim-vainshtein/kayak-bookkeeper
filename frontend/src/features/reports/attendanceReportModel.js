export const attendanceReportStatusLabels = {
    '': 'Рассчитанные и завершённые', FINALIZED: 'Рассчитанные', COMPLETED: 'Завершённые',
};

export function attendanceSessionLabel(session) {
    return !session.started ? 'Ещё не началась' : session.recorded ? 'Отмечено' : 'Не отмечено';
}

export function attendanceCellLabel(session, visit) {
    if (!session.started) return 'Ещё не началась';
    if (visit) return visit.guest ? 'Гость' : 'По абонементу';
    return session.recorded ? 'Нет отметки посещения' : 'Не отмечено';
}

export function attendanceSubscriptionLabel(ids, subscriptions) {
    if (!ids.length) return 'Все абонементы';
    if (ids.length === 1) return subscriptions.find((subscription) => (subscription.id ?? subscription.subscriptionId) === ids[0])?.title || 'Без названия';
    return `Выбрано: ${ids.length}`;
}
