alter table users add column minecraft_uuid varchar(36);

alter table users add column display_name varchar(255);
update users set display_name = username where display_name is null;

