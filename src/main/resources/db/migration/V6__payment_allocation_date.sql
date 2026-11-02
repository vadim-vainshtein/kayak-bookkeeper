-- Allocation dates make the end-of-period report an immutable historical view.
-- Existing allocations did not retain their creation date, so their payment date
-- is the best available legacy approximation. New allocations use their actual
-- business date when they are recorded.
alter table payment_allocations add column allocated_at date;
update payment_allocations
set allocated_at = (select paid_at from payments where payments.id = payment_allocations.payment_id)
where allocated_at is null;
alter table payment_allocations alter column allocated_at set not null;
