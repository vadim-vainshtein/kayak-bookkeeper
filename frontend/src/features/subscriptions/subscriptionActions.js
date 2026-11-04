export function createSubscriptionActions({ api, submit, load, run, setError }) {
    const createSubscription = (event, members) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api('/api/subscriptions/recurring-with-members', {
                    method: 'POST',
                    body: JSON.stringify({
                        dayOfWeek: form.get('dayOfWeek'),
                        startTime: form.get('startTime'),
                        startDate: form.get('startDate'),
                        sessionCount: Number(form.get('sessionCount')),
                        capacity: Number(form.get('capacity')),
                        title: form.get('title') || null,
                        description: form.get('description') || null,
                        members: Object.entries(members).map(([id, equipment]) => ({
                            personId: Number(id),
                            usesSkirt: equipment.skirt,
                            usesPaddle: equipment.paddle,
                            usesKayak: equipment.kayak,
                        })),
                    }),
                });
                await load();
            },
            'Абонемент создан.',
        );
    };
    const previewSubscriptionUpdate = async (subscription, values) => {
        try {
            setError('');
            return await api(`/api/subscriptions/${subscription.id}/finalized-edit-preview`, {
                method: 'POST',
                body: JSON.stringify(values),
            });
        } catch (requestError) {
            setError(requestError.message);
            return null;
        }
    };
    const updateSubscription = (subscription, values) =>
        run(async () => {
            const endpoint =
                subscription.status === 'DRAFT'
                    ? 'draft'
                    : subscription.status === 'FINALIZED'
                      ? 'finalized'
                      : 'metadata';
            await api(`/api/subscriptions/${subscription.id}/${endpoint}`, {
                method: 'PUT',
                body: JSON.stringify(values),
            });
            await load();
        }, 'Карточка абонемента обновлена.');
    const finalize = (subscription) =>
        run(async () => {
            await api(`/api/subscriptions/${subscription.id}/finalize`, { method: 'POST' });
            await load();
        }, 'Абонемент рассчитан, начисления созданы.');
    const complete = (subscription) =>
        run(async () => {
            await api(`/api/subscriptions/${subscription.id}/complete`, { method: 'POST' });
            await load();
        }, 'Абонемент завершён.');
    return { createSubscription, previewSubscriptionUpdate, updateSubscription, finalize, complete };
}
