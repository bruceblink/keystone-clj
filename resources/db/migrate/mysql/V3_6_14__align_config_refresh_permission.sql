UPDATE sys_menu
SET menu_name = '刷新缓存',
    permission = 'system:config:refresh',
    meta_info = '{"title":"刷新缓存"}'
WHERE is_button = 1
  AND permission = 'system:config:remove';
