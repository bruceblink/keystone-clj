(ns keystone.dict.schema
  (:require [malli.core :as m]))

(def page-schema
  [:map
   [:pageNum [:int {:min 1}]]
   [:pageSize [:int {:min 1 :max 100}]]
   [:dictName {:optional true} string?]
   [:dictType {:optional true} string?]
   [:status {:optional true} [:enum 0 1]]])

(defn- read-positive-long [raw fallback name]
  (let [value (try
                (Long/parseLong (str (or raw fallback)))
                (catch NumberFormatException e
                  (throw (ex-info "Invalid integer"
                                  {:type :validation :field name} e))))]
    (when-not (pos? value)
      (throw (ex-info "Must be positive" {:type :validation :field name})))
    value))

(defn positive-id [value]
  (read-positive-long value nil "id"))

(defn page-query [params]
  (let [page (read-positive-long (get params "pageNum") 1 "pageNum")
        size (read-positive-long (get params "pageSize") 10 "pageSize")
        status-str (get params "status")
        status (when (some? status-str)
                 (try
                   (Long/parseLong (str status-str))
                   (catch NumberFormatException e
                     (throw (ex-info "Invalid status" {:type :validation} e)))))
        query (cond-> {:pageNum page :pageSize size}
                (some? status-str) (assoc :status status)
                (some? (get params "dictName")) (assoc :dictName (get params "dictName"))
                (some? (get params "dictType")) (assoc :dictType (get params "dictType")))]
    (when-not (m/validate page-schema query)
      (throw (ex-info "Invalid dictionary query" {:type :validation})))
    query))
