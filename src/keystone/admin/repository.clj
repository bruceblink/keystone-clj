(ns keystone.admin.repository
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def jdbc-options {:builder-fn rs/as-unqualified-lower-maps})

(defn- as-int [value]
  (cond
    (boolean? value) (if value 1 0)
    (nil? value) 0
    :else (long value)))

(defn- query [datasource sql params]
  (jdbc/execute! datasource (into [sql] params) jdbc-options))

(defn- first-row [datasource sql params]
  (first (query datasource sql params)))

(defn- execute! [datasource sql params]
  (jdbc/execute! datasource (into [sql] params) jdbc-options))

(defn- value [body key]
  (or (get body key) (get body (keyword key))))

(defn- required [body key]
  (let [result (value body key)]
    (when (or (nil? result) (and (string? result) (str/blank? result)))
      (throw (ex-info (str key " is required") {:type :validation})))
    result))

(defn- page [params]
  [(max 1 (Long/parseLong (str (or (get params "pageNum") 1))))
   (min 100 (max 1 (Long/parseLong (str (or (get params "pageSize") 10)))))])

(defn- json-array [value]
  (try
    (if (str/blank? value) [] (json/read-value value))
    (catch Exception _ [])))

(defn- config->dto [row]
  {:configId (:config_id row)
   :configName (:config_name row)
   :configKey (:config_key row)
   :configOptions (json-array (:config_options row))
   :configValue (:config_value row)
   :isAllowChange (:is_allow_change row)
   :isAllowChangeStr (if (= 1 (as-int (:is_allow_change row))) "是" "否")
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(def config-columns
  "config_id, config_name, config_key, config_options, config_value,
   is_allow_change, remark, create_time")

(defn list-configs [datasource params]
  (let [[page size] (page params)
        conditions (cond-> ["1 = 1"]
                     (not (str/blank? (get params "configName"))) (conj "config_name LIKE ?")
                     (not (str/blank? (get params "configKey"))) (conj "config_key LIKE ?")
                     (some? (get params "isAllowChange")) (conj "is_allow_change = ?"))
        values (cond-> []
                 (not (str/blank? (get params "configName")))
                 (conj (str "%" (get params "configName") "%"))
                 (not (str/blank? (get params "configKey")))
                 (conj (str "%" (get params "configKey") "%"))
                 (some? (get params "isAllowChange")) (conj (get params "isAllowChange")))
        where (str/join " AND " conditions)
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_config WHERE " where) values))
        rows (query datasource
                     (str "SELECT " config-columns " FROM sys_config WHERE " where
                          " ORDER BY config_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv config->dto rows)}))

(defn get-config [datasource id]
  (some-> (first-row datasource (str "SELECT " config-columns
                                     " FROM sys_config WHERE config_id = ? AND 1 = 1") [id])
          config->dto))

(defn add-config! [datasource body]
  (execute! datasource
            "INSERT INTO sys_config
             (config_name, config_key, config_options, config_value, is_allow_change,
              remark, create_time)
             VALUES (?, ?, ?, ?, ?, ?, NOW())"
            [(required body "configName") (required body "configKey")
             (json/write-value-as-string (or (value body "configOptions") []))
             (required body "configValue") (or (value body "isAllowChange") 1)
             (or (value body "remark") "")])
  nil)

(defn update-config! [datasource id body]
  (execute! datasource
            "UPDATE sys_config SET config_value = ?, update_time = NOW()
               WHERE config_id = ? AND 1 = 1"
            [(required body "configValue") id])
  nil)

(defn delete-config-cache! [] nil)

(defn- notice->dto [row]
  {:noticeId (:notice_id row)
   :noticeTitle (:notice_title row)
   :noticeType (:notice_type row)
   :noticeContent (:notice_content row)
   :status (:status row)
   :creatorName (:creator_name row)
   :createTime (some-> (:create_time row) str)})

(def notice-select
  "SELECT n.notice_id, n.notice_title, n.notice_type, n.notice_content,
          n.status, n.create_time, u.nickname AS creator_name
     FROM sys_notice n
     LEFT JOIN sys_user u ON u.user_id = n.creator_id
    WHERE 1 = 1")

(defn list-notices [datasource params]
  (let [[page size] (page params)
        conditions (cond-> []
                     (not (str/blank? (get params "noticeTitle"))) (conj "n.notice_title LIKE ?")
                     (some? (get params "noticeType")) (conj "n.notice_type = ?"))
        values (cond-> []
                 (not (str/blank? (get params "noticeTitle")))
                 (conj (str "%" (get params "noticeTitle") "%"))
                 (some? (get params "noticeType")) (conj (get params "noticeType")))
        suffix (if (seq conditions) (str " AND " (str/join " AND " conditions)) "")
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_notice n WHERE 1 = 1" suffix) values))
        rows (query datasource
                     (str notice-select suffix " ORDER BY n.notice_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv notice->dto rows)}))

(defn get-notice [datasource id]
  (some-> (first-row datasource (str notice-select " AND n.notice_id = ?") [id]) notice->dto))

(defn add-notice! [datasource body creator-id]
  (execute! datasource
            "INSERT INTO sys_notice
             (notice_title, notice_type, notice_content, status, creator_id, create_time)
             VALUES (?, ?, ?, ?, ?, NOW())"
            [(required body "noticeTitle") (or (value body "noticeType") 1)
             (or (value body "noticeContent") "") (or (value body "status") 1) creator-id])
  nil)

(defn update-notice! [datasource id body]
  (execute! datasource
            "UPDATE sys_notice SET notice_title = ?, notice_type = ?, notice_content = ?,
                    status = ?, update_time = NOW() WHERE notice_id = ? AND 1 = 1"
            [(required body "noticeTitle") (or (value body "noticeType") 1)
             (or (value body "noticeContent") "") (or (value body "status") 1) id])
  nil)

(defn delete-notices! [datasource ids]
  (let [ids (->> (str/split (str (or ids "")) #",") (remove str/blank?)
                 (map #(Long/parseLong %)) distinct vec)]
    (when (empty? ids) (throw (ex-info "noticeIds is required" {:type :validation})))
    (execute! datasource
              (str "DELETE FROM sys_notice WHERE notice_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids))
  nil)

(defn- login-log->dto [row]
  {:logId (:info_id row) :username (:username row) :ipAddress (:ip_address row)
   :loginLocation (:login_location row) :browser (:browser row)
   :operationSystem (:operation_system row) :status (:status row)
   :statusStr (if (= 1 (as-int (:status row))) "成功" "失败")
   :msg (:msg row) :loginTime (some-> (:login_time row) str)})

(defn list-login-logs [datasource params]
  (let [[page size] (page params)
        conditions (cond-> ["1 = 1"]
                     (not (str/blank? (get params "username"))) (conj "username LIKE ?")
                     (not (str/blank? (get params "ipAddress"))) (conj "ip_address LIKE ?")
                     (some? (get params "status")) (conj "status = ?"))
        values (cond-> []
                 (not (str/blank? (get params "username"))) (conj (str "%" (get params "username") "%"))
                 (not (str/blank? (get params "ipAddress"))) (conj (str "%" (get params "ipAddress") "%"))
                 (some? (get params "status")) (conj (get params "status")))
        where (str/join " AND " conditions)
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_login_info WHERE " where) values))
        rows (query datasource
                     (str "SELECT info_id, username, ip_address, login_location, browser,
                                  operation_system, status, msg, login_time
                             FROM sys_login_info WHERE " where
                          " ORDER BY info_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv login-log->dto rows)}))

(defn delete-login-logs! [datasource ids]
  (let [ids (->> (str/split (str (or ids "")) #",") (remove str/blank?)
                 (map #(Long/parseLong %)) distinct vec)]
    (when (empty? ids) (throw (ex-info "ids is required" {:type :validation})))
    (execute! datasource
              (str "DELETE FROM sys_login_info WHERE info_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids))
  nil)

(defn- operation-log->dto [row]
  {:operationId (:operation_id row) :businessType (:business_type row)
   :businessTypeStr (str (:business_type row)) :requestMethod (:request_method row)
   :requestModule (:request_module row) :requestUrl (:request_url row)
   :calledMethod (:called_method row) :operatorType (:operator_type row)
   :operatorTypeStr (str (:operator_type row)) :userId (:user_id row)
   :username (:username row) :operatorIp (:operator_ip row) :deptId (:dept_id row)
   :deptName (:dept_name row) :operationParam (:operation_param row)
   :operationResult (:operation_result row) :status (:status row)
   :statusStr (if (= 1 (as-int (:status row))) "正常" "异常")
   :errorStack (:error_stack row) :operationTime (some-> (:operation_time row) str)})

(defn list-operation-logs [datasource params]
  (let [[page size] (page params)
        conditions (cond-> ["1 = 1"]
                     (not (str/blank? (get params "requestModule"))) (conj "request_module LIKE ?")
                     (not (str/blank? (get params "username"))) (conj "username LIKE ?")
                     (some? (get params "status")) (conj "status = ?"))
        values (cond-> []
                 (not (str/blank? (get params "requestModule"))) (conj (str "%" (get params "requestModule") "%"))
                 (not (str/blank? (get params "username"))) (conj (str "%" (get params "username") "%"))
                 (some? (get params "status")) (conj (get params "status")))
        where (str/join " AND " conditions)
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_operation_log WHERE " where) values))
        rows (query datasource
                     (str "SELECT operation_id, business_type, request_method, request_module,
                                  request_url, called_method, operator_type, user_id, username,
                                  operator_ip, dept_id, dept_name, operation_param, operation_result,
                                  status, error_stack, operation_time
                             FROM sys_operation_log WHERE " where
                          " ORDER BY operation_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv operation-log->dto rows)}))

(defn delete-operation-logs! [datasource ids]
  (let [ids (->> (str/split (str (or ids "")) #",") (remove str/blank?)
                 (map #(Long/parseLong %)) distinct vec)]
    (when (empty? ids) (throw (ex-info "operationIds is required" {:type :validation})))
    (execute! datasource
              (str "DELETE FROM sys_operation_log WHERE operation_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids))
  nil)

(defn- job->dto [row]
  {:jobId (:job_id row) :jobName (:job_name row) :jobGroup (:job_group row)
   :invokeTarget (:invoke_target row) :jobParams (:job_params row)
   :cronExpression (:cron_expression row) :concurrent (:concurrent row)
   :concurrentStr (if (= 1 (as-int (:concurrent row))) "允许" "禁止")
   :status (:status row) :statusStr (if (= 1 (as-int (:status row))) "正常" "暂停")
   :remark (:remark row) :createTime (some-> (:create_time row) str)})

(defn list-jobs [datasource params]
  (let [[page size] (page params)
        conditions (cond-> ["1 = 1"]
                     (not (str/blank? (get params "jobName"))) (conj "job_name LIKE ?")
                     (not (str/blank? (get params "jobGroup"))) (conj "job_group LIKE ?")
                     (some? (get params "status")) (conj "status = ?"))
        values (cond-> []
                 (not (str/blank? (get params "jobName"))) (conj (str "%" (get params "jobName") "%"))
                 (not (str/blank? (get params "jobGroup"))) (conj (str "%" (get params "jobGroup") "%"))
                 (some? (get params "status")) (conj (get params "status")))
        where (str/join " AND " conditions)
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_job WHERE " where) values))
        rows (query datasource
                     (str "SELECT job_id, job_name, job_group, invoke_target, job_params,
                                  cron_expression, concurrent, status, remark, create_time
                             FROM sys_job WHERE " where
                          " ORDER BY job_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv job->dto rows)}))

(defn get-job [datasource id]
  (some-> (first-row datasource
                     "SELECT job_id, job_name, job_group, invoke_target, job_params,
                             cron_expression, concurrent, status, remark, create_time
                        FROM sys_job WHERE job_id = ? AND 1 = 1"
                     [id]) job->dto))

(defn add-job! [datasource body]
  (execute! datasource
            "INSERT INTO sys_job
             (job_name, job_group, invoke_target, job_params, cron_expression, concurrent,
              status, remark, create_time)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())"
            [(required body "jobName") (required body "jobGroup") (required body "invokeTarget")
             (or (value body "jobParams") "") (required body "cronExpression")
             (or (value body "concurrent") 0) (or (value body "status") 0)
             (or (value body "remark") "")])
  nil)

(defn update-job! [datasource id body]
  (execute! datasource
            "UPDATE sys_job SET job_name = ?, job_group = ?, invoke_target = ?, job_params = ?,
                    cron_expression = ?, concurrent = ?, status = ?, remark = ?, update_time = NOW()
               WHERE job_id = ? AND 1 = 1"
            [(required body "jobName") (required body "jobGroup") (required body "invokeTarget")
             (or (value body "jobParams") "") (required body "cronExpression")
             (or (value body "concurrent") 0) (or (value body "status") 0)
             (or (value body "remark") "") id])
  nil)

(defn update-job-status! [datasource id body]
  (execute! datasource "UPDATE sys_job SET status = ?, update_time = NOW() WHERE job_id = ? AND 1 = 1"
            [(or (value body "status") 0) id])
  nil)

(defn delete-jobs! [datasource ids]
  (let [ids (->> (str/split (str (or ids "")) #",") (remove str/blank?)
                 (map #(Long/parseLong %)) distinct vec)]
    (when (empty? ids) (throw (ex-info "jobIds is required" {:type :validation})))
    (execute! datasource
              (str "DELETE FROM sys_job WHERE job_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids))
  nil)

(defn list-job-logs [datasource params]
  (let [[page size] (page params)
        conditions ["1 = 1"]
        values []
        where (str/join " AND " conditions)
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_job_log WHERE " where) values))
        rows (query datasource
                     (str "SELECT job_log_id, job_id, job_name, job_group, invoke_target, job_params,
                                  cron_expression, trigger_type, status, job_message, exception_info,
                                  start_time, end_time, duration_ms, create_time
                             FROM sys_job_log WHERE " where
                          " ORDER BY job_log_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0)
     :rows (mapv (fn [row]
                   {:jobLogId (:job_log_id row) :jobId (:job_id row)
                    :jobName (:job_name row) :jobGroup (:job_group row)
                    :invokeTarget (:invoke_target row) :jobParams (:job_params row)
                    :cronExpression (:cron_expression row) :triggerType (:trigger_type row)
                    :status (:status row) :jobMessage (:job_message row)
                    :exceptionInfo (:exception_info row) :startTime (some-> (:start_time row) str)
                    :endTime (some-> (:end_time row) str) :durationMs (:duration_ms row)
                    :createTime (some-> (:create_time row) str)}) rows)}))

(defn make-repository [datasource]
  {:list-configs #(list-configs datasource %)
   :get-config #(get-config datasource %)
   :add-config! #(add-config! datasource %)
   :update-config! (fn [[id body]] (update-config! datasource id body))
   :delete-config-cache! #(delete-config-cache!)
   :list-notices #(list-notices datasource %)
   :get-notice #(get-notice datasource %)
   :add-notice! (fn [body creator-id] (add-notice! datasource body creator-id))
   :update-notice! (fn [[id body]] (update-notice! datasource id body))
   :delete-notices! #(delete-notices! datasource %)
   :list-login-logs #(list-login-logs datasource %)
   :delete-login-logs! #(delete-login-logs! datasource %)
   :list-operation-logs #(list-operation-logs datasource %)
   :delete-operation-logs! #(delete-operation-logs! datasource %)
   :list-jobs #(list-jobs datasource %)
   :get-job #(get-job datasource %)
   :add-job! #(add-job! datasource %)
   :update-job! (fn [[id body]] (update-job! datasource id body))
   :update-job-status! (fn [[id body]] (update-job-status! datasource id body))
   :delete-jobs! #(delete-jobs! datasource %)
   :list-job-logs #(list-job-logs datasource %)})
