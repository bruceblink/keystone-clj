UPDATE sys_user
SET email = NULL
WHERE email = '';

ALTER TABLE sys_user
    MODIFY email varchar(128) NULL DEFAULT NULL COMMENT '用户邮箱';

SET @ddl = IF(
    (SELECT COUNT(*)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user'
       AND INDEX_NAME = 'uk_sys_user_username') = 0,
    'CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user'
       AND INDEX_NAME = 'uk_sys_user_email') = 0,
    'CREATE UNIQUE INDEX uk_sys_user_email ON sys_user (email)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
