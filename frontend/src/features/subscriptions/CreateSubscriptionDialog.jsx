import { useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { today } from '../../shared/format.js';
import { days } from '../../shared/labels.js';
import MemberPicker from './MemberPicker.jsx';
import { memberRequests } from './subscriptionDraft.js';

export default function CreateSubscriptionDialog({ open, people, create, submitting, onClose }) {
    const [members, setMembers] = useState({});
    const [editError, setEditError] = useState('');
    async function createSubmit(event) {
        event.preventDefault();
        const capacity = Number(new FormData(event.currentTarget).get('capacity'));
        const selectedMembers = memberRequests(members);
        if (!selectedMembers.length || selectedMembers.length > capacity) {
            setEditError('Выберите участников в пределах вместимости.');
            return;
        }
        if (await create(event, members)) {
            setMembers({});
            onClose();
        }
    }
    return (
        <Modal open={open} onClose={onClose} title="Новый абонемент">
            <form onSubmit={createSubmit}>
                <label>
                    Название
                    <input name="title" maxLength="255" />
                </label>
                <label>
                    Описание
                    <textarea name="description" rows="2" maxLength="1000" />
                </label>
                <div className="form-columns">
                    <label>
                        День недели
                        <select name="dayOfWeek" defaultValue="TUESDAY">
                            {days.map(([value, label]) => (
                                <option key={value} value={value}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Время
                        <input name="startTime" type="time" defaultValue="19:00" required />
                    </label>
                </div>
                <div className="form-columns">
                    <label>
                        Первая дата
                        <input name="startDate" type="date" defaultValue={today()} required />
                    </label>
                    <label>
                        Тренировок
                        <input name="sessionCount" type="number" min="1" max="52" defaultValue="4" required />
                    </label>
                </div>
                <label>
                    Вместимость
                    <input name="capacity" type="number" min="1" max="8" defaultValue="8" required />
                </label>
                <MemberPicker people={people} members={members} setMembers={setMembers} />
                <div className="dialog-actions">
                    <button className="primary" disabled={submitting}>
                        Создать
                    </button>
                </div>
                {editError && (
                    <small className="validation-message" role="alert">
                        {editError}
                    </small>
                )}
            </form>
        </Modal>
    );
}
