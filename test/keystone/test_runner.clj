(ns keystone.test-runner
  (:require [clojure.test :as test]
            [keystone.config-test]
            [keystone.http-test]
            [keystone.security-test]
            [keystone.auth-test]))

(defn -main [& _args]
  (let [{:keys [fail error]} (test/run-tests 'keystone.config-test
                                            'keystone.http-test
                                            'keystone.security-test
                                            'keystone.auth-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
