(ns keystone.admin.repository
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def jdbc-options {:builder-fn rs/as-unqualified-lower-maps})

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
   :isAllowChangeStr (if (= 1 (long (or (:is_allow_change row) 0))) "是" "否")
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(def config-columns
  "config_id, config_name, config_key, config_options, config_value,
   is_allow_change, remark, create_time")

(defn list-configs [datasource params]
  (let [[page size] (page params)
        conditions (cond-> ["deleted = 0"]
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
                                     " FROM sys_config WHERE config_id = ? AND deleted = 0") [id])
          config->dto))

(defn add-config! [datasource body]
  (execute! datasource
            "INSERT INTO sys_config
             (config_name, config_key, config_options, config_value, is_allow_change,
              remark, create_time, deleted)
             VALUES (?, ?, ?, ?, ?, ?, NOW(), 0)"
            [(required body "configName") (required body "configKey")
             (json/write-value-as-string (or (value body "configOptions") []))
             (required body "configValue") (or (value body "isAllowChange") 1)
             (or (value body "remark") "")])
  nil)

(defn update-config! [datasource id body]
  (execute! datasource
            "UPDATE sys_config SET config_value = ?, update_time = NOW()
               WHERE config_id = ? AND deleted = 0"
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
    WHERE n.deleted = 0")

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
        total (:total (first-row datasource (str "SELECT COUNT(*) AS total FROM sys_notice n WHERE n.deleted = 0" suffix) values))
        rows (query datasource
                     (str notice-select suffix " ORDER BY n.notice_id DESC LIMIT ? OFFSET ?")
                     (into values [size (* (dec page) size)]))]
    {:total (or total 0) :rows (mapv notice->dto rows)}))

(defn get-notice [datasource id]
  (some-> (first-row datasource (str notice-select " AND n.notice_id = ?") [id]) notice->dto))

(defn add-notice! [datasource body creator-id]
  (execute! datasource
            "INSERT INTO sys_notice
             (notice_title, notice_type, notice_content, status, creator_id, create_time, deleted)
             VALUES (?, ?, ?, ?, ?, NOW(), 0)"
            [(required body "noticeTitle") (or (value body "noticeType") 1)
             (or (value body "noticeContent") "") (or (value body "status") 1) creator-id])
  nil)

(defn update-notice! [datasource id body]
  (execute! datasource
            "UPDATE sys_notice SET notice_title = ?, notice_type = ?, notice_content = ?,
                    status = ?, update_time = NOW() WHERE notice_id = ? AND deleted = 0"
            [(required body "noticeTitle") (or (value body "noticeType") 1)
             (or (value body "noticeContent") "") (or (value body "status") 1) id])
  nil)

(defn delete-notices! [datasource ids]
  (let [ids (->> (str/split (str (or ids "")) #",") (remove str/blank?)
                 (map #(Long/parseLong %)) distinct vec)]
    (when (empty? ids) (throw (ex-info "noticeIds is required" {:type :validation})))
    (execute! datasource
              (str "UPDATE sys_notice SET deleted = 1, update_time = NOW() WHERE notice_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids))
  nil)

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
   :delete-notices! #(delete-notices! datasource %)})
