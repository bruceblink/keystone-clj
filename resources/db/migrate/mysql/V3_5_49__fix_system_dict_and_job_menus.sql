-- V3_5_34 uses menu_id 66-85. V3_5_46 reused 66-77 with INSERT IGNORE, so the
-- system dictionary/job menus can be skipped silently. Restore them with
-- auto-increment ids and bind the default common role.

SET @system_parent_id = (
    SELECT menu_id
    FROM sys_menu
    WHERE parent_id = 0
      AND menu_name = '系统管理'
      AND deleted = 0
    ORDER BY menu_id
    LIMIT 1
);

SET @monitor_parent_id = (
    SELECT menu_id
    FROM sys_menu
    WHERE parent_id = 0
      AND menu_name = '系统监控'
      AND deleted = 0
    ORDER BY menu_id
    LIMIT 1
);

UPDATE sys_menu
SET menu_name = '字典管理',
    menu_type = 1,
    router_name = 'SystemDict',
    parent_id = @system_parent_id,
    path = '/system/dict/index',
    is_button = 0,
    permission = 'system:dict:list',
    meta_info = '{"title":"字典管理","icon":"ep:collection","showParent":true}',
    status = 1,
    deleted = 0,
    updater_id = 0,
    update_time = NOW()
WHERE @system_parent_id IS NOT NULL
  AND is_button = 0
  AND (router_name = 'SystemDict'
      OR path = '/system/dict/index'
      OR permission = 'system:dict:list');

INSERT INTO sys_menu (
    menu_name, menu_type, router_name, parent_id, path, is_button, permission,
    meta_info, status, remark, creator_id, create_time, updater_id, update_time, deleted
)
SELECT '字典管理', 1, 'SystemDict', @system_parent_id, '/system/dict/index', 0,
       'system:dict:list',
       '{"title":"字典管理","icon":"ep:collection","showParent":true}',
       1, '字典管理菜单', 0, NOW(), NULL, NULL, 0
WHERE @system_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu
      WHERE is_button = 0
        AND deleted = 0
        AND (router_name = 'SystemDict'
            OR path = '/system/dict/index'
            OR permission = 'system:dict:list')
  );

SET @dict_menu_id = (
    SELECT menu_id
    FROM sys_menu
    WHERE is_button = 0
      AND deleted = 0
      AND (router_name = 'SystemDict'
          OR path = '/system/dict/index'
          OR permission = 'system:dict:list')
    ORDER BY menu_id DESC
    LIMIT 1
);

UPDATE sys_menu m
JOIN (
    SELECT 'system:dict:query' AS permission, '字典查询' AS menu_name, '{"title":"字典查询"}' AS meta_info
    UNION ALL SELECT 'system:dict:add', '字典新增', '{"title":"字典新增"}'
    UNION ALL SELECT 'system:dict:edit', '字典修改', '{"title":"字典修改"}'
    UNION ALL SELECT 'system:dict:remove', '字典删除', '{"title":"字典删除"}'
) s ON m.permission = s.permission
SET m.menu_name = s.menu_name,
    m.menu_type = 0,
    m.router_name = ' ',
    m.parent_id = @dict_menu_id,
    m.path = '',
    m.is_button = 1,
    m.meta_info = s.meta_info,
    m.status = 1,
    m.deleted = 0,
    m.updater_id = 0,
    m.update_time = NOW()
WHERE @dict_menu_id IS NOT NULL
  AND m.is_button = 1;

INSERT INTO sys_menu (
    menu_name, menu_type, router_name, parent_id, path, is_button, permission,
    meta_info, status, remark, creator_id, create_time, updater_id, update_time, deleted
)
SELECT s.menu_name, 0, ' ', @dict_menu_id, '', 1, s.permission, s.meta_info,
       1, '', 0, NOW(), NULL, NULL, 0
FROM (
    SELECT 'system:dict:query' AS permission, '字典查询' AS menu_name, '{"title":"字典查询"}' AS meta_info
    UNION ALL SELECT 'system:dict:add', '字典新增', '{"title":"字典新增"}'
    UNION ALL SELECT 'system:dict:edit', '字典修改', '{"title":"字典修改"}'
    UNION ALL SELECT 'system:dict:remove', '字典删除', '{"title":"字典删除"}'
) s
WHERE @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu m
      WHERE m.parent_id = @dict_menu_id
        AND m.is_button = 1
        AND m.permission = s.permission
        AND m.deleted = 0
  );

