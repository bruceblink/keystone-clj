-- ----------------------------
-- 字典类型表
-- ----------------------------
create table if not exists sys_dict_type
(
    dict_id     bigint auto_increment comment '字典主键'
        primary key,
    dict_name   varchar(100) default '' not null comment '字典名称',
    dict_type   varchar(100) default '' not null comment '字典类型',
    status      tinyint(1)   default 1  not null comment '状态（1正常 0停用）',
    remark      varchar(500)            null comment '备注',
    creator_id  bigint                  null comment '创建者ID',
    updater_id  bigint                  null comment '更新者ID',
    create_time datetime                null comment '创建时间',
    update_time datetime                null comment '更新时间',
    deleted     tinyint(1)   default 0  not null comment '逻辑删除',
    constraint dict_type_uniq_idx unique (dict_type)
) comment '字典类型表';

-- ----------------------------
-- 字典数据表
-- ----------------------------
create table if not exists sys_dict_data
(
    dict_code   bigint auto_increment comment '字典编码'
        primary key,
    dict_type   varchar(100) default '' not null comment '字典类型',
    dict_label  varchar(100) default '' not null comment '字典标签',
    dict_value  varchar(100) default '' not null comment '字典键值',
    dict_sort   int          default 0  not null comment '字典排序',
    is_default  tinyint(1)   default 0  not null comment '是否默认（1是 0否）',
    css_class   varchar(100)            null comment '样式属性（其他样式扩展）',
    list_class  varchar(100)            null comment '表格回显样式',
    status      tinyint(1)   default 1  not null comment '状态（1正常 0停用）',
    remark      varchar(500)            null comment '备注',
    creator_id  bigint                  null comment '创建者ID',
    updater_id  bigint                  null comment '更新者ID',
    create_time datetime                null comment '创建时间',
    update_time datetime                null comment '更新时间',
    deleted     tinyint(1)   default 0  not null comment '逻辑删除',
    index idx_dict_type (dict_type)
) comment '字典数据表';

-- ----------------------------
-- 初始数据：常用字典类型
-- ----------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, dict_name, dict_type, status, remark, creator_id, updater_id, create_time, update_time, deleted)
VALUES (1, '系统是否', 'common.yesOrNo', 1, '系统是否列表', NULL, NULL, NOW(), NOW(), 0),
       (2, '通用状态', 'common.status', 1, '通用状态列表', NULL, NULL, NOW(), NOW(), 0),
       (3, '用户性别', 'sysUser.sex', 1, '用户性别列表', NULL, NULL, NOW(), NOW(), 0),
       (4, '用户状态', 'sysUser.status', 1, '用户状态列表', NULL, NULL, NOW(), NOW(), 0),
       (5, '菜单显示状态', 'sysMenu.isVisible', 1, '菜单显示状态列表', NULL, NULL, NOW(), NOW(), 0),
       (6, '通知类型', 'sysNotice.noticeType', 1, '通知类型列表', NULL, NULL, NOW(), NOW(), 0),
       (7, '通知状态', 'sysNotice.status', 1, '通知状态列表', NULL, NULL, NOW(), NOW(), 0),
       (8, '操作业务类型', 'sysOperationLog.businessType', 1, '操作业务类型列表', NULL, NULL, NOW(), NOW(), 0),
       (9, '操作状态', 'sysOperationLog.status', 1, '操作状态列表', NULL, NULL, NOW(), NOW(), 0),
       (10, '操作者类型', 'sysOperationLog.operatorType', 1, '操作者类型列表', NULL, NULL, NOW(), NOW(), 0),
       (11, '登录状态', 'sysLoginLog.status', 1, '登录状态列表', NULL, NULL, NOW(), NOW(), 0),
       (12, '任务状态', 'sysJob.status', 1, '任务状态列表', NULL, NULL, NOW(), NOW(), 0);

