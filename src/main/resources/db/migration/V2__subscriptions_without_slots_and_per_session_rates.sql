-- Subscriptions now own their capacity. Existing subscriptions retain the capacity
-- of their former slot. Legacy slot columns/tables are deliberately retained in this
-- cross-database migration for audit/migration compatibility; the application no
-- longer maps or exposes them.
alter table subscriptions add column capacity integer;
update subscriptions set capacity = (select capacity from training_slots where training_slots.id = subscriptions.slot_id);
update subscriptions set capacity = 8 where capacity is null;
alter table subscriptions alter column capacity set default 8;
alter table subscriptions alter column capacity set not null;
alter table subscriptions add constraint subscriptions_capacity_check check (capacity between 1 and 8);
-- New subscriptions do not use legacy slot/month columns. They stay nullable only
-- so an existing database can be upgraded without database-specific FK surgery.
alter table subscriptions alter column slot_id drop not null;
alter table subscriptions alter column subscription_month drop not null;

-- Equipment rates are no longer monthly: their amount is for one training.
update pricing_rates set code = 'SKIRT_RENTAL' where code = 'SKIRT_MONTHLY';
update pricing_rates set code = 'PADDLE_RENTAL' where code = 'PADDLE_MONTHLY';
update pricing_rates set code = 'KAYAK_RENTAL' where code = 'KAYAK_MONTHLY';

alter table charges add column description varchar(1000);
