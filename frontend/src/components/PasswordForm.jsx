export default function PasswordForm({ onSubmit }) {
  return (
    <section className="card">
      <h2>Сменить пароль</h2>
      <p className="muted">Новый пароль должен содержать от 8 до 72 символов.</p>
      <form onSubmit={onSubmit}>
        <label>Текущий пароль<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
        <label>Новый пароль<input name="newPassword" type="password" minLength="8" maxLength="72" autoComplete="new-password" required /></label>
        <button className="primary" type="submit">Обновить пароль</button>
      </form>
    </section>
  );
}
