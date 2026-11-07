import { useLayoutEffect, useRef, useState } from 'react';
import Login from './components/Login.jsx';
import Navigation, { MoreMenu, sections } from './components/Navigation.jsx';
import { NavigationLock } from './components/NavigationLock.jsx';
import Overview from './features/overview/Overview.jsx';
import People from './features/people/People.jsx';
import Subscriptions from './features/subscriptions/Subscriptions.jsx';
import Rates from './features/rates/Rates.jsx';
import Ledger from './features/ledger/Ledger.jsx';
import ReportsPage from './features/reports/ReportsPage.jsx';
import PasswordSettings from './features/account/PasswordSettings.jsx';
import Users from './features/account/Users.jsx';
import useAuth from './hooks/useAuth.js';
import useClubData from './hooks/useClubData.js';
import useMutation from './hooks/useMutation.js';
import usePersonBalance from './hooks/usePersonBalance.js';
import useFinancialReport from './hooks/useFinancialReport.js';
import useAttendanceReport from './hooks/useAttendanceReport.js';
import useInlineValidation from './hooks/useInlineValidation.js';
import { createPeopleActions } from './features/people/peopleActions.js';
import { createRateActions } from './features/rates/rateActions.js';
import { createSubscriptionActions } from './features/subscriptions/subscriptionActions.js';
import { createLedgerActions } from './features/ledger/ledgerActions.js';
import { createAccountActions } from './features/account/accountActions.js';

export default function App() {
    const auth = useAuth();
    if (!auth.authorization) return <Login onLogin={auth.login} />;
    return <ClubApp key={auth.account.id} auth={auth} />;
}

function ClubApp({ auth }) {
    const { authorization, user, account, api, setAuthorization } = auth;
    const readOnly = account.role !== 'ADMIN';
    const [tab, setTab] = useState('overview');
    const pageHeading = useRef(null);
    useLayoutEffect(() => { pageHeading.current?.focus(); }, [tab]);
    const [navigationBlocked, setNavigationBlocked] = useState(false);
    const navigate = (next) => { if (!navigationBlocked) setTab(next); };
    const logout = () => { if (!navigationBlocked) auth.logout(); };
    const [reportType, setReportType] = useState('financial');
    const { submitting, error, message, setError, run, submit } = useMutation();
    const { people, personBalances, subscriptions, rates, loading, load, refreshBalances } = useClubData(
        api,
        authorization,
        setError,
    );
    const { personId, balance, loadBalance, selectPerson, showPeople } = usePersonBalance(api, setError);
    const { financialReport, reportLoading, loadFinancialReport } = useFinancialReport(api, setError);
    const { attendanceReport, attendanceLoading, attendanceError, loadAttendanceReport } = useAttendanceReport(api);
    useInlineValidation();
    const { addPerson, updatePerson } = createPeopleActions({ api, submit, load });
    const { createRate, endRate } = createRateActions({ api, submit, load });
    const { createSubscription, previewSubscriptionUpdate, updateSubscription, finalize, complete } =
        createSubscriptionActions({ api, submit, load, run, setError });
    const { addCharge, saveCharge, deleteCharge, addPayment, allocatePayment, savePayment, cancelAllocation } = createLedgerActions({
        api,
        run,
        submit,
        loadBalance,
        refreshBalances,
        personId,
    });
    const { changePassword } = createAccountActions({ api, submit, user, setAuthorization });
    const content = {
        overview: <Overview readOnly={readOnly} people={people} subscriptions={subscriptions} rates={rates} go={navigate} />,
        people: <People readOnly={readOnly} people={people} onAdd={addPerson} onUpdate={updatePerson} submitting={submitting} />,
        subscriptions: (
            <Subscriptions
                readOnly={readOnly}
                people={people}
                subscriptions={subscriptions}
                api={api}
                refreshData={load}
                create={createSubscription}
                update={updateSubscription}
                preview={previewSubscriptionUpdate}
                finalize={finalize}
                complete={complete}
                submitting={submitting}
            />
        ),
        rates: <Rates readOnly={readOnly} rates={rates} create={createRate} end={endRate} submitting={submitting} />,
        ledger: (
            <Ledger
                readOnly={readOnly}
                personBalances={personBalances}
                subscriptions={subscriptions}
                personId={personId}
                balance={balance}
                submitting={submitting}
                selectPerson={selectPerson}
                showPeople={showPeople}
                addCharge={addCharge}
                saveCharge={saveCharge}
                deleteCharge={deleteCharge}
                addPayment={addPayment}
                allocatePayment={allocatePayment}
                savePayment={savePayment}
                cancelAllocation={cancelAllocation}
            />
        ),
        reports: (
            <ReportsPage
                reportType={reportType}
                setReportType={setReportType}
                financial={{ reportState: financialReport, loading: reportLoading, loadReport: loadFinancialReport }}
                attendance={{ reportState: attendanceReport, loading: attendanceLoading, error: attendanceError, loadReport: loadAttendanceReport }}
                subscriptions={subscriptions}
            />
        ),
        password: !readOnly && <PasswordSettings onSubmit={changePassword} submitting={submitting} />,
        users: !readOnly && <Users api={api} />,
        more: <MoreMenu go={navigate} readOnly={readOnly} logout={logout} />,
    };
    const [title, subtitle] = sections.find(([id]) => id === tab)?.slice(1)
        || (tab === 'users' ? ['Пользователи', 'Учётные записи и роли']
            : tab === 'password' ? ['Пароль', 'Безопасность вашей учётной записи'] : ['Ещё', `${user} · ${readOnly ? 'Только чтение' : 'Администратор'}`]);
    return (
        <NavigationLock.Provider value={setNavigationBlocked}>
        <div className="app-shell">
            <a className="skip-link" href="#main-content">К содержимому</a>
            <Navigation tab={tab} go={navigate} readOnly={readOnly} user={user} logout={logout} busy={navigationBlocked} />
            <div className="workspace">
            <header className="topbar">
                <span>Управление клубом</span>
                <span className="topbar-account">{user} · {readOnly ? 'Только чтение' : 'Администратор'}</span>
            </header>
            <main id="main-content" className="page-content">
            <div className="page-heading"><h1 ref={pageHeading} tabIndex={-1}>{title}</h1><p className="muted">{subtitle}</p></div>
            {error && (
                <p className="notice" role="alert">
                    {error}
                </p>
            )}
            {message && (
                <p className="notice success" role="status">
                    {message}
                </p>
            )}
            {loading ? (
                <p className="muted" role="status">
                    Загрузка…
                </p>
            ) : (
                content[tab]
            )}
            </main>
            </div>
        </div>
        </NavigationLock.Provider>
    );
}
