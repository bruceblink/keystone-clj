-- ----------------------------
-- 定时任务表
-- ----------------------------
create table if not exists sys_job
(
    job_id          bigint auto_increment comment '任务ID'
        primary key,
    job_name        varchar(64)  default '' not null comment '任务名称',
    job_group       varchar(64)  default '' not null comment '任务组名',
    invoke_target   varchar(255) default '' not null comment '调用目标，格式 springBean.method()',
    cron_expression varchar(255) default '' not null comment 'Cron执行表达式',
    concurrent      tinyint(1)   default 0  not null comment '是否允许并发执行（1允许 0禁止）',
    status          tinyint(1)   default 0  not null comment '状态（1正常 0暂停）',
    remark          varchar(500)            null comment '备注',
    creator_id      bigint                  null comment '创建者ID',
    updater_id      bigint                  null comment '更新者ID',
    create_time     datetime                null comment '创建时间',
    update_time     datetime                null comment '更新时间',
    deleted         tinyint(1)   default 0  not null comment '逻辑删除',
    index idx_job_group (job_group),
    index idx_job_status (status)
) comment '定时任务表';

-- ----------------------------
-- 字典管理与定时任务菜单
-- ----------------------------
INSERT IGNORE INTO sys_menu (menu_id, menu_name, menu_type, router_name, parent_id, path, is_button, permission, meta_info, status, remark, creator_id, create_time, updater_id, update_time, deleted)
VALUES
    (66, '字典管理', 1, 'SystemDict', 1, '/system/dict/index', 0, 'system:dict:list', '{"title":"字典管理","icon":"ep:collection","showParent":true}', 1, '字典管理菜单', 0, NOW(), NULL, NULL, 0),
    (67, '定时任务', 1, 'SystemJob', 2, '/system/job/index', 0, 'system:job:list', '{"title":"定时任务","icon":"ep:timer","showParent":true}', 1, '定时任务菜单', 0, NOW(), NULL, NULL, 0),
    (68, '字典查询', 0, ' ', 66, '', 1, 'system:dict:query', '{"title":"字典查询"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (69, '字典新增', 0, ' ', 66, '', 1, 'system:dict:add', '{"title":"字典新增"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (70, '字典修改', 0, ' ', 66, '', 1, 'system:dict:edit', '{"title":"字典修改"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (71, '字典删除', 0, ' ', 66, '', 1, 'system:dict:remove', '{"title":"字典删除"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (72, '任务查询', 0, ' ', 67, '', 1, 'system:job:query', '{"title":"任务查询"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (73, '任务新增', 0, ' ', 67, '', 1, 'system:job:add', '{"title":"任务新增"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (74, '任务修改', 0, ' ', 67, '', 1, 'system:job:edit', '{"title":"任务修改"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (75, '任务删除', 0, ' ', 67, '', 1, 'system:job:remove', '{"title":"任务删除"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (76, '任务状态修改', 0, ' ', 67, '', 1, 'system:job:changeStatus', '{"title":"任务状态修改"}', 1, '', 0, NOW(), NULL, NULL, 0),
    (77, '任务执行', 0, ' ', 67, '', 1, 'system:job:run', '{"title":"任务执行"}', 1, '', 0, NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES
    (2, 66),
    (2, 67),
    (2, 68),
    (2, 69),
    (2, 70),
    (2, 71),
    (2, 72),
    (2, 73),
    (2, 74),
    (2, 75),
    (2, 76),
    (2, 77);
