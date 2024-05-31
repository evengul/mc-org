alter table permission add column authority_number int not null default 9999;

update permission
    set authority_number =
        case
           when authority = 'OWNER' then 0
            when authority = 'ADMIN' then 10
            when authority = 'PARTICIPANT' then 20
        end
    where permission.authority_number = 9999;

alter table permission drop column authority;
alter table permission rename column authority_number to authority;