import { useLayoutEffect, useRef, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import AddChargeDialog from './AddChargeDialog.jsx';
import AddPaymentDialog from './AddPaymentDialog.jsx';
import EditChargeDialogs from './EditChargeDialogs.jsx';
import EditPaymentDialog from './EditPaymentDialog.jsx';
import AllocationForm from './AllocationForm.jsx';
import LedgerDetails from './LedgerDetails.jsx';
import PersonBalanceTable from './PersonBalanceTable.jsx';
import { paymentRemaining } from './allocation.js';
import DetailPage from '../../components/DetailPage.jsx';

export default function Ledger(props) {
    const { personId, personBalances, subscriptions, selectPerson } = props;
    const tablePanel = useRef(null);
    const previousPerson = useRef(null);
    useLayoutEffect(() => {
        if (!personId && previousPerson.current) {
            const buttons = tablePanel.current?.querySelectorAll(`[data-balance-person="${previousPerson.current}"]`);
            Array.from(buttons || []).find((button) => button.getClientRects().length)?.focus();
        }
        previousPerson.current = personId;
    }, [personId]);
    return <>
        <div ref={tablePanel} hidden={Boolean(personId)}><PersonBalanceTable personBalances={personBalances} subscriptions={subscriptions} selectPerson={selectPerson} /></div>
        {personId && <PersonLedger key={personId} {...props} />}
    </>;
}

function PersonLedger({
    readOnly = false,
    personBalances,
    subscriptions,
    personId,
    balance,
    submitting,
    showPeople,
    addCharge,
    saveCharge,
    deleteCharge,
    addPayment,
    allocatePayment,
    savePayment,
    cancelAllocation,
}) {
    const [chargeDialog, setChargeDialog] = useState(false);
    const [paymentDialog, setPaymentDialog] = useState(false);
    const [allocationPayment, setAllocationPayment] = useState(null);
    const [editingCharge, setEditingCharge] = useState(null);
    const [editingPaymentId, setEditingPaymentId] = useState(null);
    const person = personBalances.find((entry) => entry.personId === personId);
    const charges = balance?.charges || [];
    const payments = balance?.payments || [];
    const editingPayment = payments.find((payment) => payment.id === editingPaymentId);
    const outstanding = charges.filter((charge) => charge.amount > charge.allocated);
    return (
        <>
            <DetailPage
                onBack={showPeople}
                backLabel="Все каякеры и балансы"
                busy={submitting}
                title={person ? `Начисления и оплаты · ${person.fullName}` : 'Начисления и оплаты'}
            >
                {!balance ? (
                    <p className="muted" role="status">
                        Загрузка баланса…
                    </p>
                ) : (
                    <div className="ledger">
                        <LedgerDetails
                            key={personId}
                            readOnly={readOnly}
                            balance={balance}
                            subscriptions={subscriptions}
                            charges={charges}
                            payments={payments}
                            setChargeDialog={setChargeDialog}
                            setPaymentDialog={setPaymentDialog}
                            openEdit={setEditingCharge}
                            openPaymentEdit={setEditingPaymentId}
                            setAllocationPayment={setAllocationPayment}
                        />
                        {!readOnly && <><AddChargeDialog
                            open={chargeDialog}
                            subscriptions={subscriptions}
                            personId={personId}
                            addCharge={addCharge}
                            submitting={submitting}
                            onClose={() => setChargeDialog(false)}
                        />
                        <AddPaymentDialog
                            open={paymentDialog}
                            outstanding={outstanding}
                            subscriptions={subscriptions}
                            personId={personId}
                            addPayment={addPayment}
                            submitting={submitting}
                            onClose={() => setPaymentDialog(false)}
                        />
                        {allocationPayment && (
                            <Modal
                                open
                                onClose={() => setAllocationPayment(null)}
                                title="Распределить полученную оплату"
                            >
                                <AllocationForm
                                    payment={allocationPayment}
                                    outstanding={outstanding}
                                    subscriptions={subscriptions}
                                    paymentRemaining={paymentRemaining}
                                    allocate={allocatePayment}
                                    close={() => setAllocationPayment(null)}
                                    submitting={submitting}
                                />
                            </Modal>
                        )}
                        {editingCharge && (
                            <EditChargeDialogs
                                editingCharge={editingCharge}
                                personId={personId}
                                subscriptions={subscriptions}
                                saveCharge={saveCharge}
                                deleteCharge={deleteCharge}
                                submitting={submitting}
                                onClose={() => setEditingCharge(null)}
                            />
                        )}
                        {editingPayment && (
                            <EditPaymentDialog
                                key={editingPayment.id}
                                payment={editingPayment}
                                charges={charges}
                                subscriptions={subscriptions}
                                savePayment={savePayment}
                                cancelAllocation={cancelAllocation}
                                submitting={submitting}
                                onClose={() => setEditingPaymentId(null)}
                            />
                        )}</>}
                    </div>
                )}
            </DetailPage>
        </>
    );
}
