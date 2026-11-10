-- Normalize legacy string dictionary values before changing the column type.
UPDATE sys_dict_data
SET dict_value = TRIM(dict_value)
WHERE dict_value <> TRIM(dict_value);

UPDATE sys_dict_data
SET dict_value = '0'
WHERE dict_value IS NULL
   OR dict_value = ''
   OR dict_value NOT REGEXP '^-?[0-9]+$'
   OR CAST(dict_value AS SIGNED) < -2147483648
   OR CAST(dict_value AS SIGNED) > 2147483647;

ALTER TABLE sys_dict_data
    MODIFY dict_value int DEFAULT 0 NOT NULL COMMENT '字典键值';
