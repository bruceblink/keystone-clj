(ns keystone.response
  (:require [jsonista.core :as json]))

(defn reply [status code msg data]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"}
   :body (json/write-value-as-string {:code code :msg msg :data data})})

(defn ok
  ([] (ok nil))
  ([data] (reply 200 0 "操作成功" data)))

(defn fail [status code msg]
  (reply status code msg nil))

(defn validation-error [message]
  (fail 400 1 (str "参数异常：" message)))

(defn unavailable []
  (fail 503 99999 "所需基础设施尚未启用"))

(defn not-found [entity id]
  (fail 404 10001 (str "找不到ID为 " id " 的 " entity)))
