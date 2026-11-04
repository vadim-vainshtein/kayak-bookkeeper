import { useState } from 'react';
import Login from './components/Login.jsx';
import Overview from './features/overview/Overview.jsx';
import People from './features/people/People.jsx';
import Subscriptions from './features/subscriptions/Subscriptions.jsx';
import Rates from './features/rates/Rates.jsx';
import Ledger from './features/ledger/Ledger.jsx';
import Reports from './features/reports/Reports.jsx';
import PasswordSettings from './features/account/PasswordSettings.jsx';
import Users from './features/account/Users.jsx';
import useAuth from './hooks/useAuth.js';
import useClubData from './hooks/useClubData.js';
import useMutation from './hooks/useMutation.js';
import usePersonBalance from './hooks/usePersonBalance.js';
import useFinancialReport from './hooks/useFinancialReport.js';
import useInlineValidation from './hooks/useInlineValidation.js';
import { createPeopleActions } from './features/people/peopleActions.js';
import { createRateActions } from './features/rates/rateActions.js';
import { createSubscriptionActions } from './features/subscriptions/subscriptionActions.js';
import { createAttendanceActions } from './features/attendance/attendanceActions.js';
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
    const { submitting, error, message, setError, run, submit } = useMutation();
    const { people, personBalances, subscriptions, rates, loading, load, refreshBalances } = useClubData(
        api,
        authorization,
        setError,
    );
    const { personId, balance, loadBalance, selectPerson, showPeople } = usePersonBalance(api, setError);
    const { financialReport, reportLoading, loadFinancialReport } = useFinancialReport(api, setError);
    useInlineValidation();
    const { addPerson, updatePerson } = createPeopleActions({ api, submit, load });
    const { createRate, endRate } = createRateActions({ api, submit, load });
    const { createSubscription, previewSubscriptionUpdate, updateSubscription, finalize, complete } =
        createSubscriptionActions({ api, submit, load, run, setError });
    const { loadRoster, markAttendance, addDropIn } = createAttendanceActions({ api, run, refreshBalances });
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
        overview: <Overview readOnly={readOnly} people={people} subscriptions={subscriptions} rates={rates} go={setTab} />,
        people: <People readOnly={readOnly} people={people} onAdd={addPerson} onUpdate={updatePerson} submitting={submitting} />,
        subscriptions: (
            <Subscriptions
                readOnly={readOnly}
                people={people}
                subscriptions={subscriptions}
                create={createSubscription}
                update={updateSubscription}
                preview={previewSubscriptionUpdate}
                finalize={finalize}
                complete={complete}
                loadRoster={loadRoster}
                markAttendance={markAttendance}
                addDropIn={addDropIn}
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
            <Reports
                reportState={financialReport}
                loading={reportLoading}
                loadReport={loadFinancialReport}
                subscriptions={subscriptions}
            />
        ),
        password: !readOnly && <PasswordSettings onSubmit={changePassword} submitting={submitting} />,
        users: !readOnly && <Users api={api} />,
    };
    return (
        <main className="shell">
            <header className="topbar">
                <div>
                    <h1>Kayak Bookkeeper</h1>
                    <span className="muted">Управление клубом</span>
                </div>
                <div>
                    <span className="muted">{user} · {readOnly ? 'Только чтение' : 'Администратор'} · </span>
                    <button onClick={auth.logout}>Выйти</button>
                </div>
            </header>
            <nav className="tabs" aria-label="Разделы приложения">
                {[
                    ['overview', 'Обзор'],
                    ['people', 'Каякеры'],
                    ['subscriptions', 'Абонементы'],
                    ['rates', 'Тарифы'],
                    ['ledger', 'Начисления и оплаты'],
                    ['reports', 'Отчёты'],
                    ...(!readOnly ? [['users', 'Пользователи'], ['password', 'Пароль']] : []),
                ].map(([id, title]) => (
                    <button
                        key={id}
                        className={tab === id ? 'active' : ''}
                        aria-current={tab === id ? 'page' : undefined}
                        onClick={() => setTab(id)}
                    >
                        {title}
                    </button>
                ))}
            </nav>
            <div className="spacer" />
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
    );
}
