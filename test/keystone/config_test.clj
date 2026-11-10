(ns keystone.config-test
  (:require [clojure.test :refer [deftest is]]
            [keystone.dict.schema :as schema]))

(deftest page-query-defaults-and-validation
  (is (= {:pageNum 1 :pageSize 10} (schema/page-query {})))
  (is (= {:pageNum 3 :pageSize 25 :status 1}
         (schema/page-query {"pageNum" "3" "pageSize" "25" "status" "1"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/page-query {"pageSize" "101"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/page-query {"status" "2"}))))

(deftest id-validation
  (is (= 15 (schema/positive-id "15")))
  (is (thrown? clojure.lang.ExceptionInfo (schema/positive-id "-3"))))
