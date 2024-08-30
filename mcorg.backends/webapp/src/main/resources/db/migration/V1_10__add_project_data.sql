alter table project add column priority VARCHAR(127) default 'LOW' not null;
alter table project add column dimension varchar(127) default 'OVERWORLD';
alter table project add column assignee int;
alter table project add column progress float default 0.0;

alter table project add constraint assignee_fkey foreign key (assignee) references users(id)