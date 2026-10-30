import { useEffect, useState } from 'react';
import { basicAuth, createApi } from './api';
import Login from './components/Login';
import PasswordForm from './components/PasswordForm';

const formatDate = (value) => value
  ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: value.includes('T') ? 'short' : undefined }).format(new Date(value))
  : '—';

function Overview({ people, subscriptions, rates }) {
  return (
    <div className="grid">
      <section className="card"><h3>Каякеры</h3><div className="metric">{people.filter((person) => person.active).length}</div><p className="muted">активных</p></section>
      <section className="card"><h3>Абонементы</h3><div className="metric">{subscriptions.length}</div><p className="muted">всего</p></section>
      <section className="card"><h3>Тарифы</h3><div className="metric">{rates.length}</div><p className="muted">настроено</p></section>
      <section className="card wide"><h2>Быстрый старт</h2><p className="muted">Добавьте каякеров, настройте тарифы и создайте абонемент через API или Swagger.</p></section>
    </div>
  );
}

function People({ people, onAdd }) {
  return (
    <div className="grid">
      <section className="card">
        <h2>Новый каякер</h2>
        <form onSubmit={onAdd}>
          <label>Имя<input name="fullName" required placeholder="Имя и фамилия" /></label>
          <label>Телефон<input name="phone" placeholder="+7…" /></label>
          <button className="primary">Добавить</button>
        </form>
      </section>
      <section className="card">
        <h2>Каякеры</h2>
        <ul className="list">{people.map((person) => <li key={person.id}><div className="line"><strong>{person.fullName}</strong><span className="muted">{person.active ? 'активен' : 'неактивен'}</span></div><small className="muted">{person.phone || 'Телефон не указан'}</small></li>)}</ul>
      </section>
    </div>
  );
}

function Subscriptions({ subscriptions }) {
  return <section className="card"><h2>Абонементы</h2><ul className="list">{subscriptions.map((subscription) => <li key={subscription.id}><div className="line"><strong>{subscription.title || 'Без названия'}</strong><span className="muted">{subscription.status}</span></div><div className="muted">{subscription.members.length}/{subscription.capacity} участников · {subscription.sessions.length} тренировок</div>{subscription.sessions[0] && <small className="muted">Начало: {formatDate(subscription.sessions[0].startsAt)}</small>}</li>)}</ul></section>;
}

function Rates({ rates }) {
  return <section className="card"><h2>Тарифы</h2><ul className="list">{rates.map((rate) => <li key={rate.id}><div className="line"><strong>{rate.code}</strong><span>{new Intl.NumberFormat('ru-RU').format(rate.amount)} ₸</span></div><small className="muted">{formatDate(rate.effectiveFrom)} — {formatDate(rate.effectiveTo)}</small></li>)}</ul></section>;
}

export default function App() {
  const [authorization, setAuthorization] = useState(null);
  const [user, setUser] = useState('admin');
  const [tab, setTab] = useState('overview');
  const [people, setPeople] = useState([]);
  const [subscriptions, setSubscriptions] = useState([]);
  const [rates, setRates] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const api = createApi(authorization, () => setAuthorization(null));
  async function load() {
    setLoading(true);
    setError('');
    try {
      const [loadedPeople, loadedSubscriptions, loadedRates] = await Promise.all([api('/api/people'), api('/api/subscriptions'), api('/api/pricing-rates')]);
      setPeople(loadedPeople);
      setSubscriptions(loadedSubscriptions);
      setRates(loadedRates);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { if (authorization) load(); }, [authorization]);
  if (!authorization) return <Login onLogin={(newAuthorization, login) => { setAuthorization(newAuthorization); setUser(login); }} />;

  async function addPerson(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await api('/api/people', { method: 'POST', body: JSON.stringify({ fullName: form.get('fullName'), phone: form.get('phone') || null, note: null }) });
      event.currentTarget.reset();
      await load();
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function changePassword(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const newPassword = form.get('newPassword');
    try {
      await api('/api/account/password', { method: 'PUT', body: JSON.stringify({ currentPassword: form.get('currentPassword'), newPassword }) });
      setAuthorization(basicAuth(user, newPassword));
      setMessage('Пароль обновлён. Он не сохраняется в браузере.');
      event.currentTarget.reset();
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  const tabs = {
    overview: <Overview people={people} subscriptions={subscriptions} rates={rates} />,
    people: <People people={people} onAdd={addPerson} />,
    subscriptions: <Subscriptions subscriptions={subscriptions} />,
    rates: <Rates rates={rates} />,
    password: <PasswordForm onSubmit={changePassword} />
  };

  return (
    <main className="shell">
      <header className="topbar"><div><h1>Kayak Bookkeeper</h1><span className="muted">Управление клубом</span></div><div><span className="muted">{user} · </span><button onClick={() => setAuthorization(null)}>Выйти</button></div></header>
      <nav className="tabs">{[['overview', 'Обзор'], ['people', 'Каякеры'], ['subscriptions', 'Абонементы'], ['rates', 'Тарифы'], ['password', 'Пароль']].map(([id, title]) => <button key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{title}</button>)}</nav>
      <div className="spacer" />
      {error && <p className="notice">{error}</p>}
      {message && <p className="notice success">{message}</p>}
      {loading ? <p className="muted">Загрузка…</p> : tabs[tab]}
    </main>
  );
}
