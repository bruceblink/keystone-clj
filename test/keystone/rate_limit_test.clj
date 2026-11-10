(ns keystone.rate-limit-test
  (:require [clojure.test :refer [deftest is]]
            [keystone.rate-limit :as rate]))

(deftest enforces-window-and-limit
  (let [limiter (rate/make-limiter)]
    (is (rate/allowed? limiter "client" 10000 2))
    (is (rate/allowed? limiter "client" 10000 2))
    (is (not (rate/allowed? limiter "client" 10000 2)))
    (is (rate/allowed? limiter "other" 10000 2))))
