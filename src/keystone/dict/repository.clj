(ns keystone.dict.repository
  (:require [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [com.github.benmanes.caffeine.cache Cache]))

(def jdbc-options {:builder-fn rs/as-unqualified-lower-maps})

(defn execute! [datasource query]
  (jdbc/execute! datasource (sql/format query) jdbc-options))

(defn row->type [row]
  {:dictId (:dict_id row)
   :dictName (:dict_name row)
   :dictType (:dict_type row)
   :status (:status row)
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(defn row->data [row]
  {:dictCode (:dict_code row)
   :dictType (:dict_type row)
   :dictLabel (:dict_label row)
   :dictValue (:dict_value row)
   :dictSort (:dict_sort row)
   :isDefault (:is_default row)
   :cssClass (:css_class row)
   :listClass (:list_class row)
   :status (:status row)
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(defn- filters [{:keys [dictName dictType status]}]
  (let [conditions (cond-> []
                     (not (str/blank? dictName))
                     (conj [:like :dict_name (str "%" dictName "%")])
                     (not (str/blank? dictType))
                     (conj [:like :dict_type (str "%" dictType "%")])
                     (some? status)
                     (conj [:= :status status]))]
    (when (seq conditions) (into [:and] conditions))))

(defn list-types [datasource query]
  (let [{:keys [pageNum pageSize]} query
        where (filters query)
        base (cond-> {:from [:sys_dict_type]}
               where (assoc :where where))
        total (-> (execute! datasource (assoc base :select [[[:count :*] :total]]))
                  first :total)
        rows (execute! datasource
                       (assoc base
                              :select [:dict_id :dict_name :dict_type :status
                                       :remark :create_time]
                              :order-by [[:dict_id :asc]]
                              :limit pageSize
                              :offset (* (dec pageNum) pageSize)))]
    {:total (or total 0)
     :rows (mapv row->type rows)}))

(defn get-type [datasource id]
  (some-> (execute! datasource
                    {:select [:dict_id :dict_name :dict_type :status
                              :remark :create_time]
                     :from [:sys_dict_type]
                    :where [:= :dict_id id]
                     :limit 1})
          first row->type))

(defn list-data-by-type [datasource cache dict-type]
  (if-let [cached (when cache (.getIfPresent ^Cache cache dict-type))]
    cached
    (let [rows (execute! datasource
                         {:select [:dict_code :dict_type :dict_label :dict_value
                                   :dict_sort :is_default :css_class :list_class
                                   :status :remark :create_time]
                          :from [:sys_dict_data]
                          :where [:and [:= :dict_type dict-type] [:= :status 1]]
                          :order-by [[:dict_sort :asc] [:dict_code :asc]]})
          result (mapv row->data rows)]
      (when cache (.put ^Cache cache dict-type result))
      result)))

(defn- body-value [body key]
  (or (get body key) (get body (keyword key))))

(defn- required-body [body key]
  (let [value (body-value body key)]
    (when (or (nil? value) (and (string? value) (str/blank? value)))
      (throw (ex-info (str key " is required") {:type :validation})))
    value))

(defn list-data [datasource query]
  (let [page (max 1 (Long/parseLong (str (or (get query "pageNum") 1))))
        size (min 100 (max 1 (Long/parseLong (str (or (get query "pageSize") 10)))))
        conditions (cond-> []
                     (not (str/blank? (get query "dictType")))
                     (conj [:like :dict_type (str "%" (get query "dictType") "%")])
                     (not (str/blank? (get query "dictLabel")))
                     (conj [:like :dict_label (str "%" (get query "dictLabel") "%")])
                     (some? (get query "status"))
                     (conj [:= :status (Long/parseLong (str (get query "status")))]))
        where (when (seq conditions) (into [:and] conditions))
        base (cond-> {:from [:sys_dict_data]}
               where (assoc :where where))
        count (first (execute! datasource (assoc base :select [[[:count :*] :total]])))
        rows (execute! datasource (assoc base
                                         :select [:dict_code :dict_type :dict_label :dict_value
                                                  :dict_sort :is_default :css_class :list_class
                                                  :status :remark :create_time]
                                         :order-by [[:dict_sort :asc] [:dict_code :asc]]
                                         :limit size
                                         :offset (* (dec page) size)))]
    {:total (or (:total count) 0)
     :rows (mapv row->data rows)}))

(defn- invalidate-cache! [cache]
  (when cache (.invalidateAll ^Cache cache)))

(defn add-type! [datasource body]
  (execute! datasource
            {:insert-into :sys_dict_type
             :columns [:dict_name :dict_type :status :remark :create_time]
             :values [[(required-body body "dictName") (required-body body "dictType")
                       (or (body-value body "status") 1) (or (body-value body "remark") "")
                       [:now]]]})
  nil)

(defn update-type! [datasource id body]
  (execute! datasource
            {:update :sys_dict_type
             :set {:dict_name (required-body body "dictName")
                   :dict_type (required-body body "dictType")
                   :status (or (body-value body "status") 1)
                   :remark (or (body-value body "remark") "")
                   :update_time [:now]}
             :where [:= :dict_id id]})
  nil)

(defn delete-type! [datasource id]
  (let [dict-type (:dict_type (first (execute! datasource
                                               {:select [:dict_type]
                                                :from [:sys_dict_type]
                                                :where [:= :dict_id id]
                                                :limit 1})))
        count (if dict-type
                (first (execute! datasource
                                 {:select [[[:count :*] :total]]
                                  :from [:sys_dict_data]
                                  :where [:= :dict_type dict-type]}))
                {:total 0})]
    (when (pos? (long (or (:total count) 0)))
      (throw (ex-info "字典类型存在字典数据，不能删除" {:type :conflict :code 99999})))
    (execute! datasource {:delete-from :sys_dict_type
                          :where [:= :dict_id id]}))
  nil)

(defn add-data! [datasource cache body]
  (execute! datasource
            {:insert-into :sys_dict_data
             :columns [:dict_type :dict_label :dict_value :dict_sort :is_default
                       :css_class :list_class :status :remark :create_time]
             :values [[(required-body body "dictType") (required-body body "dictLabel")
                       (required-body body "dictValue") (or (body-value body "dictSort") 0)
                       (or (body-value body "isDefault") 0) (or (body-value body "cssClass") "")
                       (or (body-value body "listClass") "") (or (body-value body "status") 1)
                       (or (body-value body "remark") "") [:now]]]})
  (invalidate-cache! cache)
  nil)

(defn get-data [datasource id]
  (some-> (execute! datasource {:select [:dict_code :dict_type :dict_label :dict_value
                                         :dict_sort :is_default :css_class :list_class
                                         :status :remark :create_time]
                                :from [:sys_dict_data]
                                :where [:= :dict_code id]
                                :limit 1}) first row->data))

(defn update-data! [datasource cache id body]
  (execute! datasource
            {:update :sys_dict_data
             :set {:dict_type (required-body body "dictType")
                   :dict_label (required-body body "dictLabel")
                   :dict_value (required-body body "dictValue")
                   :dict_sort (or (body-value body "dictSort") 0)
                   :is_default (or (body-value body "isDefault") 0)
                   :css_class (or (body-value body "cssClass") "")
                   :list_class (or (body-value body "listClass") "")
                   :status (or (body-value body "status") 1)
                   :remark (or (body-value body "remark") "")
                   :update_time [:now]}
             :where [:= :dict_code id]})
  (invalidate-cache! cache)
  nil)

(defn delete-data! [datasource cache id]
  (execute! datasource {:delete-from :sys_dict_data
                        :where [:= :dict_code id]})
  (invalidate-cache! cache)
  nil)

(defn make-repository [datasource cache]
  {:list-types #(list-types datasource %)
   :get-type #(get-type datasource %)
   :list-data-by-type #(list-data-by-type datasource cache %)
   :list-data #(list-data datasource %)
   :get-data #(get-data datasource %)
   :add-type! #(add-type! datasource %)
   :update-type! (fn [[id body]] (update-type! datasource id body))
   :delete-type! #(delete-type! datasource %)
   :add-data! #(add-data! datasource cache %)
   :update-data! (fn [[id body]] (update-data! datasource cache id body))
   :delete-data! #(delete-data! datasource cache %)})
