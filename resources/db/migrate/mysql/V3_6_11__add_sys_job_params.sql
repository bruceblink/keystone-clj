alter table sys_job
    add column job_params text null comment '任务参数JSON' after invoke_target;

alter table sys_job_log
    add column job_params text null comment '任务参数JSON快照' after invoke_target;
