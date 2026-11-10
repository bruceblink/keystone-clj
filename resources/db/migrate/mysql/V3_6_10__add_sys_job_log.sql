create table if not exists sys_job_log
(
    job_log_id     bigint auto_increment comment '任务日志ID'
        primary key,
    job_id         bigint                  null comment '任务ID',
    job_name       varchar(64)  default '' not null comment '任务名称',
    job_group      varchar(64)  default '' not null comment '任务组名',
    invoke_target  varchar(255) default '' not null comment '调用目标',
    cron_expression varchar(255)           null comment 'Cron执行表达式',
    trigger_type   tinyint      default 1  not null comment '触发类型（1自动调度 2手动执行）',
    status         tinyint      default 1  not null comment '执行状态（1成功 0失败 2跳过）',
    job_message    varchar(500)            null comment '日志信息',
    exception_info varchar(2000)           null comment '异常信息',
    start_time     datetime                not null comment '开始时间',
    end_time       datetime                null comment '结束时间',
    duration_ms    bigint                  null comment '耗时毫秒',
    create_time    datetime default current_timestamp not null comment '创建时间',
    index idx_job_log_job_id (job_id),
    index idx_job_log_start_time (start_time),
    index idx_job_log_status (status)
) comment '定时任务运行日志表';
