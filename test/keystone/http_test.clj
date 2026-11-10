(ns keystone.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [keystone.auth :as auth]
            [keystone.http :as http]))

(defn- request-body [body]
  (java.io.ByteArrayInputStream.
   (.getBytes (json/write-value-as-string body) "UTF-8")))

(defn invoke
  ([handler uri] (invoke handler uri nil))
  ([handler uri token]
   (invoke handler :get uri token nil))
  ([handler method uri token body]
   (let [headers (cond-> {} token (assoc "authorization" (str "Bearer " token)))
         request (cond-> {:request-method method
                          :uri uri
                          :query-string nil
                          :headers headers}
                   body (assoc :body (request-body body)
                               :headers (assoc headers "content-type" "application/json")))
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

(def auth-user
  {:user-id 42
   :username "admin"
   :nickname "管理员"
   :password (org.mindrot.jbcrypt.BCrypt/hashpw "secret"
                                                 (org.mindrot.jbcrypt.BCrypt/gensalt 4))
   :status 1
   :role-key "admin"
   :permissions #{"system:dict:list"}})

(defn make-full-auth-handler []
  (let [generator (java.security.KeyPairGenerator/getInstance "RSA")]
    (.initialize generator 2048)
    (let [pair (.generateKeyPair generator)
          service (auth/make-service
                   {:key-pair pair
                    :session-store (auth/make-memory-session-store)
                    :find-user (fn [username] (when (= username "admin") auth-user))})
          user-repository {:find-by-id (fn [_] auth-user)
                           :current-user (fn [user]
                                           {:userInfo (dissoc user :password :permissions :role-key)
                                            :roleKey (:role-key user)
                                            :permissions (vec (:permissions user))})
                           :menu-tree (fn [_] [])}]
      (http/make-handler
       {:dict-repository sample-repo
        :user-repository user-repository
        :auth {:service service :key-pair pair :captcha-enabled? false}}))))

(defn make-management-handler []
  (http/make-handler
   {:dict-repository sample-repo
    :user-repository
    {:list-users (fn [_] {:total 1 :rows [{:userId 42 :username "admin"}]})
     :get-user-detail (fn [id] (when (= 42 id) {:userId 42 :username "admin"}))
     :list-roles (fn [_] {:total 1 :rows [{:roleId 1 :roleKey "admin"}]})
     :get-role (fn [id] (when (= 1 id) {:roleId 1 :roleKey "admin"}))
     :list-menu-management (fn [_] [{:id 1 :menuName "系统管理"}])
     :get-menu (fn [id] (when (= 1 id) {:id 1 :menuName "系统管理"}))
     :menu-dropdown (fn [] [{:id 1 :label "系统管理"}])
     :list-depts (fn [_] [{:id 1 :deptName "总部"}])
     :get-dept (fn [id] (when (= 1 id) {:id 1 :deptName "总部"}))
     :dept-dropdown (fn [] [{:id 1 :label "总部"}])
     :list-posts (fn [_] {:total 1 :rows [{:postId 1 :postCode "ceo"}]})
     :get-post (fn [id] (when (= 1 id) {:postId 1 :postCode "ceo"}))}
    :authenticator (fn [_]
                     {:user-id 42
                      :permissions #{"*"}})}))

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

(deftest login-refresh-and-current-user-contract
  (let [handler (make-full-auth-handler)
        key-res (invoke handler "/login/rsa-public-key")
        login-res (invoke handler :post "/login" nil
                          {"username" "admin" "password" "secret"})
        token (get-in login-res [:json "data" "token"])
        user-res (invoke handler "/getLoginUserInfo" token)
        refresh-res (invoke handler :post "/refresh-token" nil
                            {"refreshToken" (get-in login-res [:json "data" "refreshToken"])})]
    (is (= 200 (:status key-res)))
    (is (string? (get-in key-res [:json "data" "publicKey"])))
    (is (= 0 (get-in login-res [:json "code"])))
    (is (string? token))
    (is (= "admin" (get-in user-res [:json "data" "userInfo" "username"])))
    (is (= 0 (get-in refresh-res [:json "code"])))) )

(deftest management-read-routes-contract
  (let [handler (make-management-handler)
        users (invoke handler "/system/users" "test-token")
        user (invoke handler "/system/users/42" "test-token")
        role (invoke handler "/system/role/1" "test-token")
        menu-dropdown (invoke handler "/system/menus/dropdown" "test-token")
        dept (invoke handler "/system/dept/1" "test-token")
        posts (invoke handler "/system/post/list" "test-token")]
    (is (= 1 (get-in users [:json "data" "total"])))
    (is (= "admin" (get-in user [:json "data" "username"])))
    (is (= "admin" (get-in role [:json "data" "roleKey"])))
    (is (= "系统管理" (get-in menu-dropdown [:json "data" 0 "label"])))
    (is (= "总部" (get-in dept [:json "data" "deptName"])))
    (is (= "ceo" (get-in posts [:json "data" "rows" 0 "postCode"])))) )
