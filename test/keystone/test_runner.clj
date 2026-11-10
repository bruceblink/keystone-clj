(ns keystone.test-runner
  (:require [clojure.test :as test]
            [keystone.config-test]
            [keystone.http-test]
            [keystone.security-test]))

(defn -main [& _args]
  (let [{:keys [fail error]} (test/run-tests 'keystone.config-test
                                            'keystone.http-test
                                            'keystone.security-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
