alter table world add column game_type varchar(127) not null default 'JAVA'; -- JAVA OR BEDROCK
alter table world add column version_main int not null default 21;
alter table world add column version_secondary int not null default 0;
alter table world add column is_technical boolean not null default false;