alter table users add column profile_photo varchar(255);
alter table users add column selected_world int;

alter table users add constraint world_fkey foreign key (selected_world) references world(id)