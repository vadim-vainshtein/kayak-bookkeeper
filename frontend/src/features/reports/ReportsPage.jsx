import Reports from './Reports.jsx';
import AttendanceReports from './AttendanceReports.jsx';

export default function ReportsPage({ reportType, setReportType, financial, attendance, subscriptions }) {
    return <>
        <nav className="tabs report-type-switch" aria-label="Виды отчётов">
            <button className={reportType === 'financial' ? 'active' : ''} aria-pressed={reportType === 'financial'} onClick={() => setReportType('financial')}>Финансы</button>
            <button className={reportType === 'attendance' ? 'active' : ''} aria-pressed={reportType === 'attendance'} onClick={() => setReportType('attendance')}>Посещения</button>
        </nav>
        {reportType === 'attendance' ? <AttendanceReports {...attendance} subscriptions={subscriptions} /> : <Reports {...financial} subscriptions={subscriptions} />}
    </>;
}
