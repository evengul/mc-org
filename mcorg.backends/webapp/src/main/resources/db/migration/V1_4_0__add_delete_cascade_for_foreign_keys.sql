alter table permission drop constraint permission_pack_id_fkey;
alter table permission drop constraint team_fk;
alter table permission drop constraint world_fk;
alter table permission drop constraint user_fk;

alter table permission add constraint pack_fkey foreign key (pack_id) references resource_pack(id) on delete cascade;
alter table permission add constraint world_fkey foreign key (world_id) references world(id) on delete cascade;
alter table permission add constraint team_fkey foreign key (team_id) references team(id) on delete cascade;
alter table permission add constraint user_fkey foreign key (user_id) references users(id) on delete cascade;

alter table project drop constraint fk_team;
alter table project drop constraint fk_world;

alter table project add constraint world_fkey foreign key (world_id) references world(id) on delete cascade;
alter table project add constraint team_fkey foreign key (team_id) references team(id) on delete cascade;

alter table project_dependency drop constraint fk_project_id;
alter table project_dependency drop constraint fk_task_id;

alter table project_dependency add constraint project_fkey foreign key (project_dependency_id) references project(id) on delete cascade;
alter table project_dependency add constraint task_fkey foreign key (dependant_task_id) references task(id) on delete cascade;

alter table resource drop constraint fk_pack;

alter table resource add constraint pack_fkey foreign key (pack_id) references resource_pack(id) on delete cascade;

alter table task drop constraint fk_project;

alter table task add constraint project_fkey foreign key (project_id) references project(id) on delete cascade;

alter table team drop constraint fk_world;

alter table team add constraint world_fkey foreign key (world_id) references world(id) on delete cascade;

alter table team_packs drop constraint fk_pack;
alter table team_packs drop constraint fk_team;

alter table team_packs add constraint pack_fkey foreign key (pack_id) references resource_pack(id) on delete cascade;
alter table team_packs add constraint team_fkey foreign key (team_id) references team(id) on delete cascade;

alter table world_packs drop constraint fk_pack;
alter table world_packs drop constraint fk_world;

alter table world_packs add constraint pack_fkey foreign key (pack_id) references resource_pack(id) on delete cascade;
alter table world_packs add constraint world_fkey foreign key (world_id) references world(id) on delete cascade;