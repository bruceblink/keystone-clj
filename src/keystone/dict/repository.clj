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
        base (cond-> {:from [:sys_dict_type]} where (assoc :where where))
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
                          :where [:= :dict_type dict-type]
                          :order-by [[:dict_sort :asc] [:dict_code :asc]]})
          result (mapv row->data rows)]
      (when cache (.put ^Cache cache dict-type result))
      result)))

(defn make-repository [datasource cache]
  {:list-types #(list-types datasource %)
   :get-type #(get-type datasource %)
   :list-data-by-type #(list-data-by-type datasource cache %)})
