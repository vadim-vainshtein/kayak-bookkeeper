-- Start with no visitors, including for already calculated subscriptions.
alter table training_sessions add column attendance_revision bigint not null default 0;
create table session_attendance (
    id bigserial primary key,
    training_session_id bigint not null references training_sessions(id),
    person_id bigint not null references people(id),
    guest boolean not null,
    charge_id bigint unique references charges(id),
    unique(training_session_id, person_id)
);
