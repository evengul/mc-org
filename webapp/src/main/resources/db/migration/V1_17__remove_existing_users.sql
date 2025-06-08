update users set minecraft_uuid = 'd5ec6cd18e8a4835b544809ee6dbcd2d' where username = 'lilpebblez';

update task set assignee = null where assignee in (select id from users where minecraft_uuid is null);
update project set assignee = null where assignee in (select id from users where minecraft_uuid is null);

delete from permission where user_id in (select id from users where minecraft_uuid is null);

delete from users where minecraft_uuid is null;