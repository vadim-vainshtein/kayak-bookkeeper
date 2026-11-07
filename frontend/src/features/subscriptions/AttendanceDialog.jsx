import { useEffect, useRef, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import DetailPage from '../../components/DetailPage.jsx';
import { useNavigationLock } from '../../components/NavigationLock.jsx';
import { formatDate, money } from '../../shared/format.js';

function AttendanceSurface({ page, onClose, saving, title, children }) {
    return page ? <DetailPage className="attendance-page" title={title} backLabel="К абонементу" onBack={onClose} busy={saving}>{children}</DetailPage>
        : <Modal open title={title} onClose={onClose} closeDisabled={saving}>{children}</Modal>;
}

export default function AttendanceDialog({ session, people, api, readOnly = false, onSaved, onClose, page = false }) {
    const [roster, setRoster] = useState(null);
    const [members, setMembers] = useState({});
    const [guests, setGuests] = useState([]);
    const [personId, setPersonId] = useState('');
    const [newName, setNewName] = useState('');
    const [newPhone, setNewPhone] = useState('');
    const [search, setSearch] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    useNavigationLock(page && saving);
    const pending = useRef(false);
    const nextKey = useRef(0);

    useEffect(() => {
        let active = true;
        api(`/api/sessions/${session.id}/attendance`).then((data) => {
            if (!active) return;
            setRoster(data);
            setMembers(Object.fromEntries(data.people.filter((person) => !person.guest).map((person) => [person.personId, person.present])));
            setGuests(data.people.filter((person) => person.guest).map((person) => ({
                key: `person-${person.personId}`, personId: person.personId, personName: person.personName,
                amount: String(person.amount), allocated: person.allocated,
            })));
        }).catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [api, session.id]);

    const disabled = readOnly || saving || !roster?.available;
    const memberRows = roster?.people.filter((person) => !person.guest) || [];
    const unavailableIds = new Set([...memberRows.map((person) => person.personId), ...guests.map((person) => person.personId)]);
    const candidates = people.filter((person) => person.active && !unavailableIds.has(person.id)
        && `${person.fullName} ${person.phone || ''}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
    const presentCount = Object.values(members).filter(Boolean).length + guests.length;

    function addExisting() {
        const person = candidates.find((item) => item.id === Number(personId));
        if (!person) return;
        setGuests([...guests, { key: `person-${person.id}`, personId: person.id, personName: person.fullName,
            amount: roster.dropInAmount == null ? '' : String(roster.dropInAmount), allocated: 0 }]);
        setPersonId('');
        setSearch('');
        setError('');
    }

    function addNew() {
        if (!newName.trim()) { setError('Укажите имя нового гостя.'); return; }
        setGuests([...guests, { key: `new-${nextKey.current++}`, fullName: newName.trim(), phone: newPhone.trim() || null,
            personName: newName.trim(), amount: roster.dropInAmount == null ? '' : String(roster.dropInAmount), allocated: 0 }]);
        setNewName('');
        setNewPhone('');
        setError('');
    }

    async function save(event) {
        event.preventDefault();
        if (disabled || pending.current) return;
        setError('');
        for (const guest of guests) {
            const amount = Number(guest.amount);
            if (guest.amount.trim() === '' || !Number.isSafeInteger(amount) || amount < guest.allocated || amount < 0) {
                setError(`Укажите целую сумму для гостя «${guest.personName}» не меньше ${money(guest.allocated)}.`);
                return;
            }
        }
        pending.current = true;
        setSaving(true);
        try {
            await api(`/api/sessions/${session.id}/attendance`, {
                method: 'PUT',
                body: JSON.stringify({
                    revision: roster.revision,
                    memberIds: Object.entries(members).filter(([, present]) => present).map(([id]) => Number(id)),
                    guests: guests.map((guest) => ({
                        ...(guest.personId ? { personId: guest.personId } : { fullName: guest.fullName, phone: guest.phone }),
                        amount: Number(guest.amount),
                    })),
                }),
            });
            await onSaved();
            onClose();
        } catch (failure) {
            setError(failure.message || 'Не удалось сохранить посещения.');
        } finally {
            pending.current = false;
            setSaving(false);
        }
    }

    return (
        <AttendanceSurface page={page} title={readOnly ? 'Посещения тренировки' : 'Отметить посещения'} onClose={onClose} saving={saving}>
            <p className="attendance-date">{formatDate(session.startsAt)}</p>
            {error && <p className="validation-message" role="alert">{error}</p>}
            {loading && <p role="status">Загрузка посещений…</p>}
            {!loading && !roster && <p className="muted">Закройте окно и откройте его снова для повторной загрузки.</p>}
            {roster && <form className="attendance-form" onSubmit={save}>
                <p className="muted">По умолчанию никто не пришёл. Отметьте только присутствующих. Начисления по абонементу от этих отметок не меняются.</p>
                {!roster.available && <p role="status">Отметки доступны с начала тренировки рассчитанного абонемента.</p>}
                <fieldset className="member-picker" disabled={disabled}>
                    <legend>Участники абонемента</legend>
                    {memberRows.length ? memberRows.map((person) => (
                        <label className="checkbox member-option" key={person.personId}>
                            <input type="checkbox" checked={Boolean(members[person.personId])}
                                onChange={(event) => setMembers({ ...members, [person.personId]: event.target.checked })} />
                            {person.personName}
                        </label>
                    )) : <p className="muted">Участников абонемента нет.</p>}
                </fieldset>
                <h3>Гости · {guests.length}</h3>
                <p className="muted">{roster.dropInAmount == null
                    ? 'Действующего тарифа разовой тренировки нет. Укажите сумму каждого гостя вручную или добавьте тариф в разделе «Тарифы».'
                    : `Текущий тариф на ${formatDate(roster.rateDate)}: ${money(roster.dropInAmount)}. Новому гостю подставится эта сумма.`} Суммы можно изменить до сохранения. 0 — бесплатное посещение.</p>
                <ul className="list attendance-guests">
                    {guests.map((guest) => (
                        <li key={guest.key}>
                            <div className="line"><strong>{guest.personName}</strong>
                                {!readOnly && <button type="button" disabled={disabled || guest.allocated > 0}
                                    aria-label={`Убрать гостя ${guest.personName}`}
                                    onClick={() => setGuests(guests.filter((person) => person.key !== guest.key))}>Убрать</button>}
                            </div>
                            <label>Начисление, ₸ · {guest.personName}
                                <input type="number" min={guest.allocated} step="1" required value={guest.amount} disabled={disabled}
                                    onChange={(event) => setGuests(guests.map((person) => person.key === guest.key ? { ...person, amount: event.target.value } : person))} />
                            </label>
                            {guest.allocated > 0 && <small className="muted">Распределено {money(guest.allocated)}. Чтобы убрать гостя или уменьшить сумму ниже оплаты, сначала отмените распределение в разделе оплат.</small>}
                            {guest.fullName && <small className="muted">Новый каякер будет создан при сохранении посещений.</small>}
                        </li>
                    ))}
                </ul>
                {!readOnly && <fieldset className="member-picker" disabled={disabled}>
                    <legend>Добавить гостя</legend>
                    <label>Поиск по имени или телефону
                        <input value={search} onChange={(event) => { setSearch(event.target.value); setPersonId(''); }} />
                    </label>
                    <div className="form-columns">
                        <label>Человек из справочника
                            <select value={personId} onChange={(event) => setPersonId(event.target.value)}>
                                <option value="">Выберите человека</option>
                                {candidates.map((person) => <option key={person.id} value={person.id}>{person.fullName}{person.phone ? ` · ${person.phone}` : ''}</option>)}
                            </select>
                        </label>
                        <button type="button" disabled={disabled || !personId} onClick={addExisting}>Добавить выбранного гостя</button>
                    </div>
                    <details>
                        <summary>Новый человек, которого нет в справочнике</summary>
                        <label>Имя нового гостя<input maxLength="255" value={newName} onChange={(event) => setNewName(event.target.value)} /></label>
                        <label>Телефон нового гостя<input maxLength="255" value={newPhone} onChange={(event) => setNewPhone(event.target.value)} /></label>
                        <button type="button" disabled={disabled || !newName.trim()} onClick={addNew}>Добавить нового гостя</button>
                    </details>
                </fieldset>}
                <p role="status" className="attendance-total">Всего пришли: <strong>{presentCount}</strong>. Количество гостей не ограничено вместимостью группы.</p>
                {!readOnly && <div className="dialog-actions"><button type="button" onClick={onClose} disabled={saving}>Отмена</button>
                    <button className="primary" disabled={disabled}>{saving ? 'Сохранение…' : 'Сохранить посещения'}</button>
                </div>}
            </form>}
        </AttendanceSurface>
    );
}
