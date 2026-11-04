import { useRef, useState } from 'react';

export default function useRoster({ loadRoster, markAttendance, addDropIn }) {
    const [attendanceSession, setAttendanceSession] = useState(null);
    const [roster, setRoster] = useState(null);
    const [rosterLoading, setRosterLoading] = useState(false);
    const [rosterError, setRosterError] = useState('');
    const rosterRequest = useRef(0);
    async function refreshRoster(sessionId) {
        const request = ++rosterRequest.current;
        setRosterLoading(true);
        setRosterError('');
        try {
            const next = await loadRoster(sessionId);
            if (request === rosterRequest.current) {
                setRoster(next);
                setRosterLoading(false);
            }
            return next;
        } catch (requestError) {
            if (request === rosterRequest.current) {
                setRoster(null);
                setRosterError(requestError.message || 'Не удалось загрузить перекличку.');
                setRosterLoading(false);
            }
            return null;
        }
    }

    function openAttendance(session) {
        setAttendanceSession(session);
        setRoster(null);
        setRosterError('');
        refreshRoster(session.id);
    }

    function closeAttendance() {
        rosterRequest.current += 1;
        setAttendanceSession(null);
        setRoster(null);
        setRosterLoading(false);
        setRosterError('');
    }

    async function saveAttendance(marks) {
        if (!attendanceSession || !marks.length) return false;
        const ok = await markAttendance(attendanceSession.id, marks);
        await refreshRoster(attendanceSession.id);
        return ok;
    }

    async function createDropIn(personId) {
        if (!attendanceSession) return false;
        const ok = await addDropIn(attendanceSession.id, personId);
        if (ok) await refreshRoster(attendanceSession.id);
        return ok;
    }

    return {
        attendanceSession,
        roster,
        rosterLoading,
        rosterError,
        refreshRoster,
        openAttendance,
        closeAttendance,
        saveAttendance,
        createDropIn,
    };
}
