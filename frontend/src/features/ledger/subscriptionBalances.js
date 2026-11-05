export const UNASSIGNED = 'unassigned';
export const subscriptionKey = (id) => id == null ? UNASSIGNED : String(id);
export const subscriptionTitle = (group) => group.subscriptionId == null
    ? 'Без абонемента'
    : group.title || `Абонемент #${group.subscriptionId}`;

export function balanceFilterOptions(subscriptions, people) {
    const options = new Map(subscriptions.map(({ id, title }) => [String(id), title || `Абонемент #${id}`]));
    for (const person of people) {
        for (const group of person.subscriptionBalances || []) {
            const key = subscriptionKey(group.subscriptionId);
            if (!options.has(key)) options.set(key, subscriptionTitle(group));
        }
    }
    options.delete(UNASSIGNED);
    return [...options].map(([key, title]) => ({ key, title }))
        .sort((a, b) => a.title.localeCompare(b.title, 'ru') || a.key.localeCompare(b.key))
        .concat({ key: UNASSIGNED, title: 'Без абонемента' });
}

export function filteredPersonBalances(people, selectedKeys) {
    if (!selectedKeys.length) return people;
    return people.flatMap((person) => {
        const subscriptionBalances = (person.subscriptionBalances || [])
            .filter((group) => selectedKeys.includes(subscriptionKey(group.subscriptionId)));
        if (!subscriptionBalances.length) return [];
        const charged = subscriptionBalances.reduce((sum, group) => sum + group.charged, 0);
        const paid = subscriptionBalances.reduce((sum, group) => sum + group.allocated, 0);
        return [{ ...person, charged, paid, balance: charged - paid, subscriptionBalances }];
    });
}
