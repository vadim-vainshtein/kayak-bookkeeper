alter table user_accounts add column role varchar(20) not null default 'ADMIN';
alter table user_accounts add constraint user_accounts_role_check check (role in ('ADMIN', 'READ_ONLY'));
alter table user_accounts alter column role drop default;