UPDATE sys_menu
SET menu_name = '定时任务管理',
    menu_type = 1,
    router_name = 'SystemJob',
    parent_id = @monitor_parent_id,
    path = '/system/job/index',
    is_button = 0,
    permission = 'system:job:list',
    meta_info = '{"title":"定时任务管理","icon":"ep:timer","showParent":true}',
    status = 1,
    deleted = 0,
    updater_id = 0,
    update_time = NOW()
WHERE @monitor_parent_id IS NOT NULL
  AND is_button = 0
  AND (router_name = 'SystemJob'
      OR path = '/system/job/index'
      OR permission = 'system:job:list');

INSERT INTO sys_menu (
    menu_name, menu_type, router_name, parent_id, path, is_button, permission,
    meta_info, status, remark, creator_id, create_time, updater_id, update_time, deleted
)
SELECT '定时任务管理', 1, 'SystemJob', @monitor_parent_id, '/system/job/index', 0,
       'system:job:list',
       '{"title":"定时任务管理","icon":"ep:timer","showParent":true}',
       1, '定时任务管理菜单', 0, NOW(), NULL, NULL, 0
WHERE @monitor_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu
      WHERE is_button = 0
        AND deleted = 0
        AND (router_name = 'SystemJob'
            OR path = '/system/job/index'
            OR permission = 'system:job:list')
  );

SET @job_menu_id = (
    SELECT menu_id
    FROM sys_menu
    WHERE is_button = 0
      AND deleted = 0
      AND (router_name = 'SystemJob'
          OR path = '/system/job/index'
          OR permission = 'system:job:list')
    ORDER BY menu_id DESC
    LIMIT 1
);

UPDATE sys_menu m
JOIN (
    SELECT 'system:job:query' AS permission, '任务查询' AS menu_name, '{"title":"任务查询"}' AS meta_info
    UNION ALL SELECT 'system:job:add', '任务新增', '{"title":"任务新增"}'
    UNION ALL SELECT 'system:job:edit', '任务修改', '{"title":"任务修改"}'
    UNION ALL SELECT 'system:job:remove', '任务删除', '{"title":"任务删除"}'
    UNION ALL SELECT 'system:job:changeStatus', '任务状态修改', '{"title":"任务状态修改"}'
    UNION ALL SELECT 'system:job:run', '任务执行', '{"title":"任务执行"}'
) s ON m.permission = s.permission
SET m.menu_name = s.menu_name,
    m.menu_type = 0,
    m.router_name = ' ',
    m.parent_id = @job_menu_id,
    m.path = '',
    m.is_button = 1,
    m.meta_info = s.meta_info,
    m.status = 1,
    m.deleted = 0,
    m.updater_id = 0,
    m.update_time = NOW()
WHERE @job_menu_id IS NOT NULL
  AND m.is_button = 1;

INSERT INTO sys_menu (
    menu_name, menu_type, router_name, parent_id, path, is_button, permission,
    meta_info, status, remark, creator_id, create_time, updater_id, update_time, deleted
)
SELECT s.menu_name, 0, ' ', @job_menu_id, '', 1, s.permission, s.meta_info,
       1, '', 0, NOW(), NULL, NULL, 0
FROM (
    SELECT 'system:job:query' AS permission, '任务查询' AS menu_name, '{"title":"任务查询"}' AS meta_info
    UNION ALL SELECT 'system:job:add', '任务新增', '{"title":"任务新增"}'
    UNION ALL SELECT 'system:job:edit', '任务修改', '{"title":"任务修改"}'
    UNION ALL SELECT 'system:job:remove', '任务删除', '{"title":"任务删除"}'
    UNION ALL SELECT 'system:job:changeStatus', '任务状态修改', '{"title":"任务状态修改"}'
    UNION ALL SELECT 'system:job:run', '任务执行', '{"title":"任务执行"}'
) s
WHERE @job_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu m
      WHERE m.parent_id = @job_menu_id
        AND m.is_button = 1
        AND m.permission = s.permission
        AND m.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, menu_ids.menu_id
FROM sys_role r
JOIN (
    SELECT @system_parent_id AS menu_id
    UNION SELECT @monitor_parent_id
    UNION SELECT m.menu_id
    FROM sys_menu m
    WHERE m.deleted = 0
      AND (
          m.menu_id IN (@dict_menu_id, @job_menu_id)
          OR m.parent_id IN (@dict_menu_id, @job_menu_id)
      )
) menu_ids ON menu_ids.menu_id IS NOT NULL
WHERE r.role_id = 2
  AND r.deleted = 0;
