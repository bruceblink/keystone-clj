(ns keystone.config
  (:require [clojure.string :as str]))

(defn env
  "Return a non-blank environment value or the supplied default."
  [name default]
  (let [value (System/getenv name)]
    (if (str/blank? value) default value)))

(defn enabled? [name]
  (contains? #{"true" "1" "yes" "on"}
             (str/lower-case (env name "false"))))

(defn env-long [name default]
  (let [value (env name (str default))]
    (try
      (Long/parseLong value)
      (catch NumberFormatException e
        (throw (ex-info (str "Invalid integer environment variable: " name)
                        {:variable name} e))))))

(defn settings []
  {:http {:host (env "HOST" "0.0.0.0")
          :port (env-long "PORT" 18080)}
   :database {:enabled? (enabled? "APP_DB_ENABLED")
              :jdbc-url (env "DB_JDBC_URL" "jdbc:mysql://localhost:33066/keystone?serverTimezone=UTC")
              :username (env "DB_USERNAME" "")
              :password (env "DB_PASSWORD" "")
              :max-pool-size (env-long "DB_POOL_SIZE" 10)}
   :migrations {:enabled? (enabled? "APP_MIGRATE")}
   :redis {:enabled? (enabled? "APP_REDIS_ENABLED")
           :host (env "REDIS_HOST" "localhost")
           :port (env-long "REDIS_PORT" 6379)
           :password (env "REDIS_PASSWORD" "")}
   :auth {:issuer (env "JWT_ISSUER" "keystone")
          :private-key (env "KEYSTONE_RSA_PRIVATE_KEY" "")
          :public-key (env "KEYSTONE_RSA_PUBLIC_KEY" "")
          :access-ttl-seconds (env-long "TOKEN_EXPIRATION_SECONDS" 1800)
          :refresh-ttl-seconds (env-long "TOKEN_REFRESH_EXPIRATION_SECONDS" 604800)
          :captcha-enabled? (enabled? "APP_CAPTCHA_ENABLED")}
   :cache {:maximum-size (env-long "CACHE_MAXIMUM_SIZE" 10000)
           :ttl-minutes (env-long "CACHE_TTL_MINUTES" 5)}})
