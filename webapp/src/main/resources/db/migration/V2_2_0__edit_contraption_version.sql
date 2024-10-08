alter table contraption drop column version_main;
alter table contraption drop column version_secondary;
alter table contraption drop column version_all_above;

alter table contraption add column version_lower_major int not null default 0;
alter table contraption add column version_lower_minor int not null default 0;
alter table contraption add column version_upper_major int;
alter table contraption add column version_upper_minor int;