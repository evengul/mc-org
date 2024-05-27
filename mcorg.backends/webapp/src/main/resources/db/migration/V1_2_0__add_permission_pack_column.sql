alter table permission add column pack_id integer;

alter table permission add foreign key (pack_id) references resource_pack(id)