-- Migrate dictionary entries formerly assembled from enum metadata into managed database rows.

INSERT IGNORE INTO sys_dict_type (dict_name, dict_type, status, remark, creator_id, updater_id, create_time, update_time, deleted)
SELECT seed.dict_name, seed.dict_type, 1, seed.remark, NULL, NULL, NOW(), NOW(), 0
FROM (
    SELECT '系统是否' AS dict_name, 'common.yesOrNo' AS dict_type, '系统是否列表' AS remark
    UNION ALL SELECT '通用状态', 'common.status', '通用状态列表'
    UNION ALL SELECT '用户性别', 'sysUser.sex', '用户性别列表'
    UNION ALL SELECT '用户状态', 'sysUser.status', '用户状态列表'
    UNION ALL SELECT '菜单显示状态', 'sysMenu.isVisible', '菜单显示状态列表'
    UNION ALL SELECT '通知类型', 'sysNotice.noticeType', '通知类型列表'
    UNION ALL SELECT '通知状态', 'sysNotice.status', '通知状态列表'
    UNION ALL SELECT '操作业务类型', 'sysOperationLog.businessType', '操作业务类型列表'
    UNION ALL SELECT '操作状态', 'sysOperationLog.status', '操作状态列表'
    UNION ALL SELECT '操作者类型', 'sysOperationLog.operatorType', '操作者类型列表'
    UNION ALL SELECT '登录状态', 'sysLoginLog.status', '登录状态列表'
    UNION ALL SELECT '任务状态', 'sysJob.status', '任务状态列表'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type existing WHERE existing.dict_type = seed.dict_type
);

INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, is_default, list_class, status, remark,
                           creator_id, updater_id, create_time, update_time, deleted)
SELECT seed.dict_type, seed.dict_label, seed.dict_value, seed.dict_sort, seed.is_default, seed.list_class, 1, seed.remark,
       NULL, NULL, NOW(), NOW(), 0
FROM (
    SELECT 'common.yesOrNo' AS dict_type, '是' AS dict_label, '1' AS dict_value, 1 AS dict_sort, 0 AS is_default,
           '' AS list_class, '是' AS remark
    UNION ALL SELECT 'common.yesOrNo', '否', '0', 2, 0, 'danger', '否'
    UNION ALL SELECT 'common.status', '正常', '1', 1, 0, '', '正常状态'
    UNION ALL SELECT 'common.status', '停用', '0', 2, 0, 'danger', '停用状态'
    UNION ALL SELECT 'sysUser.sex', '女', '0', 1, 0, '', '性别女'
    UNION ALL SELECT 'sysUser.sex', '男', '1', 2, 0, '', '性别男'
    UNION ALL SELECT 'sysUser.sex', '未知', '2', 3, 0, '', '性别未知'
    UNION ALL SELECT 'sysUser.status', '正常', '1', 1, 0, '', '用户正常'
    UNION ALL SELECT 'sysUser.status', '禁用', '2', 2, 0, 'danger', '用户禁用'
    UNION ALL SELECT 'sysUser.status', '冻结', '3', 3, 0, 'warning', '用户冻结'
    UNION ALL SELECT 'sysMenu.isVisible', '显示', '1', 1, 0, '', '显示菜单'
    UNION ALL SELECT 'sysMenu.isVisible', '隐藏', '0', 2, 0, 'danger', '隐藏菜单'
    UNION ALL SELECT 'sysNotice.noticeType', '通知', '1', 1, 0, 'warning', '通知'
    UNION ALL SELECT 'sysNotice.noticeType', '公告', '2', 2, 0, 'success', '公告'
    UNION ALL SELECT 'sysNotice.status', '正常', '1', 1, 0, '', '正常状态'
    UNION ALL SELECT 'sysNotice.status', '关闭', '0', 2, 0, 'danger', '关闭状态'
    UNION ALL SELECT 'sysOperationLog.businessType', '其他操作', '0', 1, 0, 'info', '其他操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '添加', '1', 2, 0, '', '添加操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '修改', '2', 3, 0, '', '修改操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '删除', '3', 4, 0, 'danger', '删除操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '授权', '4', 5, 0, '', '授权操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '导出', '5', 6, 0, 'warning', '导出操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '导入', '6', 7, 0, 'warning', '导入操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '强退', '7', 8, 0, 'danger', '强退操作'
    UNION ALL SELECT 'sysOperationLog.businessType', '清空', '8', 9, 0, 'danger', '清空操作'
    UNION ALL SELECT 'sysOperationLog.status', '成功', '1', 1, 0, '', '操作成功'
    UNION ALL SELECT 'sysOperationLog.status', '失败', '0', 2, 0, 'danger', '操作失败'
    UNION ALL SELECT 'sysOperationLog.operatorType', '其他', '1', 1, 0, '', '其他操作者'
    UNION ALL SELECT 'sysOperationLog.operatorType', 'Web用户', '2', 2, 0, '', 'Web用户'
    UNION ALL SELECT 'sysOperationLog.operatorType', '手机端用户', '3', 3, 0, '', '手机端用户'
    UNION ALL SELECT 'sysLoginLog.status', '登录成功', '1', 1, 0, 'success', '登录成功'
    UNION ALL SELECT 'sysLoginLog.status', '退出成功', '2', 2, 0, 'info', '退出成功'
    UNION ALL SELECT 'sysLoginLog.status', '注册', '3', 3, 0, '', '注册'
    UNION ALL SELECT 'sysLoginLog.status', '登录失败', '0', 4, 0, 'danger', '登录失败'
    UNION ALL SELECT 'sysJob.status', '正常', '1', 1, 0, '', '任务正常'
    UNION ALL SELECT 'sysJob.status', '暂停', '0', 2, 0, 'danger', '任务暂停'
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_data existing
    WHERE existing.dict_type = seed.dict_type
      AND existing.dict_value = seed.dict_value
      AND existing.deleted = 0
);
