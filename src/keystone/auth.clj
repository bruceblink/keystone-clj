(ns keystone.auth
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [keystone.security :as security])
  (:import [java.nio.charset StandardCharsets]
           [java.security KeyFactory KeyPair PrivateKey PublicKey Signature]
           [java.security.interfaces RSAPrivateCrtKey]
           [java.security.spec PKCS8EncodedKeySpec RSAPublicKeySpec X509EncodedKeySpec]
           [java.util Base64 UUID]
           [javax.crypto Cipher]
           [org.mindrot.jbcrypt BCrypt]))

(def ^:private access-header {"alg" "RS256" "typ" "JWT"})

(defn- now-seconds [] (quot (System/currentTimeMillis) 1000))

(defn- b64url [bytes]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bytes))

(defn- b64 [value]
  (.decode (Base64/getDecoder) ^String value))

(defn- strip-pem [value]
  (-> value
      (str/replace #"-----BEGIN [^-]+-----" "")
      (str/replace #"-----END [^-]+-----" "")
      (str/replace #"\s+" "")))

(defn- decode-key-bytes [value]
  (b64 (strip-pem value)))

(defn parse-private-key
  "Parse a PKCS#8 RSA private key from base64 DER or PEM text." 
  [value]
  (when-not (str/blank? value)
    (let [factory (KeyFactory/getInstance "RSA")]
      (.generatePrivate factory (PKCS8EncodedKeySpec.
                                 (decode-key-bytes value))))))

(defn parse-public-key
  "Parse an X.509 RSA public key from base64 DER or PEM text." 
  [value]
  (when-not (str/blank? value)
    (let [factory (KeyFactory/getInstance "RSA")]
      (.generatePublic factory (X509EncodedKeySpec.
                               (decode-key-bytes value))))))

(defn key-pair-from-config
  "Load the configured key pair. The public key may be omitted because it is
   derivable from an RSA private CRT key." 
  [{:keys [private-key public-key]}]
  (let [private (parse-private-key private-key)
        public (or (parse-public-key public-key)
                   (when (instance? RSAPrivateCrtKey private)
                     (let [rsa ^RSAPrivateCrtKey private
                           factory (KeyFactory/getInstance "RSA")]
                       (.generatePublic factory
                                         (RSAPublicKeySpec. (.getModulus rsa)
                                                             (.getPublicExponent rsa))))))]
    (when (and private public)
      (KeyPair. public private))))

(defn public-key-base64 [^PublicKey public-key]
  (.encodeToString (Base64/getEncoder) (.getEncoded public-key)))

(defn decrypt-password
  "Decrypt the browser's RSA/ECB/PKCS1Padding password payload." 
  [^PrivateKey private-key encrypted-password]
  (try
    (let [cipher (Cipher/getInstance "RSA/ECB/PKCS1Padding")]
      (.init cipher Cipher/DECRYPT_MODE private-key)
      (String. (.doFinal cipher (b64 encrypted-password)) StandardCharsets/UTF_8))
    (catch Exception e
      (throw (ex-info "Unable to decrypt password" {:type :invalid-credentials} e)))))

(defn- sha256 [value]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")]
    (.encodeToString (Base64/getUrlEncoder)
                     (.digest digest (.getBytes ^String value StandardCharsets/UTF_8)))))

(defn- random-secret [] (str (UUID/randomUUID) (UUID/randomUUID)))

(defn- json-encode [value]
  (json/write-value-as-string value))

(defn- json-decode [value]
  (json/read-value value))

(defn make-memory-session-store []
  "Create a session store for tests and single-process development." 
  (let [state (atom {:accounts {} :sessions {} :access {}})
        expired? (fn [session]
                   (or (nil? session)
                       (<= (long (:expires-at session))
                           (System/currentTimeMillis))))
        get-session (fn [id]
                      (let [session (get-in @state [:sessions id])]
                        (if (expired? session)
                          (do (when session
                                (swap! state update :sessions dissoc id))
                              nil)
                          session)))]
    {:get-account (fn [account-id]
                   (when-let [id (get-in @state [:accounts account-id])]
                     (when (get-session id) id)))
     :get (fn [id] (get-session id))
     :put! (fn [session]
             (swap! state
                    (fn [current]
                      (-> current
                          (assoc-in [:sessions (:id session)] session)
                          (assoc-in [:accounts (:account-id session)] (:id session))
                          (assoc-in [:access (:access-jti session)] (:id session)))))
             session)
     :delete-access! (fn [jti]
                       (swap! state update :access dissoc jti))
     :delete! (fn [id]
                (when-let [session (get-session id)]
                  (swap! state
                         (fn [current]
                           (-> current
                               (update :sessions dissoc id)
                               (update :access dissoc (:access-jti session))
                               (update :accounts
                                       (fn [accounts]
                                         (if (= id (get accounts (:account-id session)))
                                           (dissoc accounts (:account-id session))
                                           accounts)))))))
                nil)
     :active-access? (fn [jti]
                       (boolean (when-let [id (get-in @state [:access jti])]
                                  (get-session id))))
     :state state}))

(defn make-redis-session-store
  "Create a Redis-backed refresh/access session store." 
  [client]
  (let [refresh-key (fn [id] (str "keystone:login:refresh:" id))
        account-key (fn [id] (str "keystone:login:account:" id))
        access-key (fn [id] (str "keystone:login:access:" id))
        get-session (fn [id]
                      (some-> (.get client (refresh-key id)) json-decode))]
    {:get-account (fn [account-id]
                   (let [id (.get client (account-key account-id))]
                     (when (and id (get-session id)) id)))
     :get get-session
     :put! (fn [session]
             (let [ttl (max 1 (int (Math/ceil (/ (- (long (:expires-at session))
                                                   (System/currentTimeMillis))
                                                1000.0))))
                   value (json-encode session)]
               (.setex client (refresh-key (:id session)) ttl value)
               (.setex client (account-key (:account-id session)) ttl (:id session))
               (.setex client (access-key (:access-jti session)) ttl "1")
               session))
     :delete-access! (fn [jti] (.del client (access-key jti)))
     :delete! (fn [id]
                (when-let [session (get-session id)]
                  (.del client (refresh-key id)
                        (account-key (:account-id session))
                        (access-key (:access-jti session))))
                nil)
     :active-access? (fn [jti] (= "1" (.get client (access-key jti))))}))

(defn- sign-access-token [^PrivateKey private-key claims]
  (let [header (b64url (.getBytes (json-encode access-header) StandardCharsets/UTF_8))
        payload (b64url (.getBytes (json-encode claims) StandardCharsets/UTF_8))
        input (str header "." payload)
        signer (doto (Signature/getInstance "SHA256withRSA")
                 (.initSign private-key)
                 (.update (.getBytes input StandardCharsets/UTF_8)))]
    (str input "." (b64url (.sign signer)))))

(defn- as-long [value]
  (when (some? value) (long value)))

(defn- credentials-valid? [password hash]
  (try
    (and (string? password) (string? hash) (BCrypt/checkpw password hash))
    (catch Exception _ false)))

(defn make-service
  "Build the login, JWT and refresh-session service. `find-user` must return a
   map containing :userId/:user-id, :username, :password, :status,
   :permissions and :roleKey/:role-key." 
  [{:keys [key-pair issuer access-ttl-seconds refresh-ttl-seconds session-store
           find-user password-decoder captcha-validator]
    :or {issuer "keystone"
         access-ttl-seconds 1800
         refresh-ttl-seconds 604800
         password-decoder identity}}]
  (let [private-key (.getPrivate ^KeyPair key-pair)
        public-key (.getPublic ^KeyPair key-pair)
        store session-store
        issue (fn [user force-login]
                (let [account-id (str (or (:user-id user) (:userId user)))
                      existing (when store ((:get-account store) account-id))]
                  (when existing
                    (if force-login
                      ((:delete! store) existing)
                      (throw (ex-info "Account already logged in"
                                      {:type :already-logged-in :code 10210}))))
                  (let [session-id (str (UUID/randomUUID))
                        access-jti (str (UUID/randomUUID))
                        refresh-secret (random-secret)
                        refresh-token (str session-id "." refresh-secret)
                        issued-at (now-seconds)
                        access-exp (+ issued-at (long access-ttl-seconds))
                        refresh-exp (+ (* 1000 issued-at)
                                       (* 1000 (long refresh-ttl-seconds)))
                        permissions (vec (or (:permissions user) []))
                        role-key (or (:role-key user) (:roleKey user))
                        claims (cond-> {"sub" account-id
                                       "iss" issuer
                                       "iat" issued-at
                                       "exp" access-exp
                                       "jti" access-jti
                                       "sid" session-id
                                       "permissions" permissions}
                                 role-key (assoc "roleKey" role-key))
                        session {:id session-id
                                 :account-id account-id
                                 :user-id (or (:user-id user) (:userId user))
                                 :username (:username user)
                                 :access-jti access-jti
                                 :refresh-hash (sha256 refresh-secret)
                                 :expires-at refresh-exp
                                 :user user}]
                    (when-not store
                      (throw (ex-info "Session store is unavailable" {:type :unavailable})))
                    ((:put! store) session)
                    {:token (sign-access-token private-key claims)
                     :refreshToken refresh-token
                     :expiresIn (long access-ttl-seconds)
                     :refreshExpiresIn (long refresh-ttl-seconds)
                     :claims claims
                     :user user})))
        service {:public-key public-key
                 :private-key private-key
                 :issuer issuer
                 :session-store store
                 :authenticate (fn [token]
                                 (when store
                                   (when-let [principal (security/verify-rs256
                                                         token public-key issuer
                                                         #((:active-access? store)
                                                           (get % "jti")))]
                                     {:user-id (or (get principal "sub")
                                                   (get principal "userId"))
                                      :permissions (set (or (get principal "permissions") []))
                                      :role-key (get principal "roleKey")
                                      :claims principal})))
                 :login! (fn [body]
                           (let [username (or (get body "username") (:username body))
                                 supplied-password (or (get body "password") (:password body))
                                 force-login (boolean (or (get body "forceLogin")
                                                          (:force-login body)))
                                 _ (when captcha-validator
                                     (captcha-validator body))
                                 user (when find-user (find-user username))
                                 password (password-decoder supplied-password)]
                             (when-not (and user
                                            (= 1 (long (or (:status user) 0)))
                                            (credentials-valid? password (:password user)))
                               (throw (ex-info "Invalid username or password"
                                               {:type :invalid-credentials :code 10201})))
                             (issue user force-login)))
                 :refresh! (fn [refresh-token]
                             (let [[session-id secret] (str/split (str refresh-token) #"\." 2)
                                   session (when (and session-id secret store)
                                             ((:get store) session-id))]
                               (when-not (and session
                                              (= (sha256 secret) (:refresh-hash session))
                                              (> (long (:expires-at session))
                                                 (System/currentTimeMillis)))
                                 (throw (ex-info "Invalid refresh token"
                                                 {:type :invalid-token :code 107})))
                               ((:delete-access! store) (:access-jti session))
                               (let [user (:user session)
                                     issued (issue user true)]
                                 ((:delete! store) session-id)
                                 issued)))
                 :logout! (fn [principal]
                            (when-let [session-id (get-in principal [:claims "sid"])]
                              ((:delete! store) session-id))
                            nil)
                 :logout-refresh! (fn [refresh-token]
                                    (let [[session-id secret] (str/split (str refresh-token) #"\." 2)
                                          session (when (and session-id store)
                                                    ((:get store) session-id))]
                                      (when (and session (= (sha256 secret) (:refresh-hash session)))
                                        ((:delete! store) session-id)))
                                    nil)}]
    service))
