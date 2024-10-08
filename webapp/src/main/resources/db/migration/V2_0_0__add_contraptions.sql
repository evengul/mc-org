create table contraption (
    id int generated always as identity,
    name varchar(255) not null,
    description varchar(4096) not null default '',
    archived boolean not null default false,
    authors varchar(4096), -- Split by ;
    game_type varchar(127) default 'JAVA', -- BEDROCK or JAVA
    version_main int not null,
    version_secondary int not null,
    version_all_above boolean not null default false,
    schematic_url varchar(2048),
    world_download_url varchar(2048),
    primary key(id)
)