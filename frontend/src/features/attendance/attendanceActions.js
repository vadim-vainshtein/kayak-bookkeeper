export function createAttendanceActions({ api, run, refreshBalances }) {
    const loadRoster = (sessionId) => api(`/api/sessions/${sessionId}/roster`);
    const markAttendance = (sessionId, marks) =>
        run(async () => {
            for (const mark of marks)
                await api(`/api/sessions/${sessionId}/attendance`, {
                    method: 'PUT',
                    body: JSON.stringify(mark),
                });
        }, 'Отметки сохранены.');
    const addDropIn = (sessionId, personId) =>
        run(async () => {
            await api(`/api/sessions/${sessionId}/drop-ins`, {
                method: 'POST',
                body: JSON.stringify({ personId }),
            });
            await refreshBalances();
        }, 'Разовый посетитель добавлен.');
    return { loadRoster, markAttendance, addDropIn };
}
