alter table project drop constraint team_fkey;
alter table project drop column team_id;
alter table permission drop constraint team_fkey;
alter table permission drop column team_id;
alter table permission drop constraint pack_fkey;
alter table permission drop column pack_id;

drop table team_packs;
drop table world_packs;
drop table team;
drop table resource;
drop table resource_pack;