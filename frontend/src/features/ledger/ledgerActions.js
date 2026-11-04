export function createLedgerActions({ api, run, submit, loadBalance, refreshBalances, personId }) {
    const addCharge = (event, id) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api('/api/charges', {
                    method: 'POST',
                    body: JSON.stringify({
                        personId: id,
                        amount: Number(form.get('amount')),
                        chargedAt: form.get('chargedAt'),
                        description: form.get('description'),
                        subscriptionId: form.get('subscriptionId')
                            ? Number(form.get('subscriptionId'))
                            : null,
                    }),
                });
                await Promise.all([loadBalance(id), refreshBalances()]);
            },
            'Начисление добавлено.',
        );
    };
    const saveCharge = (charge, id, values) =>
        run(async () => {
            await api(`/api/charges/${charge.id}`, { method: 'PUT', body: JSON.stringify(values) });
            await Promise.all([loadBalance(id), refreshBalances()]);
        }, 'Начисление обновлено.');
    const deleteCharge = (charge, id, onError) =>
        run(async () => {
            try {
                await api(`/api/charges/${charge.id}`, { method: 'DELETE' });
                await Promise.all([loadBalance(id, { keepCurrent: true }), refreshBalances()]);
            } catch (error) {
                onError?.(error.message);
                throw error;
            }
        }, 'Начисление удалено.');
    const addPayment = (event, id) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api('/api/payments/with-allocation', {
                    method: 'POST',
                    body: JSON.stringify({
                        personId: id,
                        amount: Number(form.get('amount')),
                        paidAt: form.get('paidAt'),
                        method: form.get('method'),
                        note: form.get('note') || null,
                        chargeId: form.get('chargeId') ? Number(form.get('chargeId')) : null,
                        allocationAmount: form.get('allocationAmount')
                            ? Number(form.get('allocationAmount'))
                            : null,
                    }),
                });
                await Promise.all([loadBalance(id), refreshBalances()]);
            },
            'Оплата зарегистрирована.',
        );
    };
    const allocatePayment = (paymentId, chargeId, amount) =>
        run(async () => {
            await api(`/api/payments/${paymentId}/allocations`, {
                method: 'POST',
                body: JSON.stringify({ chargeId, amount }),
            });
            await Promise.all([loadBalance(personId), refreshBalances()]);
        }, 'Оплата распределена.');
    const mutatePayment = (request, success, onError) =>
        run(async () => {
            try {
                await request();
                await Promise.all([loadBalance(personId, { keepCurrent: true }), refreshBalances()]);
            } catch (error) {
                onError?.(error.message);
                throw error;
            }
        }, success);
    const savePayment = (paymentId, values, onError) =>
        mutatePayment(
            () => api(`/api/payments/${paymentId}`, { method: 'PATCH', body: JSON.stringify(values) }),
            'Оплата обновлена.', onError,
        );
    const cancelAllocation = (paymentId, allocationId, onError) =>
        mutatePayment(
            () => api(`/api/payments/${paymentId}/allocations/${allocationId}`, { method: 'DELETE' }),
            'Распределение оплаты отменено.', onError,
        );
    return { addCharge, saveCharge, deleteCharge, addPayment, allocatePayment, savePayment, cancelAllocation };
}
