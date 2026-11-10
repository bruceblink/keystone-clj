(ns keystone.security
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [keystone.response :as response]))

(defn- b64url-decode [s]
  (.decode (java.util.Base64/getUrlDecoder) ^String s))

(defn- unsigned-claims [token]
  (let [[header payload signature] (str/split token #"\." -1)]
    (when (and header payload signature)
      {:header (json/read-value (String. (b64url-decode header) "UTF-8"))
       :claims (json/read-value (String. (b64url-decode payload) "UTF-8"))
       :signing-input (str header "." payload)
       :signature (b64url-decode signature)})))

(defn verify-rs256
  "Return JWT claims when token is a valid RS256 token. The public-key
   argument is a java.security.PublicKey. Optional issuer and session-valid?
   enforce the server-side session boundary."
  ([token public-key] (verify-rs256 token public-key nil nil))
  ([token public-key issuer session-valid?]
   (try
     (let [{:keys [header claims signing-input signature]} (unsigned-claims token)
           now (quot (System/currentTimeMillis) 1000)
           verifier (doto (java.security.Signature/getInstance "SHA256withRSA")
                      (.initVerify public-key)
                      (.update (.getBytes signing-input "UTF-8")))]
       (when (and (= "RS256" (get header "alg"))
                  (.verify verifier signature)
                  (number? (get claims "exp"))
                  (> (long (get claims "exp")) now)
                  (or (nil? issuer) (= issuer (get claims "iss")))
                  (or (nil? session-valid?) (session-valid? claims)))
         claims))
     (catch Exception _ nil))))

(defn make-rs256-authenticator
  "Build an authenticator suitable for wrap-protected. permissions-claim is
   the JWT claim containing a collection of RBAC permission strings."
  [{:keys [public-key issuer session-valid? permissions-claim]
    :or {permissions-claim "permissions"}}]
  (fn [token]
    (when-let [claims (verify-rs256 token public-key issuer session-valid?)]
      {:user-id (or (get claims "userId") (get claims "sub"))
       :permissions (set (or (get claims permissions-claim) []))
       :claims claims})))

(defn bearer-token [request]
  (some->> (get-in request [:headers "authorization"])
           str/trim
           (re-matches #"(?i)^Bearer\s+([^\s]+)$")
           second))

(defn wrap-protected
  "Require an authenticated principal and optionally one permission.
   The authenticator is nil until a real RS256+session verifier is implemented.
   A nil authenticator ALWAYS denies access."
  [handler authenticator required-permission]
  (fn [request]
    (let [token (bearer-token request)
          principal (when (and token authenticator)
                      (authenticator token))]
      (cond
        (nil? principal)
        (response/fail 401 106
                       (str "请求接口：" (:uri request) " 失败，用户未授权"))

        (and required-permission
             (not (contains? (set (:permissions principal)) required-permission)))
        (response/fail 403 10202 "没有权限进行此操作，请联系管理员")

        :else
        (handler (assoc request :principal principal))))))
