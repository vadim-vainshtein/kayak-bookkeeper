import { useState } from 'react';

export default function useFinancialReport(api, setError) {
    const [financialReport, setFinancialReport] = useState(null);
    const [reportLoading, setReportLoading] = useState(false);
    const loadFinancialReport = async (subscriptionIds = [], status = '') => {
        const appliedIds = [...subscriptionIds];
        setReportLoading(true);
        setError('');
        try {
            const parameters = appliedIds.map((id) => `subscriptionId=${encodeURIComponent(id)}`);
            if (status) parameters.push(`status=${encodeURIComponent(status)}`);
            const filter = parameters.join('&');
            const nextReport = await api(`/api/reports/financial${filter ? `?${filter}` : ''}`);
            setFinancialReport({ data: nextReport, subscriptionIds: appliedIds, status });
            return nextReport;
        } catch (requestError) {
            setError(requestError.message);
            return null;
        } finally {
            setReportLoading(false);
        }
    };

    return { financialReport, reportLoading, loadFinancialReport };
}
