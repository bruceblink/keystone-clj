SET @ddl = IF(
    (SELECT COUNT(*)
     FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user') > 0
    AND
    (SELECT COUNT(*)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user'
       AND COLUMN_NAME = 'external_user_id') = 0,
    'ALTER TABLE sys_user ADD COLUMN external_user_id varchar(128) null comment ''外部用户ID'' after external_subject',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*)
     FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user') > 0
    AND
    (SELECT COUNT(*)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user'
       AND INDEX_NAME = 'uk_sys_user_external_user_id') = 0,
    'CREATE UNIQUE INDEX uk_sys_user_external_user_id on sys_user (external_user_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
