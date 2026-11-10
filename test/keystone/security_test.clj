(ns keystone.security-test
  (:require [clojure.test :refer [deftest is]]
            [jsonista.core :as json]
            [keystone.security :as security])
  (:import [java.security KeyPairGenerator Signature]
           [java.util Base64]))

(defn- b64url [bytes]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bytes))

(defn- key-pair []
  (let [generator (KeyPairGenerator/getInstance "RSA")]
    (.initialize generator 2048)
    (let [pair (.generateKeyPair generator)]
      {:private (.getPrivate pair)
       :public (.getPublic pair)})))

(defn- token [private-key claims]
  (let [header (b64url (.getBytes (json/write-value-as-string {"alg" "RS256" "typ" "JWT"}) "UTF-8"))
        payload (b64url (.getBytes (json/write-value-as-string claims) "UTF-8"))
        input (str header "." payload)
        signer (doto (Signature/getInstance "SHA256withRSA")
                 (.initSign private-key)
                 (.update (.getBytes input "UTF-8")))]
    (str input "." (b64url (.sign signer)))))

(defn- valid-claims []
  {"sub" "42"
   "iss" "keystone"
   "exp" (+ (quot (System/currentTimeMillis) 1000) 300)
   "permissions" ["system:dict:list"]})

(deftest verifies-valid-rs256-token
  (let [{:keys [private public]} (key-pair)
        claims (valid-claims)
        verified (security/verify-rs256 (token private claims) public "keystone"
                                        (fn [_] true))]
    (is (= claims verified))
    (is (= claims (security/verify-rs256 (token private claims) public "keystone")))))

(deftest rejects-invalid-expiry-issuer-signature-and-session
  (let [{:keys [private public]} (key-pair)
        claims (valid-claims)
        jwt (token private claims)
        expired (token private (assoc claims "exp" 1))
        wrong-issuer (token private (assoc claims "iss" "other"))
        tampered (str jwt "x")]
    (is (nil? (security/verify-rs256 expired public "keystone" nil)))
    (is (nil? (security/verify-rs256 wrong-issuer public "keystone" nil)))
    (is (nil? (security/verify-rs256 tampered public "keystone" nil)))
    (is (nil? (security/verify-rs256 jwt public "keystone" (fn [_] false))))))

(deftest authenticator-exposes-rbac-permissions
  (let [{:keys [private public]} (key-pair)
        authenticate (security/make-rs256-authenticator
                     {:public-key public :issuer "keystone"})
        principal (authenticate (token private (valid-claims)))]
    (is (= "42" (:user-id principal)))
    (is (= #{"system:dict:list"} (:permissions principal)))
    (is (= "keystone" (get-in principal [:claims "iss"])))))
