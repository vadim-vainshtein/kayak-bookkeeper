create table people (
    id bigserial primary key, full_name varchar(255) not null, phone varchar(255), note varchar(1000), active boolean not null default true
);
create table pricing_rates (
    id bigserial primary key, code varchar(40) not null, amount bigint not null check (amount > 0),
    effective_from date not null, effective_to date,
    check (effective_to is null or effective_to >= effective_from)
);
create index pricing_rates_lookup on pricing_rates(code, effective_from, effective_to);
create table training_slots (
    id bigserial primary key, title varchar(255) not null, day_of_week varchar(12) not null, start_time time not null,
    capacity integer not null default 8 check (capacity between 1 and 8), active boolean not null default true
);
create table subscriptions (
    id bigserial primary key, slot_id bigint not null references training_slots(id), subscription_month date not null,
    status varchar(20) not null, pool_rental_amount bigint, unique(slot_id, subscription_month)
);
create table training_sessions (
    id bigserial primary key, subscription_id bigint not null references subscriptions(id), starts_at timestamp not null,
    unique(subscription_id, starts_at)
);
create table subscription_members (
    id bigserial primary key, subscription_id bigint not null references subscriptions(id), person_id bigint not null references people(id),
    uses_skirt boolean not null default false, uses_paddle boolean not null default false, uses_kayak boolean not null default false,
    pool_share bigint, skirt_fee bigint, paddle_fee bigint, kayak_fee bigint, unique(subscription_id, person_id)
);
create table attendance (
    id bigserial primary key, training_session_id bigint not null references training_sessions(id), person_id bigint not null references people(id),
    subscription_member_id bigint references subscription_members(id), type varchar(20) not null, status varchar(20) not null,
    unique(training_session_id, person_id)
);
create table charges (
    id bigserial primary key, person_id bigint not null references people(id), subscription_id bigint references subscriptions(id),
    attendance_id bigint references attendance(id), type varchar(30) not null, amount bigint not null check (amount > 0), charged_at date not null
);
create table payments (
    id bigserial primary key, person_id bigint not null references people(id), amount bigint not null check (amount > 0),
    paid_at date not null, method varchar(20) not null, note varchar(1000)
);
create table payment_allocations (
    id bigserial primary key, payment_id bigint not null references payments(id), charge_id bigint not null references charges(id),
    amount bigint not null check (amount > 0), unique(payment_id, charge_id)
);
