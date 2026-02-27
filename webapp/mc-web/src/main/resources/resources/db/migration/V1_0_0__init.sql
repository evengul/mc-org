create table world(
                      id int generated always as identity,
                      name varchar(255) not null,
                      primary key(id)
);

create table team(
                     id int generated always as identity ,
                     world_id int not null ,
                     name varchar(255) not null,
                     primary key(id),
                     constraint fk_world
                         foreign key (world_id)
                             references world(id)
);

create table project(
                        id int generated always as identity,
                        world_id int not null,
                        team_id int not null,
                        name varchar(255) not null,
                        archived boolean not null,
                        primary key(id),
                        constraint fk_world foreign key (world_id) references world(id),
                        constraint fk_team foreign key (team_id) references team(id)
);

create table task(
                     id int generated always as identity,
                     project_id int not null,
                     name varchar(255) not null,
                     needed int not null,
                     done int not null,
                     primary key(id),
                     constraint fk_project foreign key (project_id) references project(id)
);

create table project_dependency(
                                   id int generated always as identity,
                                   dependant_task_id int not null,
                                   project_dependency_id int not null,
                                   priority varchar(255) not null,
                                   primary key(id),
                                   constraint fk_task_id foreign key (dependant_task_id) references task(id),
                                   constraint fk_project_id foreign key (project_dependency_id) references project(id)
);

create table resource_pack(
                              id int generated always as identity,
                              name varchar(255) not null,
                              version varchar(255) not null,
                              server_type varchar(255) not null,
                              primary key(id)
);

create table resource(
                         id int generated always as identity,
                         pack_id int not null,
                         name varchar(255) not null,
                         type varchar(255) not null,
                         download_url varchar(2047) not null,
                         primary key(id),
                         constraint fk_pack foreign key (pack_id) references resource_pack(id)
);

create table world_packs(
                            pack_id int not null,
                            world_id int not null,
                            primary key (pack_id, world_id),
                            constraint fk_pack foreign key (pack_id) references resource_pack(id),
                            constraint fk_world foreign key (world_id) references world(id)
);

create table team_packs(
                           pack_id int not null,
                           team_id int not null,
                           primary key (pack_id, team_id),
                           constraint fk_pack foreign key (pack_id) references resource_pack(id),
                           constraint fk_team foreign key (team_id) references team(id)
);

create table users(
                      id int generated always as identity,
                      username varchar(255) not null unique,
                      email varchar(255) not null unique,
                      primary key (id)
);

create table permission(
                           id int generated always as identity,
                           user_id int,
                           authority varchar(255) not null,
                           world_id int,
                           team_id int,
                           primary key (id),
                           constraint user_fk foreign key (user_id) references users(id),
                           constraint world_fk foreign key (world_id) references world(id),
                           constraint team_fk foreign key (team_id) references team(id)
);
