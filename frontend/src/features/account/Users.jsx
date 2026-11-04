import { useEffect, useState } from 'react';

const roleLabels = { READ_ONLY: 'Член клуба · только чтение', ADMIN: 'Администратор' };

export default function Users({ api }) {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [login, setLogin] = useState('');
    const [password, setPassword] = useState('');
    const [role, setRole] = useState('READ_ONLY');

    useEffect(() => {
        let active = true;
        setLoading(true);
        api('/api/users').then((result) => {
            if (active) setUsers(result);
        }).catch((failure) => {
            if (active) setError(failure.message);
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [api]);

    async function createUser(event) {
        event.preventDefault();
        if (submitting || loading) return;
        setError('');
        setMessage('');
        if (new TextEncoder().encode(password).length > 72) {
            setError('Пароль не должен превышать 72 байта UTF-8.');
            return;
        }
        setSubmitting(true);
        try {
            const created = await api('/api/users', { method: 'POST', body: JSON.stringify({ login, password, role }) });
            setUsers((current) => [...current, created].sort((a, b) => a.login.localeCompare(b.login)));
            setLogin('');
            setPassword('');
            setRole('READ_ONLY');
            setMessage(`Учётная запись «${created.login}» создана.`);
        } catch (failure) {
            setError(failure.message);
        } finally {
            setSubmitting(false);
        }
    }

    return <div className="grid">
        <section className="card">
            <h2>Новая учётная запись</h2>
            <p className="muted">Член клуба видит все данные, включая финансовые отчёты. Изменения доступны только администратору.</p>
            {error && <p className="notice" role="alert">{error}</p>}
            {message && <p className="notice success" role="status">{message}</p>}
            <form onSubmit={createUser}>
                <label>Логин
                    <input name="login" value={login} onChange={(event) => setLogin(event.target.value)} autoComplete="off" maxLength={100} pattern="[^\s:]+" required disabled={submitting} />
                </label>
                <small className="muted">Без пробелов и двоеточий.</small>
                <label>Пароль
                    <input name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required disabled={submitting} />
                </label>
                <small className="muted">От 8 символов, не более 72 байт UTF-8. Сохраните пароль перед созданием: он не отображается в списке.</small>
                <label>Роль
                    <select name="role" value={role} onChange={(event) => setRole(event.target.value)} disabled={submitting}>
                        {Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                </label>
                <button className="primary" disabled={submitting || loading}>Создать учётную запись</button>
            </form>
        </section>
        <section className="card">
            <h2>Пользователи</h2>
            {loading ? <p role="status">Загрузка…</p> : <ul className="list">
                {users.map((user) => <li key={user.id}><strong>{user.login}</strong><p className="muted">{roleLabels[user.role]}</p></li>)}
            </ul>}
        </section>
    </div>;
}
