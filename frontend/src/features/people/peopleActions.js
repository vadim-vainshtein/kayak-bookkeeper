export function createPeopleActions({ api, submit, load }) {
    const addPerson = (event) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api('/api/people', {
                    method: 'POST',
                    body: JSON.stringify({
                        fullName: form.get('fullName'),
                        phone: form.get('phone') || null,
                        note: null,
                    }),
                });
                await load();
            },
            'Каякер добавлен.',
        );
    };
    const updatePerson = (event, person) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        return submit(
            target,
            async () => {
                await api(`/api/people/${person.id}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        fullName: form.get('fullName'),
                        phone: form.get('phone') || null,
                        note: form.get('note') || null,
                        active: form.get('active') === 'on',
                    }),
                });
                await load();
            },
            'Карточка каякера обновлена.',
        );
    };
    return { addPerson, updatePerson };
}
