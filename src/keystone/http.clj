(ns keystone.http
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [keystone.auth :as auth]
            [keystone.dict.repository :as repository]
            [keystone.dict.schema :as schema]
            [keystone.response :as response]
            [keystone.security :as security]
            [keystone.user.repository :as users]
            [reitit.ring :as ring]
            [ring.middleware.params :as params]))

(defn- body-params [request]
  (or (:body-params request) {}))

(defn- json-content? [request]
  (str/starts-with? (str/lower-case (get-in request [:headers "content-type"] ""))
                    "application/json"))

(defn- wrap-json-body [handler]
  (fn [request]
    (if (and (:body request) (json-content? request))
      (try
        (let [raw (if (string? (:body request))
                    (:body request)
                    (slurp (:body request)))]
          (handler (assoc request :body-params
                          (if (str/blank? raw) {} (json/read-value raw)))))
        (catch Exception e
          (throw (ex-info "请求体不是有效 JSON" {:type :validation} e))))
      (handler request))))

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

(defn- captcha-code []
  (let [alphabet "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"]
    (apply str (repeatedly 4 #(nth alphabet (rand-int (count alphabet)))))))

(defn- captcha-image [code]
  (let [svg (str "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='40' "
                "viewBox='0 0 120 40'><rect width='120' height='40' fill='#f4f4f5'/>"
                "<text x='10' y='28' font-family='monospace' font-size='24' "
                "font-weight='bold' fill='#303133'>" code "</text></svg>")]
    (.encodeToString (java.util.Base64/getEncoder)
                     (.getBytes svg "UTF-8"))))

(defn- captcha-response [auth-state]
  (if-not (:captcha-enabled? auth-state)
    (response/ok {:isCaptchaOn false})
    (let [code (captcha-code)
          key (str (java.util.UUID/randomUUID))]
      (swap! (:captcha-store auth-state) assoc key
             {:code code :expires-at (+ (System/currentTimeMillis) 300000)})
      (response/ok {:isCaptchaOn true
                    :captchaCodeImg (str "data:image/svg+xml;base64," (captcha-image code))
                    :captchaCodeKey key}))))

(defn- validate-captcha! [auth-state body]
  (when (:captcha-enabled? auth-state)
    (let [key (or (get body "captchaCodeKey") (:captcha-code-key body))
          code (or (get body "captchaCode") (:captcha-code body))
          entry (get @(:captcha-store auth-state) key)]
      (swap! (:captcha-store auth-state) dissoc key)
      (cond
        (nil? entry) (throw (ex-info "验证码过期" {:type :captcha-expired :code 10204}))
        (<= (long (:expires-at entry)) (System/currentTimeMillis))
        (throw (ex-info "验证码过期" {:type :captcha-expired :code 10204}))
        (not= (str/lower-case (str code)) (str/lower-case (:code entry)))
        (throw (ex-info "验证码错误" {:type :captcha-wrong :code 10203}))))))

(defn- auth-unavailable []
  (response/unavailable))

(defn- rsa-public-key [auth-state]
  (if-let [public-key (get-in auth-state [:key-pair])]
    (response/ok {:publicKey (auth/public-key-base64 (.getPublic ^java.security.KeyPair public-key))})
    (auth-unavailable)))

(defn- login [auth-state request]
  (if-let [service (:service auth-state)]
    (do
      (validate-captcha! auth-state (body-params request))
      (response/ok ((:login! service) (body-params request))))
    (auth-unavailable)))

(defn- refresh-token [auth-state request]
  (if-let [service (:service auth-state)]
    (response/ok ((:refresh! service) (or (get (body-params request) "refreshToken")
                                          (:refresh-token (body-params request)))))
    (auth-unavailable)))

(defn- logout-refresh-token [auth-state request]
  (if-let [service (:service auth-state)]
    (do
      ((:logout-refresh! service)
       (or (get (body-params request) "refreshToken")
           (:refresh-token (body-params request))))
      (response/ok))
    (auth-unavailable)))

(defn- get-config [repo auth-state]
  (response/ok {:isCaptchaOn (boolean (:captcha-enabled? auth-state))
                :dictionary (if repo ((:dictionary-data-map repo)) {})}))

(defn- current-user [repo request]
  (if-let [user ((:find-by-id repo) (get-in request [:principal :user-id]))]
    (response/ok ((:current-user repo) user))
    (response/not-found "用户" (get-in request [:principal :user-id]))))

(defn- routers [repo request]
  (if-let [user ((:find-by-id repo) (get-in request [:principal :user-id]))]
    (response/ok ((:menu-tree repo) user))
    (response/not-found "用户" (get-in request [:principal :user-id]))))

(defn- with-repository-function [repo function-key handler]
  (fn [request]
    (if (and repo (fn? (get repo function-key)))
      (handler request)
      (response/unavailable))))

(defn- management-list [repo function-key request]
  (response/ok ((get repo function-key) (:query-params request))))

(defn- management-detail [repo function-key entity request]
  (let [id (schema/positive-id (get-in request [:path-params :id]))]
    (if-let [data ((get repo function-key) id)]
      (response/ok data)
      (response/not-found entity id))))

(defn- management-dropdown [repo function-key request]
  (response/ok ((get repo function-key))))

(defn- wrap-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [type code]} (ex-data e)]
          (case type
            :validation (response/validation-error (ex-message e))
            :invalid-credentials (response/fail 200 (or code 10201) "用户密码错误，请重输")
            :already-logged-in (response/fail 200 (or code 10210) "该账号已经登录")
            :invalid-token (response/fail 200 (or code 107) "token异常")
            :captcha-expired (response/fail 200 (or code 10204) "验证码过期")
            :captcha-wrong (response/fail 200 (or code 10203) "验证码错误")
            :unavailable (response/unavailable)
            (response/fail 500 99999 "操作失败"))))
      (catch Exception _
        (response/fail 500 99999 "操作失败")))))

