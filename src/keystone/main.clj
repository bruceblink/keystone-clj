(ns keystone.main
  (:require [integrant.core :as ig]
            [keystone.system :as system])
  (:gen-class))

(defn -main [& _args]
  (let [running (ig/init (system/system-config))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! running)))
    (println "Keystone Clojure HTTP server started")
    @(promise)))
