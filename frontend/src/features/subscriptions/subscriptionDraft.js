export function memberMap(subscription) {
    return Object.fromEntries(
        subscription.members.map((member) => [
            member.personId,
            {
                skirt: member.usesSkirt,
                paddle: member.usesPaddle,
                kayak: member.usesKayak,
            },
        ]),
    );
}

export function memberRequests(members) {
    return Object.entries(members).map(([personId, equipment]) => ({
        personId: Number(personId),
        usesSkirt: equipment.skirt,
        usesPaddle: equipment.paddle,
        usesKayak: equipment.kayak,
    }));
}

export function createEditDraft(subscription) {
    return {
        title: subscription.title || '',
        description: subscription.description || '',
        capacity: subscription.capacity,
        sessions: subscription.sessions.map((session) => ({
            date: session.startsAt.slice(0, 10),
            time: session.startsAt.slice(11, 16),
        })),
        members: memberMap(subscription),
    };
}

export function prepareSubscriptionEdit(subscription, draft) {
    const metadata = { title: draft.title || null, description: draft.description || null };
    if (subscription.status === 'COMPLETED') return { values: metadata };
    const startsAt = draft.sessions.map((session) => `${session.date}T${session.time}`);
    const members = memberRequests(draft.members);
    const capacity = Number(draft.capacity);
    if (!startsAt.length || draft.sessions.some((session) => !session.date || !session.time)) {
        return { error: 'Добавьте хотя бы одну тренировку и заполните её дату и время.' };
    }
    if (new Set(startsAt).size !== startsAt.length)
        return { error: 'Дата и время тренировок не должны повторяться.' };
    if (!members.length || members.length > capacity)
        return { error: `Количество участников не должно превышать вместимость: ${capacity}.` };
    return { values: { ...metadata, startsAt, members, capacity } };
}
