import { useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { formatDate, money, today } from '../../shared/format.js';
import MemberPicker from './MemberPicker.jsx';
import { createEditDraft, prepareSubscriptionEdit } from './subscriptionDraft.js';

export default function EditSubscriptionDialog({ selected, people, update, preview, submitting, onClose }) {
    const [editDraft, setEditDraft] = useState(() => createEditDraft(selected));
    const [editError, setEditError] = useState('');
    const changeSession = (index, field, value) =>
        setEditDraft((draft) => ({
            ...draft,
            sessions: draft.sessions.map((session, current) =>
                current === index ? { ...session, [field]: value } : session,
            ),
        }));
    async function saveEdit(event) {
        event.preventDefault();
        const { values, error } = prepareSubscriptionEdit(selected, editDraft);
        if (error) {
            setEditError(error);
            return;
        }
        if (selected.status === 'FINALIZED') {
            const impact = await preview(selected, values);
            if (!impact) return;
            if (
                impact.requiresConfirmation &&
                !window.confirm(
                    `Это изменение затронет ${impact.allocatedPayments.length} распределённ${impact.allocatedPayments.length === 1 ? 'ую оплату' : 'ые оплаты'}:\n${impact.allocatedPayments.map((payment) => `• ${formatDate(payment.paidAt)} — ${money(payment.allocatedAmount)} (${payment.chargeDescription || 'начисление'})`).join('\n')}\n\nПродолжить?`,
                )
            )
                return;
            values.confirmAllocatedPayments = impact.requiresConfirmation;
        }
        if (await update(selected, values)) onClose();
    }
    return (
        <Modal open onClose={onClose} title="Редактировать абонемент">
            <form onSubmit={saveEdit}>
                <label>
                    Название
                    <input
                        maxLength="255"
                        value={editDraft.title}
                        onChange={(event) =>
                            setEditDraft({
                                ...editDraft,
                                title: event.target.value,
                            })
                        }
                    />
                </label>
                <label>
                    Описание
                    <textarea
                        rows="3"
                        maxLength="1000"
                        value={editDraft.description}
                        onChange={(event) =>
                            setEditDraft({
                                ...editDraft,
                                description: event.target.value,
                            })
                        }
                    />
                </label>
                {selected.status !== 'COMPLETED' && (
                    <>
                        <label>
                            Вместимость
                            <input
                                type="number"
                                min="1"
                                max="8"
                                required
                                value={editDraft.capacity}
                                onChange={(event) => {
                                    setEditDraft({
                                        ...editDraft,
                                        capacity: event.target.value,
                                    });
                                    setEditError('');
                                }}
                            />
                        </label>
                        <fieldset className="schedule-editor">
                            <legend>Расписание</legend>
                            {editDraft.sessions.map((session, index) => (
                                <div
                                    className="schedule-row"
                                    key={`${index}-${session.date}-${session.time}`}
                                >
                                    <label>
                                        Дата
                                        <input
                                            type="date"
                                            value={session.date}
                                            onChange={(event) =>
                                                changeSession(index, 'date', event.target.value)
                                            }
                                            required
                                        />
                                    </label>
                                    <label>
                                        Время
                                        <input
                                            type="time"
                                            value={session.time}
                                            onChange={(event) =>
                                                changeSession(index, 'time', event.target.value)
                                            }
                                            required
                                        />
                                    </label>
                                    <button
                                        type="button"
                                        disabled={editDraft.sessions.length === 1}
                                        onClick={() =>
                                            setEditDraft({
                                                ...editDraft,
                                                sessions: editDraft.sessions.filter(
                                                    (_, current) => current !== index,
                                                ),
                                            })
                                        }
                                    >
                                        Удалить
                                    </button>
                                </div>
                            ))}
                            <button
                                type="button"
                                onClick={() =>
                                    setEditDraft({
                                        ...editDraft,
                                        sessions: [...editDraft.sessions, { date: today(), time: '19:00' }],
                                    })
                                }
                            >
                                Добавить тренировку
                            </button>
                        </fieldset>
                        <MemberPicker
                            people={people}
                            members={editDraft.members}
                            retainInactive
                            setMembers={(next) => setEditDraft({ ...editDraft, members: next })}
                        />
                        {editError && (
                            <small className="validation-message" role="alert">
                                {editError}
                            </small>
                        )}
                    </>
                )}
                <div className="dialog-actions">
                    <button className="primary" disabled={submitting}>
                        Сохранить
                    </button>
                </div>
            </form>
        </Modal>
    );
}