(defn make-handler
  "Inject a real authenticator only after RS256, expiry, and session
   revocation verification exist. nil is fail-closed."
  [{:keys [db cache authenticator dict-repository user-repository auth]}]
  (let [repo (or dict-repository
                 (when-let [datasource (:datasource db)]
                   (repository/make-repository datasource cache)))
        user-repo (or user-repository (:repository auth)
                      (when-let [datasource (:datasource db)]
                        (users/make-repository datasource)))
        effective-authenticator (or authenticator (:authenticate (:service auth)))
        authorize (fn [handler permission]
                    (security/wrap-protected handler effective-authenticator permission))
        dict-list (with-repository repo (partial list-types repo))
        dict-one (with-repository repo (partial get-type repo))
        dict-data (with-repository repo (partial list-data-by-type repo))
        config-handler (fn [_] (get-config repo auth))
        captcha-handler (fn [_] (captcha-response auth))
        rsa-handler (fn [_] (rsa-public-key auth))
        login-handler (fn [request] (login auth request))
        refresh-handler (fn [request] (refresh-token auth request))
        logout-refresh-handler (fn [request] (logout-refresh-token auth request))
        logout-handler (authorize
                        (fn [request]
                          (when-let [service (:service auth)]
                            ((:logout! service) (:principal request)))
                          (response/ok)) nil)
        user-info-handler (with-repository user-repo (partial current-user user-repo))
        routers-handler (with-repository user-repo (partial routers user-repo))
        user-list-handler (with-repository-function user-repo :list-users
                            (partial management-list user-repo :list-users))
        user-detail-handler (with-repository-function user-repo :get-user-detail
                              (partial management-detail user-repo :get-user-detail "用户"))
        role-list-handler (with-repository-function user-repo :list-roles
                            (partial management-list user-repo :list-roles))
        role-detail-handler (with-repository-function user-repo :get-role
                              (partial management-detail user-repo :get-role "角色"))
        menu-management-list-handler (with-repository-function user-repo :list-menu-management
                                       (partial management-list user-repo :list-menu-management))
        menu-detail-handler (with-repository-function user-repo :get-menu
                              (partial management-detail user-repo :get-menu "菜单"))
        menu-dropdown-handler (with-repository-function user-repo :menu-dropdown
                               (partial management-dropdown user-repo :menu-dropdown))
        dept-list-handler (with-repository-function user-repo :list-depts
                            (partial management-list user-repo :list-depts))
        dept-detail-handler (with-repository-function user-repo :get-dept
                              (partial management-detail user-repo :get-dept "部门"))
        dept-dropdown-handler (with-repository-function user-repo :dept-dropdown
                                (partial management-dropdown user-repo :dept-dropdown))
        post-list-handler (with-repository-function user-repo :list-posts
                            (partial management-list user-repo :list-posts))
        post-detail-handler (with-repository-function user-repo :get-post
                              (partial management-detail user-repo :get-post "岗位"))
        router (ring/router
                [["/health"
                  {:get (fn [_] (response/ok "is alive"))}]
                 ["/getConfig" {:get config-handler}]
                 ["/captchaImage" {:get captcha-handler}]
                 ["/login/rsa-public-key" {:get rsa-handler}]
                 ["/login" {:post login-handler}]
                 ["/refresh-token" {:post refresh-handler}]
                 ["/logout-refresh-token" {:post logout-refresh-handler}]
                 ["/logout" {:post logout-handler}]
                 ["/getLoginUserInfo"
                  {:get (authorize user-info-handler nil)}]
                 ["/getRouters"
                  {:get (authorize routers-handler nil)}]
                 ["/system/users"
                  {:get (authorize user-list-handler "system:user:list")}]
                 ["/system/users/:id"
                  {:get (authorize user-detail-handler "system:user:query")}]
                 ["/system/role/list"
                  {:get (authorize role-list-handler "system:role:list")}]
                 ["/system/role/:id"
                  {:get (authorize role-detail-handler "system:role:query")}]
                 ["/system/menus"
                  {:get (authorize menu-management-list-handler "system:menu:list")}]
                 ["/system/menus/dropdown"
                  {:get (authorize menu-dropdown-handler nil)}]
                 ["/system/menus/:id"
                  {:get (authorize menu-detail-handler "system:menu:query")}]
                 ["/system/depts"
                  {:get (authorize dept-list-handler "system:dept:list")}]
                 ["/system/depts/dropdown"
                  {:get (authorize dept-dropdown-handler nil)}]
                 ["/system/dept/:id"
                  {:get (authorize dept-detail-handler "system:dept:query")}]
                 ["/system/post/list"
                  {:get (authorize post-list-handler "system:post:list")}]
                 ["/system/post/:id"
                  {:get (authorize post-detail-handler "system:post:query")}]
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
        wrap-json-body
        wrap-errors)))
