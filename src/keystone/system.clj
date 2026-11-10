(ns keystone.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [keystone.auth :as auth]
            [keystone.config :as config]
            [keystone.http :as http]
            [keystone.user.repository :as user-repository]
            [ring.adapter.jetty :as jetty])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]
           [com.github.benmanes.caffeine.cache Caffeine]
           [java.util.concurrent TimeUnit]
           [org.flywaydb.core Flyway]
           [redis.clients.jedis DefaultJedisClientConfig HostAndPort JedisPooled]))

(defmethod ig/init-key :keystone/datasource
  [_ {:keys [enabled? jdbc-url username password max-pool-size]}]
  (if enabled?
    (let [cfg (doto (HikariConfig.)
                (.setJdbcUrl jdbc-url)
                (.setUsername username)
                (.setPassword password)
                (.setMaximumPoolSize (int max-pool-size))
                (.setPoolName "keystone-clj"))]
      {:datasource (HikariDataSource. cfg)})
    {:datasource nil}))

(defmethod ig/halt-key! :keystone/datasource
  [_ {:keys [datasource]}]
  (when datasource (.close ^HikariDataSource datasource)))

(defmethod ig/init-key :keystone/migrations
  [_ {:keys [enabled? db]}]
  (when enabled?
    (let [datasource (:datasource db)]
      (when-not datasource
        (throw (ex-info "APP_MIGRATE requires APP_DB_ENABLED=true" {})))
      (when-not (io/resource "db/migrate/common/V3_3_0__flyway_baseline_marker.sql")
        (throw (ex-info "Import the original Keystone Flyway files before enabling migration" {})))
      (let [configuration (doto (Flyway/configure)
                            (.dataSource datasource)
                            (.locations (into-array String
                                                    ["classpath:db/migrate/common"
                                                     "classpath:db/migrate/mysql"]))
                            (.validateOnMigrate true))]
        (.migrate (.load configuration)))))
  {:initialized? true})

(defmethod ig/init-key :keystone/redis
  [_ {:keys [enabled? host port password]}]
  (if enabled?
    (let [builder (DefaultJedisClientConfig/builder)]
      (when (seq password) (.password builder password))
      {:client (JedisPooled. (HostAndPort. host (int port))
                             (.build builder))})
    {:client nil}))

(defmethod ig/halt-key! :keystone/redis
  [_ {:keys [client]}]
  (when client (.close ^JedisPooled client)))

(defmethod ig/init-key :keystone/auth
  [_ {:keys [auth db redis]}]
  (let [key-pair (when-not (clojure.string/blank? (:private-key auth))
                   (auth/key-pair-from-config auth))
        datasource (:datasource db)
        redis-client (:client redis)
        repository (when datasource (user-repository/make-repository datasource))
        session-store (when redis-client (auth/make-redis-session-store redis-client))
        service (when (and key-pair repository session-store)
                  (auth/make-service
                   {:key-pair key-pair
                    :issuer (:issuer auth)
                    :access-ttl-seconds (:access-ttl-seconds auth)
                    :refresh-ttl-seconds (:refresh-ttl-seconds auth)
                    :session-store session-store
                    :find-user (:find-by-username repository)
                    :password-decoder #(auth/decrypt-password (.getPrivate ^java.security.KeyPair key-pair) %)}))]
    {:key-pair key-pair
     :repository repository
     :service service
     :captcha-enabled? (:captcha-enabled? auth)
     :captcha-store (atom {})}))

(defmethod ig/init-key :keystone/cache
  [_ {:keys [maximum-size ttl-minutes]}]
  (-> (Caffeine/newBuilder)
      (.maximumSize (long maximum-size))
      (.expireAfterWrite (long ttl-minutes) TimeUnit/MINUTES)
      (.build)))

(defmethod ig/init-key :keystone/handler
  [_ {:keys [db cache redis migrations authenticator auth]}]
  ;; redis/migrations refs make infrastructure startup order explicit.
  ;; They are not yet consumed by unfinished authentication/business modules.
  (let [_ redis
        _ migrations]
    (http/make-handler {:db db :cache cache :authenticator authenticator :auth auth})))

(defmethod ig/init-key :keystone/server
  [_ {:keys [handler host port]}]
  (jetty/run-jetty handler {:host host :port (int port) :join? false}))

(defmethod ig/halt-key! :keystone/server
  [_ server]
  (when server (.stop server)))

(defn system-config
  ([] (system-config (config/settings)))
  ([settings]
   {:keystone/datasource (:database settings)
    :keystone/redis (:redis settings)
    :keystone/cache (:cache settings)
    :keystone/migrations {:db (ig/ref :keystone/datasource)
                          :enabled? (get-in settings [:migrations :enabled?])}
    :keystone/auth {:auth (:auth settings)
                    :db (ig/ref :keystone/datasource)
                    :redis (ig/ref :keystone/redis)}
    :keystone/handler {:db (ig/ref :keystone/datasource)
                       :redis (ig/ref :keystone/redis)
                       :cache (ig/ref :keystone/cache)
                       :migrations (ig/ref :keystone/migrations)
                       :auth (ig/ref :keystone/auth)}
    :keystone/server {:handler (ig/ref :keystone/handler)
                      :host (get-in settings [:http :host])
                      :port (get-in settings [:http :port])}}))
