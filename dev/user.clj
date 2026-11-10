(ns user
  (:require [integrant.core :as ig]
            [keystone.system :as system]))

(defonce running (atom nil))

(defn start []
  (when-not @running
    (reset! running (ig/init (system/system-config))))
  :started)

(defn stop []
  (when-let [instance @running]
    (ig/halt! instance)
    (reset! running nil))
  :stopped)

(defn restart []
  (stop)
  (start))
