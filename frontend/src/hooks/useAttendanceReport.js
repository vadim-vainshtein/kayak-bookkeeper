import { useCallback, useEffect, useRef, useState } from 'react';

export default function useAttendanceReport(api) {
    const [attendanceReport, setAttendanceReport] = useState(null);
    const [attendanceLoading, setAttendanceLoading] = useState(false);
    const [attendanceError, setAttendanceError] = useState('');
    const sequence = useRef(0);
    const controller = useRef(null);
    useEffect(() => () => {
        sequence.current++;
        controller.current?.abort();
    }, [api]);

    const loadAttendanceReport = useCallback(async (subscriptionIds = [], status = '') => {
        const request = ++sequence.current;
        controller.current?.abort();
        const nextController = new AbortController();
        controller.current = nextController;
        const appliedIds = [...subscriptionIds];
        setAttendanceLoading(true);
        setAttendanceError('');
        try {
            const parameters = new URLSearchParams();
            appliedIds.forEach((id) => parameters.append('subscriptionId', id));
            if (status) parameters.set('status', status);
            const filter = parameters.toString();
            const data = await api(`/api/reports/attendance${filter ? `?${filter}` : ''}`, { signal: nextController.signal });
            if (request !== sequence.current) return null;
            setAttendanceReport({ data, subscriptionIds: appliedIds, status });
            return data;
        } catch (error) {
            if (request === sequence.current && !nextController.signal.aborted)
                setAttendanceError(error.message || 'Не удалось загрузить отчёт по посещениям.');
            return null;
        } finally {
            if (request === sequence.current) setAttendanceLoading(false);
        }
    }, [api]);
    return { attendanceReport, attendanceLoading, attendanceError, loadAttendanceReport };
}
