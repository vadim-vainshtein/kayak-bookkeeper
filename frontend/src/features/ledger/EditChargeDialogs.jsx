import { useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { formatDate, money } from '../../shared/format.js';
import ChargeEditForm from './ChargeEditForm.jsx';

export default function EditChargeDialogs({
    editingCharge,
    personId,
    subscriptions,
    saveCharge,
    submitting,
    onClose,
}) {
    const [confirming, setConfirming] = useState(false);
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
    return (
        <>
            <Modal open={!confirming} onClose={cancelEdit} title="Изменить начисление">
                {editingCharge && editDraft && (
                    <ChargeEditForm
                        charge={editingCharge}
                        draft={editDraft}
                        setDraft={setEditDraft}
                        subscriptions={subscriptions}
                        onConfirm={() => setConfirming(true)}
                    />
                )}
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
