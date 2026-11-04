const shortLabels = {
    POOL_SHARE: 'Бассейн',
    SKIRT_RENT: 'Юбка',
    PADDLE_RENT: 'Весло',
    KAYAK_RENT: 'Каяк',
};

export function chargeTitle(charge, subscriptions = []) {
    const title = shortLabels[charge.type] || charge.description;
    if (!charge.subscriptionId) return title;
    const subscription = subscriptions.find((item) => item.id === charge.subscriptionId);
    return `${title} · ${subscription?.title || `Абонемент #${charge.subscriptionId}`}`;
}
