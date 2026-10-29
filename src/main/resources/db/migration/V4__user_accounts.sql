create table user_accounts (
    id bigserial primary key,
    login varchar(100) not null unique,
    password_hash varchar(100) not null
);
