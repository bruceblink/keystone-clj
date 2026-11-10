(ns keystone.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [keystone.http :as http]))

(defn invoke
  ([handler uri] (invoke handler uri nil))
  ([handler uri token]
   (let [request {:request-method :get
                  :uri uri
                  :query-string nil
                  :headers (cond-> {} token (assoc "authorization" (str "Bearer " token)))}
         result (handler request)]
     (assoc result :json (json/read-value (:body result))))))

(def sample-repo
  {:list-types (fn [{:keys [pageNum pageSize]}]
                 {:total 1
                  :rows [{:dictId 1 :dictName "状态" :dictType "status" :status 1}]
                  :query [pageNum pageSize]})
   :get-type (fn [id] (when (= 1 id) {:dictId 1 :dictType "status"}))
   :list-data-by-type (fn [_] [])})

(defn make-auth-handler [permissions]
  (http/make-handler
   {:dict-repository sample-repo
    :authenticator (fn [token]
                     (when (= "test-token" token)
                       {:user-id 42 :permissions permissions}))}))

(deftest health-contract
  (let [res (invoke (http/make-handler {}) "/health")]
    (is (= 200 (:status res)))
    (is (= 0 (get-in res [:json "code"])))
    (is (= "操作成功" (get-in res [:json "msg"])))
    (is (= "is alive" (get-in res [:json "data"])))))

(deftest protected-route-is-deny-by-default
  (let [handler (http/make-handler {:dict-repository sample-repo})
        res (invoke handler "/system/dict/types")]
    (is (= 401 (:status res)))
    (is (= 106 (get-in res [:json "code"])))))

(deftest authenticated-user-must-have-permission
  (let [res (invoke (make-auth-handler #{}) "/system/dict/types" "test-token")]
    (is (= 403 (:status res)))
    (is (= 10202 (get-in res [:json "code"])))))

(deftest query-and-response-contract
  (let [handler (make-auth-handler #{"system:dict:list" "system:dict:query"})
        res (invoke handler "/system/dict/type/list" "test-token")
        types-res (invoke handler "/system/dict/types" "test-token")
        detail (invoke handler "/system/dict/type/1" "test-token")]
    (is (= 200 (:status res)))
    (is (= 1 (get-in res [:json "data" "total"])))
    (is (= 1 (get-in res [:json "data" "rows" 0 "dictId"])))
    (is (= (get-in res [:json "data" "rows"])
           (get-in types-res [:json "data" "rows"])))
    (is (= "status" (get-in detail [:json "data" "dictType"])))))

(deftest no-database-is-not-a-fake-success
  (let [handler (http/make-handler
                 {:authenticator (fn [_] {:permissions #{"system:dict:list"}})})
        res (invoke handler "/system/dict/types" "any-token")]
    (is (= 503 (:status res)))))

(deftest invalid-id-is-rejected
  (let [res (invoke (make-auth-handler #{"system:dict:query"})
                    "/system/dict/type/abc" "test-token")]
    (is (= 400 (:status res)))))
