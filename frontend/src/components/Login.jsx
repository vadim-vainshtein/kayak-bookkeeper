import { useState } from 'react';
import { basicAuth } from '../api';

export default function Login({ onLogin }) {
  const [login, setLogin] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    const authorization = basicAuth(login, password);
    const response = await fetch('/api/people', { headers: { Authorization: authorization } });
    if (!response.ok) {
      setError('Неверный логин или пароль.');
      return;
    }
    onLogin(authorization, login);
  }

  return (
    <main className="shell login">
      <section className="card">
        <h1>Kayak Bookkeeper</h1>
        <p className="muted">Войдите, чтобы управлять клубом.</p>
        {error && <p className="notice">{error}</p>}
        <form onSubmit={submit}>
          <label>Логин<input value={login} onChange={(event) => setLogin(event.target.value)} autoComplete="username" required /></label>
          <label>Пароль<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
          <button className="primary" type="submit">Войти</button>
        </form>
      </section>
    </main>
  );
}
