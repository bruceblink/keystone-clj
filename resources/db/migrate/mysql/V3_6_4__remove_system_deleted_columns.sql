DELETE rm
FROM sys_role_menu rm
LEFT JOIN sys_role r ON r.role_id = rm.role_id
LEFT JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.deleted = 1
   OR m.deleted = 1;

DELETE FROM sys_config WHERE deleted = 1;
DELETE FROM sys_dept WHERE deleted = 1;
DELETE FROM sys_login_info WHERE deleted = 1;
DELETE FROM sys_menu WHERE deleted = 1;
DELETE FROM sys_notice WHERE deleted = 1;
DELETE FROM sys_operation_log WHERE deleted = 1;
DELETE FROM sys_post WHERE deleted = 1;
DELETE FROM sys_role WHERE deleted = 1;
DELETE FROM sys_user WHERE deleted = 1;
DELETE FROM sys_dict_type WHERE deleted = 1;
DELETE FROM sys_dict_data WHERE deleted = 1;
DELETE FROM sys_job WHERE deleted = 1;

ALTER TABLE sys_config DROP COLUMN deleted;
ALTER TABLE sys_dept DROP COLUMN deleted;
ALTER TABLE sys_login_info DROP COLUMN deleted;
ALTER TABLE sys_menu DROP COLUMN deleted;
ALTER TABLE sys_notice DROP COLUMN deleted;
ALTER TABLE sys_operation_log DROP COLUMN deleted;
ALTER TABLE sys_post DROP COLUMN deleted;
ALTER TABLE sys_role DROP COLUMN deleted;
ALTER TABLE sys_user DROP COLUMN deleted;
ALTER TABLE sys_dict_type DROP COLUMN deleted;
ALTER TABLE sys_dict_data DROP COLUMN deleted;
ALTER TABLE sys_job DROP COLUMN deleted;