-- ----------------------------
-- 初始数据：字典数据
-- ----------------------------
INSERT IGNORE INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, is_default, list_class, status, remark, creator_id, updater_id, create_time, update_time, deleted)
VALUES
-- 系统是否
('common.yesOrNo', '是', '1', 1, 0, '', 1, '是', NULL, NULL, NOW(), NOW(), 0),
('common.yesOrNo', '否', '0', 2, 0, 'danger', 1, '否', NULL, NULL, NOW(), NOW(), 0),
-- 通用状态
('common.status', '正常', '1', 1, 0, '', 1, '正常状态', NULL, NULL, NOW(), NOW(), 0),
('common.status', '停用', '0', 2, 0, 'danger', 1, '停用状态', NULL, NULL, NOW(), NOW(), 0),
-- 用户性别
('sysUser.sex', '女', '0', 1, 0, '', 1, '性别女', NULL, NULL, NOW(), NOW(), 0),
('sysUser.sex', '男', '1', 2, 0, '', 1, '性别男', NULL, NULL, NOW(), NOW(), 0),
('sysUser.sex', '未知', '2', 3, 0, '', 1, '性别未知', NULL, NULL, NOW(), NOW(), 0),
-- 用户状态
('sysUser.status', '正常', '1', 1, 0, '', 1, '用户正常', NULL, NULL, NOW(), NOW(), 0),
('sysUser.status', '禁用', '2', 2, 0, 'danger', 1, '用户禁用', NULL, NULL, NOW(), NOW(), 0),
('sysUser.status', '冻结', '3', 3, 0, 'warning', 1, '用户冻结', NULL, NULL, NOW(), NOW(), 0),
-- 菜单显示状态
('sysMenu.isVisible', '显示', '1', 1, 0, '', 1, '显示菜单', NULL, NULL, NOW(), NOW(), 0),
('sysMenu.isVisible', '隐藏', '0', 2, 0, 'danger', 1, '隐藏菜单', NULL, NULL, NOW(), NOW(), 0),
-- 通知类型
('sysNotice.noticeType', '通知', '1', 1, 0, 'warning', 1, '通知', NULL, NULL, NOW(), NOW(), 0),
('sysNotice.noticeType', '公告', '2', 2, 0, 'success', 1, '公告', NULL, NULL, NOW(), NOW(), 0),
-- 通知状态
('sysNotice.status', '正常', '1', 1, 0, '', 1, '正常状态', NULL, NULL, NOW(), NOW(), 0),
('sysNotice.status', '关闭', '0', 2, 0, 'danger', 1, '关闭状态', NULL, NULL, NOW(), NOW(), 0),
-- 操作业务类型
('sysOperationLog.businessType', '其他操作', '0', 1, 0, 'info', 1, '其他操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '添加', '1', 2, 0, '', 1, '添加操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '修改', '2', 3, 0, '', 1, '修改操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '删除', '3', 4, 0, 'danger', 1, '删除操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '授权', '4', 5, 0, '', 1, '授权操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '导出', '5', 6, 0, 'warning', 1, '导出操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '导入', '6', 7, 0, 'warning', 1, '导入操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '强退', '7', 8, 0, 'danger', 1, '强退操作', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.businessType', '清空', '8', 9, 0, 'danger', 1, '清空操作', NULL, NULL, NOW(), NOW(), 0),
-- 操作状态
('sysOperationLog.status', '成功', '1', 1, 0, '', 1, '操作成功', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.status', '失败', '0', 2, 0, 'danger', 1, '操作失败', NULL, NULL, NOW(), NOW(), 0),
-- 操作者类型
('sysOperationLog.operatorType', '其他', '1', 1, 0, '', 1, '其他操作者', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.operatorType', 'Web用户', '2', 2, 0, '', 1, 'Web用户', NULL, NULL, NOW(), NOW(), 0),
('sysOperationLog.operatorType', '手机端用户', '3', 3, 0, '', 1, '手机端用户', NULL, NULL, NOW(), NOW(), 0),
-- 登录状态
('sysLoginLog.status', '登录成功', '1', 1, 0, 'success', 1, '登录成功', NULL, NULL, NOW(), NOW(), 0),
('sysLoginLog.status', '退出成功', '2', 2, 0, 'info', 1, '退出成功', NULL, NULL, NOW(), NOW(), 0),
('sysLoginLog.status', '注册', '3', 3, 0, '', 1, '注册', NULL, NULL, NOW(), NOW(), 0),
('sysLoginLog.status', '登录失败', '0', 4, 0, 'danger', 1, '登录失败', NULL, NULL, NOW(), NOW(), 0),
-- 任务状态
('sysJob.status', '正常', '1', 1, 0, '', 1, '任务正常', NULL, NULL, NOW(), NOW(), 0),
('sysJob.status', '暂停', '0', 2, 0, 'danger', 1, '任务暂停', NULL, NULL, NOW(), NOW(), 0);
