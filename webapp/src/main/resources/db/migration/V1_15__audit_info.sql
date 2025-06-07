alter table world add column created_by varchar(255) not null default 'system';
alter table world add column created_at timestamp with time zone not null default now();
alter table world add column updated_by varchar(255) not null default 'system';
alter table world add column updated_at timestamp with time zone not null default now();

alter table project add column created_by varchar(255) not null default 'system';
alter table project add column created_at timestamp with time zone not null default now();
alter table project add column updated_by varchar(255) not null default 'system';
alter table project add column updated_at timestamp with time zone not null default now();
alter table project add column completed_at timestamp with time zone;

alter table task add column created_by varchar(255) not null default 'system';
alter table task add column created_at timestamp with time zone not null default now();
alter table task add column updated_by varchar(255) not null default 'system';
alter table task add column updated_at timestamp with time zone not null default now();
alter table task add column completed_at timestamp with time zone;

alter table users add column created_by varchar(255) not null default 'system';
alter table users add column created_at timestamp with time zone not null default now();
alter table users add column updated_by varchar(255) not null default 'system';
alter table users add column updated_at timestamp with time zone not null default now();
alter table users add column last_login timestamp with time zone;

alter table permission add column created_by varchar(255) not null default 'system';
alter table permission add column created_at timestamp with time zone not null default now();
alter table permission add column updated_by varchar(255) not null default 'system';
alter table permission add column updated_at timestamp with time zone not null default now();

alter table project_dependency add column created_by varchar(255) not null default 'system';
alter table project_dependency add column created_at timestamp with time zone not null default now();
alter table project_dependency add column updated_by varchar(255) not null default 'system';
alter table project_dependency add column updated_at timestamp with time zone not null default now();
