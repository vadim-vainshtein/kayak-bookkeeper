import { useRef, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { formatDate, money } from '../../shared/format.js';
import ChargeEditForm from './ChargeEditForm.jsx';
import { chargeTitle } from './chargeTitle.js';

export default function EditChargeDialogs({
    editingCharge,
    personId,
    subscriptions,
    saveCharge,
    deleteCharge,
    submitting,
    onClose,
}) {
    const [confirming, setConfirming] = useState(false);
    const [confirmingDelete, setConfirmingDelete] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState('');
    const deletionPending = useRef(false);
    const busy = submitting || deleting;
    const [editDraft, setEditDraft] = useState(() => ({
        amount: editingCharge.amount,
        chargedAt: editingCharge.chargedAt,
        description: editingCharge.description,
        subscriptionId: editingCharge.subscriptionId,
        updateSubscription: true,
    }));
    const cancelEdit = onClose;
    async function confirmEdit() {
        if (await saveCharge(editingCharge, personId, editDraft)) onClose();
    }
    async function confirmDelete() {
        if (busy || deletionPending.current || editingCharge.allocated > 0) return;
        deletionPending.current = true;
        setDeleting(true);
        setDeleteError('');
        let failure = 'Не удалось удалить начисление. Попробуйте ещё раз.';
        try {
            const ok = await deleteCharge(editingCharge, personId, (message) => { failure = message; });
            if (ok) onClose();
            else setDeleteError(failure);
        } catch (error) {
            setDeleteError(error.message || failure);
        } finally {
            deletionPending.current = false;
            setDeleting(false);
        }
    }
    return (
        <>
            <Modal open={!confirming && !confirmingDelete} onClose={cancelEdit} title="Изменить начисление">
                {editingCharge && editDraft && (
                    <ChargeEditForm
                        charge={editingCharge}
                        draft={editDraft}
                        setDraft={setEditDraft}
                        subscriptions={subscriptions}
                        onConfirm={() => setConfirming(true)}
                    />
                )}
                <div className="dialog-actions">
                    <button type="button" disabled={busy} onClick={() => { setDeleteError(''); setConfirmingDelete(true); }}>
                        Удалить начисление
                    </button>
                </div>
            </Modal>
            <Modal
                open={confirmingDelete}
                closeDisabled={busy}
                onClose={() => setConfirmingDelete(false)}
                title="Подтвердить удаление начисления"
            >
                <p>Удалить «{chargeTitle(editingCharge, subscriptions)}» на сумму {money(editingCharge.amount)} от {formatDate(editingCharge.chargedAt)}? Это действие нельзя отменить.</p>
                <p className="muted">Оплаты сохранятся. Баланс каякера будет пересчитан.</p>
                {editingCharge.allocated > 0 && (
                    <p role="alert">На начисление распределено {money(editingCharge.allocated)}. Сначала отмените распределение в окне редактирования оплаты.</p>
                )}
                {deleteError && <p className="validation-message" role="alert">{deleteError}</p>}
                <div className="dialog-actions">
                    <button type="button" disabled={busy} onClick={() => setConfirmingDelete(false)}>Отмена</button>
                    <button type="button" className="primary" disabled={busy || editingCharge.allocated > 0} onClick={confirmDelete}>
                        Подтвердить удаление
                    </button>
                </div>
            </Modal>
            <Modal
                open={confirming}
                onClose={() => setConfirming(false)}
                title="Подтвердить изменение начисления"
            >
                {editDraft && (
                    <>
                        <p>Это нестандартная ручная операция. Проверьте значения перед сохранением.</p>
                        <dl className="confirmation-summary">
                            <div>
                                <dt>Сумма</dt>
                                <dd>{money(editDraft.amount)}</dd>
                            </div>
                            <div>
                                <dt>Дата</dt>
                                <dd>{formatDate(editDraft.chargedAt)}</dd>
                            </div>
                            <div>
                                <dt>Описание</dt>
                                <dd>{editDraft.description}</dd>
                            </div>
                            <div>
                                <dt>Абонемент</dt>
                                <dd>
                                    {subscriptions.find(
                                        (subscription) => subscription.id === editDraft.subscriptionId,
                                    )?.title ||
                                        (editDraft.subscriptionId
                                            ? `Абонемент #${editDraft.subscriptionId}`
                                            : 'Без привязки')}
                                </dd>
                            </div>
                        </dl>
                        <div className="dialog-actions">
                            <button onClick={() => setConfirming(false)} disabled={submitting}>
                                Отмена
                            </button>
                            <button className="primary" onClick={confirmEdit} disabled={submitting}>
                                Подтвердить изменение
                            </button>
                        </div>
                    </>
                )}
            </Modal>
        </>
    );
}
