alter table task add column assignee int;
alter table task add constraint users_fkey foreign key (assignee) references users(id);