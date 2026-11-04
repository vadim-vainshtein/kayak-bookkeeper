export function createRateActions({ api, submit, load }) {
    const createRate = (event) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api('/api/pricing-rates', {
                    method: 'POST',
                    body: JSON.stringify({
                        code: form.get('code'),
                        amount: Number(form.get('amount')),
                        effectiveFrom: form.get('effectiveFrom'),
                        effectiveTo: form.get('effectiveTo') || null,
                    }),
                });
                await load();
            },
            'Тариф добавлен.',
        );
    };
    const endRate = (event, rate) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api(`/api/pricing-rates/${rate.id}/effective-to`, {
                    method: 'PUT',
                    body: JSON.stringify({ effectiveTo: form.get('effectiveTo') }),
                });
                await load();
            },
            'Дата окончания тарифа установлена.',
        );
    };
    return { createRate, endRate };
}
