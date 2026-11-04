import { useEffect, useState } from 'react';
import { money } from '../../shared/format.js';
import { chargeLabels } from '../../shared/labels.js';
import { downloadFinancialReport } from './reportCsv.js';
import ReportSummary from './ReportSummary.jsx';
import SubscriptionReportTable from './SubscriptionReportTable.jsx';

export default function Reports({ reportState, loading, loadReport, subscriptions }) {
    const report = reportState?.data || null;
    const appliedSubscriptionIds = reportState?.subscriptionIds || [];
    const [subscriptionIds, setSubscriptionIds] = useState(() => appliedSubscriptionIds);
    useEffect(() => {
        loadReport(appliedSubscriptionIds);
    }, []);
    const selectedSubscriptions = subscriptions.filter((subscription) =>
        subscriptionIds.includes(subscription.id),
    );
    const subscriptionLabel = !subscriptionIds.length
        ? 'Все абонементы'
        : selectedSubscriptions.length === 1
          ? selectedSubscriptions[0].title || `Абонемент #${selectedSubscriptions[0].id}`
          : `Выбрано: ${selectedSubscriptions.length}`;
    const toggleSubscription = (id) =>
        setSubscriptionIds((ids) => (ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id]));
    const exportCsv = () => {
        const csvSubscriptionLabel = selectedSubscriptions.length === 1
            ? selectedSubscriptions[0].title || 'Без названия'
            : subscriptionLabel;
        if (report) downloadFinancialReport(report, csvSubscriptionLabel);
    };
    const submitReport = async (event) => {
        event.preventDefault();
        const nextReport = await loadReport(subscriptionIds);
        if (!nextReport && reportState) {
            setSubscriptionIds(reportState.subscriptionIds);
        }
    };
    return (
        <div className="reports">
            <section className="card">
                <div className="line">
                    <div>
                        <h2>Финансовая отчётность</h2>
                        <p className="muted">
                            Итоги по абонементам за всё время. Выберите один или несколько абонементов либо
                            оставьте все.
                        </p>
                    </div>
                    <button onClick={exportCsv} disabled={!report}>
                        Скачать CSV
                    </button>
                </div>
                <form className="report-period" onSubmit={submitReport}>
                    <details className="report-subscription-picker">
                        <summary>
                            Абонементы: <strong>{subscriptionLabel}</strong>
                        </summary>
                        <div className="report-filters">
                            <button
                                type="button"
                                onClick={() => setSubscriptionIds([])}
                                disabled={loading || !subscriptionIds.length}
                            >
                                Все абонементы
                            </button>
                            {subscriptions.map((subscription) => (
                                <label className="checkbox" key={subscription.id}>
                                    <input
                                        type="checkbox"
                                        checked={subscriptionIds.includes(subscription.id)}
                                        onChange={() => toggleSubscription(subscription.id)}
                                        disabled={loading}
                                    />
                                    {subscription.title || `Абонемент #${subscription.id}`}
                                </label>
                            ))}
                        </div>
                    </details>
                    <button className="primary" disabled={loading}>
                        Показать
                    </button>
                </form>
            </section>
            {loading && (
                <p className="muted" role="status">
                    Отчёт загружается…
                </p>
            )}
            {report && (
                <>
                    <ReportSummary report={report} />
                    <SubscriptionReportTable report={report} />
                    {report.unassignedCharges.length > 0 && (
                        <section className="card">
                            <div className="line">
                                <h2>Начисления без абонемента</h2>
                                <span className="muted">{report.unassignedCharges.length}</span>
                            </div>
                            <div className="balance-table-wrap">
                                <table className="balance-table report-table">
                                    <thead>
                                        <tr>
                                            <th>Каякер</th>
                                            <th>Тип</th>
                                            <th>Описание</th>
                                            <th>Начислено</th>
                                            <th>Оплачено</th>
                                            <th>Недостача</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {report.unassignedCharges.map((charge) => (
                                            <tr key={charge.chargeId}>
                                                <td>
                                                    <strong>{charge.personName}</strong>
                                                </td>
                                                <td>{chargeLabels[charge.type]}</td>
                                                <td>{charge.description || '—'}</td>
                                                <td>{money(charge.amount)}</td>
                                                <td>{money(charge.allocated)}</td>
                                                <td
                                                    className={
                                                        charge.outstanding > 0
                                                            ? 'debt'
                                                            : charge.outstanding < 0
                                                              ? 'credit'
                                                              : ''
                                                    }
                                                >
                                                    {money(charge.outstanding)}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    )}
                </>
            )}
        </div>
    );
}
