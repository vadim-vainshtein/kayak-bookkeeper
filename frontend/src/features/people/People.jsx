import { useState } from 'react';
import Modal from '../../components/Modal.jsx';

export default function People({ people, onAdd, onUpdate, submitting, readOnly = false }) {
    const [adding, setAdding] = useState(false);
    const [editing, setEditing] = useState(null);
    const [query, setQuery] = useState('');
    const [activeOnly, setActiveOnly] = useState(false);
    const visible = people.filter(
        (person) =>
            (!activeOnly || person.active) &&
            `${person.fullName} ${person.phone || ''}`.toLowerCase().includes(query.toLowerCase()),
    );
    const form = (person, submit) => (
        <form key={person?.id || 'new'} onSubmit={submit}>
            <label>
                Имя <span className="required">*</span>
                <input
                    name="fullName"
                    required
                    defaultValue={person?.fullName || ''}
                    placeholder="Имя и фамилия"
                />
            </label>
            <label>
                Телефон
                <input
                    name="phone"
                    type="tel"
                    defaultValue={person?.phone || ''}
                    placeholder="+7 700 000 00 00"
                />
            </label>
            {person && (
                <>
                    <label>
                        Заметка
                        <textarea name="note" rows="3" defaultValue={person.note || ''} />
                    </label>
                    <label className="checkbox">
                        <input type="checkbox" name="active" defaultChecked={person.active} />
                        Активен
                    </label>
                </>
            )}
            <div className="dialog-actions">
                <button className="primary" disabled={submitting}>
                    {person ? 'Сохранить' : 'Добавить'}
                </button>
            </div>
        </form>
    );
    return (
        <section className="card">
            <div className="line">
                <h2>Каякеры</h2>
                {!readOnly && <button className="primary" onClick={() => setAdding(true)}>
                    Добавить каякера
                </button>}
            </div>
            <label>
                Поиск
                <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Имя или телефон"
                />
            </label>
            <label className="checkbox">
                <input
                    type="checkbox"
                    checked={activeOnly}
                    onChange={(event) => setActiveOnly(event.target.checked)}
                />
                Только активные
            </label>
            {visible.length ? (
                <ul className="list">
                    {visible.map((person) => (
                        <li key={person.id}>
                            <button className="person" onClick={() => setEditing(person)}>
                                <div className="line">
                                    <strong>{person.fullName}</strong>
                                    <span className="muted">{person.active ? 'активен' : 'неактивен'}</span>
                                </div>
                                <small className="muted">{person.phone || 'Телефон не указан'}</small>
                            </button>
                        </li>
                    ))}
                </ul>
            ) : (
                <p className="muted">Никого не найдено.</p>
            )}
            {!readOnly && <Modal open={adding} onClose={() => setAdding(false)} title="Новый каякер">
                {form(null, async (event) => {
                    const ok = await onAdd(event);
                    if (ok) setAdding(false);
                })}
            </Modal>}
            <Modal open={Boolean(editing)} onClose={() => setEditing(null)} title={readOnly ? "Каякер" : "Редактировать каякера"}>
                {readOnly && editing && <>
                    <h3>{editing.fullName}</h3>
                    <p>{editing.phone || 'Телефон не указан'}</p>
                    <p>{editing.active ? 'Активен' : 'Неактивен'}</p>
                    <p>{editing.note || 'Заметок нет'}</p>
                </>}
                {!readOnly && editing &&
                    form(editing, async (event) => {
                        const ok = await onUpdate(event, editing);
                        if (ok) setEditing(null);
                    })}
            </Modal>
        </section>
    );
}
