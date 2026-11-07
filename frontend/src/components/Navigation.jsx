import Icon from './Icon.jsx';

export const sections = [
    ['overview', 'Обзор', 'Клуб в одном месте'],
    ['subscriptions', 'Абонементы', 'Расписание, участники и посещения'],
    ['people', 'Каякеры', 'Справочник участников клуба'],
    ['ledger', 'Начисления и оплаты', 'Балансы каякеров и распределение средств'],
    ['reports', 'Отчёты', 'Финансы и посещения'],
    ['rates', 'Тарифы', 'Стоимость услуг и периоды действия'],
];

export function MoreMenu({ go, readOnly, logout }) {
    return <section className="more-menu" aria-label="Другие разделы">
        {sections.filter(([id]) => ['overview', 'reports', 'rates'].includes(id)).map(([id, title]) =>
            <button type="button" key={id} onClick={() => go(id)}><Icon name={id} />{title}<Icon name="arrow" /></button>)}
        {!readOnly && <>
            <button type="button" onClick={() => go('users')}><Icon name="people" />Пользователи<Icon name="arrow" /></button>
            <button type="button" onClick={() => go('password')}><Icon name="account" />Пароль<Icon name="arrow" /></button>
        </>}
        <button type="button" onClick={logout}>Выйти<Icon name="arrow" /></button>
    </section>;
}

export default function Navigation({ tab, go, readOnly, user, logout, busy = false }) {
    const moreActive = !['subscriptions', 'people', 'ledger'].includes(tab);
    return <>
        <aside className="sidebar">
            <div className="brand"><Icon name="waves" /><div>Kayak<span>BOOKKEEPER</span></div></div>
            <span className="nav-label">КЛУБ</span>
            <nav className="side-navigation" aria-label="Разделы приложения">
                {sections.map(([id, title]) => <button type="button" key={id} disabled={busy} className={tab === id ? 'active' : ''}
                    aria-current={tab === id ? 'page' : undefined} onClick={() => go(id)}><Icon name={id} />{title}</button>)}
            </nav>
            <div className="sidebar-account">
                <div className="account-summary"><span className="account-avatar"><Icon name="account" /></span><div>{user}<small>{readOnly ? 'Только чтение' : 'Администратор'}</small></div></div>
                {!readOnly && <nav className="account-navigation" aria-label="Управление аккаунтом">
                    <button type="button" disabled={busy} aria-current={tab === 'users' ? 'page' : undefined} onClick={() => go('users')}>Пользователи</button>
                    <button type="button" disabled={busy} aria-current={tab === 'password' ? 'page' : undefined} onClick={() => go('password')}>Пароль</button>
                </nav>}
                <button type="button" className="logout" disabled={busy} onClick={logout}>Выйти</button>
            </div>
        </aside>
        <nav className="bottom-navigation" aria-label="Основные разделы">
            {[['subscriptions', 'Абонементы'], ['people', 'Каякеры'], ['ledger', 'Финансы'], ['more', 'Ещё']].map(([id, title]) => {
                const active = id === 'more' ? moreActive : tab === id;
                return <button type="button" key={id} disabled={busy} className={active ? 'active' : ''}
                    aria-current={active ? 'page' : undefined} onClick={() => go(id)}><Icon name={id} />{title}</button>;
            })}
        </nav>
    </>;
}
