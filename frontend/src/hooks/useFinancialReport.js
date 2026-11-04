import { useState } from 'react';

export default function useFinancialReport(api, setError) {
    const [financialReport, setFinancialReport] = useState(null);
    const [reportLoading, setReportLoading] = useState(false);
    const loadFinancialReport = async (subscriptionIds = []) => {
        setReportLoading(true);
        setError('');
        try {
            const filter = subscriptionIds.map((id) => `subscriptionId=${encodeURIComponent(id)}`).join('&');
            const nextReport = await api(`/api/reports/financial${filter ? `?${filter}` : ''}`);
            setFinancialReport({ data: nextReport, subscriptionIds });
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
