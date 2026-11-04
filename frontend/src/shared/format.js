export const formatDate = (value) =>
    value
        ? new Intl.DateTimeFormat('ru-RU', {
              dateStyle: 'medium',
              timeStyle: value.includes('T') ? 'short' : undefined,
          }).format(new Date(value))
        : '—';
export const money = (value) => `${new Intl.NumberFormat('ru-RU').format(value || 0)} ₸`;
export const today = () => {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};
export const activeRate = (rate) =>
    rate.effectiveFrom <= today() && (!rate.effectiveTo || rate.effectiveTo >= today());
export const signedBalance = (value) => {
    const display = -value;
    return display > 0 ? `+${money(display)}` : money(display);
};
export const balanceClass = (value) => (value > 0 ? 'debt' : value < 0 ? 'credit' : '');
export const balanceLabel = (value) =>
    value > 0 ? `Долг: ${money(value)}` : value < 0 ? `Переплата: ${money(-value)}` : 'Баланс нулевой';
