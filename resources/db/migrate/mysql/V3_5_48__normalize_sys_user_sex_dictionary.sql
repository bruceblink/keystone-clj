-- Normalize user gender dictionary values to 0=female, 1=male, and 2=unknown.

UPDATE sys_dict_data
SET dict_label = '女',
    dict_value = '0',
    dict_sort = 1,
    status = 1,
    remark = '性别女',
    deleted = 0,
    update_time = NOW()
WHERE dict_type = 'sysUser.sex'
  AND dict_value = '0'
  AND deleted = 0
ORDER BY dict_code
LIMIT 1;

INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, is_default, list_class, status, remark,
                           creator_id, updater_id, create_time, update_time, deleted)
SELECT 'sysUser.sex', '女', '0', 1, 0, '', 1, '性别女', NULL, NULL, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'sysUser.sex' AND dict_value = '0' AND deleted = 0
);

UPDATE sys_dict_data
SET dict_label = '男',
    dict_value = '1',
    dict_sort = 2,
    status = 1,
    remark = '性别男',
    deleted = 0,
    update_time = NOW()
WHERE dict_type = 'sysUser.sex'
  AND dict_value = '1'
  AND deleted = 0
ORDER BY dict_code
LIMIT 1;

INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, is_default, list_class, status, remark,
                           creator_id, updater_id, create_time, update_time, deleted)
SELECT 'sysUser.sex', '男', '1', 2, 0, '', 1, '性别男', NULL, NULL, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'sysUser.sex' AND dict_value = '1' AND deleted = 0
);

UPDATE sys_dict_data
SET dict_label = '未知',
    dict_value = '2',
    dict_sort = 3,
    status = 1,
    remark = '性别未知',
    deleted = 0,
    update_time = NOW()
WHERE dict_type = 'sysUser.sex'
  AND dict_value = '2'
  AND deleted = 0
ORDER BY dict_code
LIMIT 1;

INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, is_default, list_class, status, remark,
                           creator_id, updater_id, create_time, update_time, deleted)
SELECT 'sysUser.sex', '未知', '2', 3, 0, '', 1, '性别未知', NULL, NULL, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'sysUser.sex' AND dict_value = '2' AND deleted = 0
);

UPDATE sys_dict_data
SET deleted = 1,
    status = 0,
    update_time = NOW()
WHERE dict_type = 'sysUser.sex'
  AND deleted = 0
  AND (
      dict_value NOT IN ('0', '1', '2')
      OR (dict_value = '0' AND dict_label <> '女')
      OR (dict_value = '1' AND dict_label <> '男')
      OR (dict_value = '2' AND dict_label <> '未知')
  );

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_sys_user_sex_keep AS
SELECT MIN(dict_code) AS dict_code
FROM sys_dict_data
WHERE dict_type = 'sysUser.sex'
  AND dict_value IN ('0', '1', '2')
  AND deleted = 0
GROUP BY dict_value;

UPDATE sys_dict_data
SET deleted = 1,
    status = 0,
    update_time = NOW()
WHERE dict_type = 'sysUser.sex'
  AND dict_value IN ('0', '1', '2')
  AND deleted = 0
  AND dict_code NOT IN (SELECT dict_code FROM tmp_sys_user_sex_keep);

DROP TEMPORARY TABLE IF EXISTS tmp_sys_user_sex_keep;
