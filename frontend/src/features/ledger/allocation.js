export const paymentRemaining = (payment) =>
    payment.amount - payment.allocations.reduce((sum, allocation) => sum + allocation.amount, 0);

export const allocationLimit = (paymentAmount, charge) =>
    charge ? Math.min(Number(paymentAmount), charge.amount - charge.allocated) : 0;

export function suggestedAllocation(paymentAmount, charge) {
    return charge && Number(paymentAmount) > 0 ? String(allocationLimit(paymentAmount, charge)) : '';
}

export function validAllocation(amount, paymentAmount, charge) {
    return (
        Number.isSafeInteger(Number(amount)) &&
        Number(amount) > 0 &&
        Number(amount) <= allocationLimit(paymentAmount, charge)
    );
}
