(ns keystone.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [keystone.auth :as auth]
            [keystone.security :as security])
  (:import [java.security KeyPairGenerator]
           [org.mindrot.jbcrypt BCrypt]))

(defn- test-key-pair []
  (let [generator (KeyPairGenerator/getInstance "RSA")]
    (.initialize generator 2048)
    (.generateKeyPair generator)))

(defn- service-fixture []
  (let [pair (test-key-pair)
        store (auth/make-memory-session-store)
        user {:user-id 7
              :username "admin"
              :nickname "Administrator"
              :password (BCrypt/hashpw "secret" (BCrypt/gensalt 4))
              :status 1
              :role-key "admin"
              :permissions #{"system:dict:list"}}
        service (auth/make-service
                 {:key-pair pair
                  :session-store store
                  :find-user (fn [username] (when (= username "admin") user))})]
    {:service service :store store :user user}))

(deftest login-refresh-and-revocation
  (let [{:keys [service]} (service-fixture)
        issued ((:login! service) {"username" "admin" "password" "secret"})
        principal ((:authenticate service) (:token issued))
        refreshed ((:refresh! service) (:refreshToken issued))]
    (is (string? (:token issued)))
    (is (string? (:refreshToken issued)))
    (is (= "7" (:user-id principal)))
    (is (contains? (:permissions principal) "system:dict:list"))
    (is (not= (:token issued) (:token refreshed)))
    (is (nil? ((:authenticate service) (:token issued))))
    (is ((:authenticate service) (:token refreshed)))))

(deftest invalid-password-and-concurrent-login-fail-closed
  (let [{:keys [service]} (service-fixture)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid username"
                          ((:login! service) {"username" "admin" "password" "wrong"})))
    ((:login! service) {"username" "admin" "password" "secret"})
    (let [error (try
                  ((:login! service) {"username" "admin" "password" "secret"})
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
      (is (= :already-logged-in (:type (ex-data error))))
      (is (= 10210 (:code (ex-data error)))))
    (is (string? (:token ((:login! service)
                           {"username" "admin" "password" "secret" "forceLogin" true}))))))

(deftest jwt-claims-stay-rs256-and-session-bound
  (let [{:keys [service]} (service-fixture)
        issued ((:login! service) {"username" "admin" "password" "secret"})
        claims (security/verify-rs256 (:token issued) (:public-key service) "keystone" nil)]
    (is (= "7" (get claims "sub")))
    (is (= "admin" (get claims "roleKey")))
    (is (pos? (long (get claims "exp"))))))
