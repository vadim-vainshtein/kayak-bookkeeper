import { useLayoutEffect, useState } from 'react';
import Modal from '../../components/Modal.jsx';

export default function PasswordSettings({ onSubmit, submitting }) {
    const [open, setOpen] = useState(false);
    useLayoutEffect(() => {
        const dialog = [...document.querySelectorAll('.modal-dialog')].find(
            (item) => item.querySelector('h2')?.textContent === 'Сменить пароль',
        );
        dialog?.querySelector('form')?.reset();
    }, [open]);
    return (
        <section className="card">
            <h2>Пароль</h2>
            <p className="muted">Пароль не хранится в браузере.</p>
            <button className="primary" onClick={() => setOpen(true)}>
                Сменить пароль
            </button>
            <Modal open={open} onClose={() => setOpen(false)} title="Сменить пароль">
                <form
                    onSubmit={async (event) => {
                        const ok = await onSubmit(event);
                        if (ok) setOpen(false);
                    }}
                >
                    <label>
                        Текущий пароль
                        <input
                            name="currentPassword"
                            type="password"
                            autoComplete="current-password"
                            required
                        />
                    </label>
                    <label>
                        Новый пароль
                        <input
                            name="newPassword"
                            type="password"
                            minLength="8"
                            maxLength="72"
                            autoComplete="new-password"
                            required
                        />
                    </label>
                    <div className="dialog-actions">
                        <button className="primary" disabled={submitting}>
                            Обновить пароль
                        </button>
                    </div>
                </form>
            </Modal>
        </section>
    );
}
