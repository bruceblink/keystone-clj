DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.is_button = 1
  AND m.permission IN (
      'system:config:export',
      'monitor:operlog:query',
      'monitor:logininfor:query',
      'monitor:online:query',
      'monitor:online:batchLogout'
  );

DELETE FROM sys_menu
WHERE is_button = 1
  AND permission IN (
      'system:config:export',
      'monitor:operlog:query',
      'monitor:logininfor:query',
      'monitor:online:query',
      'monitor:online:batchLogout'
  );
