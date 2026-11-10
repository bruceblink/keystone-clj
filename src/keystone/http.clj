(ns keystone.http
  (:require [clojure.string :as str]
            [keystone.dict.repository :as repository]
            [keystone.dict.schema :as schema]
            [keystone.response :as response]
            [keystone.security :as security]
            [reitit.ring :as ring]
            [ring.middleware.params :as params]))

(defn- with-repository [repo handler]
  (fn [request]
    (if repo
      (handler request)
      (response/unavailable))))

(defn- list-types [repo request]
  (let [query (schema/page-query (:query-params request))]
    (response/ok ((:list-types repo) query))))

(defn- get-type [repo request]
  (let [id (schema/positive-id (get-in request [:path-params :dictId]))]
    (if-let [data ((:get-type repo) id)]
      (response/ok data)
      (response/not-found "字典类型" id))))

(defn- list-data-by-type [repo request]
  (let [dict-type (get-in request [:path-params :dictType])]
    (if (str/blank? dict-type)
      (response/validation-error "dictType")
      (response/ok ((:list-data-by-type repo) dict-type)))))

(defn- wrap-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= :validation (:type (ex-data e)))
          (response/validation-error (ex-message e))
          (response/fail 500 99999 "操作失败")))
      (catch Exception _
        (response/fail 500 99999 "操作失败")))))

(defn make-handler
  "Inject a real authenticator only after RS256, expiry, and session
   revocation verification exist. nil is fail-closed."
  [{:keys [db cache authenticator dict-repository]}]
  (let [repo (or dict-repository
                 (when-let [datasource (:datasource db)]
                   (repository/make-repository datasource cache)))
        authorize (fn [handler permission]
                    (security/wrap-protected handler authenticator permission))
        dict-list (with-repository repo (partial list-types repo))
        dict-one (with-repository repo (partial get-type repo))
        dict-data (with-repository repo (partial list-data-by-type repo))
        router (ring/router
                [["/health"
                  {:get (fn [_] (response/ok "is alive"))}]
                 ["/system/dict/types"
                  {:get (authorize dict-list "system:dict:list")}]
                 ["/system/dict/type/list"
                  {:get (authorize dict-list "system:dict:list")}]
                 ["/system/dict/type/:dictId"
                  {:get (authorize dict-one "system:dict:query")}]
                 ["/system/dict/data/type/:dictType"
                  {:get (authorize dict-data nil)}]]
                {:conflicts nil})
        default (ring/create-default-handler
                 {:not-found (fn [_] (response/fail 404 10001 "请求资源不存在"))
                  :method-not-allowed (fn [_] (response/fail 405 10002 "不支持的操作"))
                  :not-acceptable (fn [_] (response/fail 406 10002 "不支持的操作"))})]
    (-> (ring/ring-handler router default)
        params/wrap-params
        wrap-errors)))
