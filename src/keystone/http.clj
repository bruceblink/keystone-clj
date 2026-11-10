(ns keystone.http
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [jsonista.core :as json]
            [keystone.admin.repository :as admin]
            [keystone.auth :as auth]
            [keystone.dict.repository :as repository]
            [keystone.dict.schema :as schema]
            [keystone.files :as files]
            [keystone.rate-limit :as rate]
            [keystone.response :as response]
            [keystone.security :as security]
            [keystone.user.repository :as users]
            [reitit.ring :as ring]
            [ring.middleware.multipart-params :as multipart]
            [ring.middleware.params :as params]))

(defn- body-params [request]
  (or (:body-params request) {}))

(defn- json-content? [request]
  (str/starts-with? (str/lower-case (get-in request [:headers "content-type"] ""))
                    "application/json"))

(defn- wrap-json-body [handler]
  (fn [request]
    (if (and (:body request) (json-content? request))
      (let [raw (try
                  (if (string? (:body request))
                    (:body request)
                    (slurp (:body request)))
                  (catch Exception e
                    (throw (ex-info "请求体不是有效 JSON" {:type :validation} e))))]
        (try
          (handler (assoc request :body-params
                          (if (str/blank? raw) {} (json/read-value raw))))
          (catch com.fasterxml.jackson.core.JsonProcessingException e
            (throw (ex-info "请求体不是有效 JSON" {:type :validation} e)))))
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
  (let [params (cond-> (:query-params request)
                 (= function-key :list-users) (assoc :principal (:principal request)))]
    (response/ok ((get repo function-key) params))))

(defn- management-detail [repo function-key entity request]
  (let [id (schema/positive-id (get-in request [:path-params :id]))]
    (if-let [data ((get repo function-key) id)]
      (response/ok data)
      (response/not-found entity id))))

(defn- management-dropdown [repo function-key request]
  (response/ok ((get repo function-key))))

(defn- write-call [repo function-key arguments]
  (if (and repo (fn? (get repo function-key)))
    (do (apply (get repo function-key) arguments) (response/ok))
    (response/unavailable)))

(defn- body-write [repo function-key request]
  (write-call repo function-key [(body-params request)]))

(defn- id-body-write [repo function-key request]
  (let [id (schema/positive-id (get-in request [:path-params :id]))]
    (write-call repo function-key [[id (body-params request)]])))

(defn- path-id-body-write [repo function-key request path-key]
  (let [id (schema/positive-id (get-in request [:path-params path-key]))]
    (write-call repo function-key [[id (body-params request)]])))

(defn- id-write [repo function-key request]
  (let [id (schema/positive-id (get-in request [:path-params :id]))]
    (write-call repo function-key [id])))

(defn- path-id-write [repo function-key request path-key]
  (let [id (schema/positive-id (get-in request [:path-params path-key]))]
    (write-call repo function-key [id])))

(defn- ids-write [repo function-key request path-key]
  (write-call repo function-key [(get-in request [:path-params path-key])]))

(defn- query-ids-write [repo function-key request query-key]
  (write-call repo function-key [(get-in request [:query-params query-key])]))

(defn- id-status-write [repo function-key request]
  (let [id (schema/positive-id (get-in request [:path-params :id]))]
    (write-call repo function-key [[id (body-params request)]])))

(defn- principal-read [repo function-key request]
  (if (and repo (fn? (get repo function-key)))
    (response/ok ((get repo function-key) (get-in request [:principal :user-id])))
    (response/unavailable)))

(defn- principal-body-write [repo function-key request]
  (write-call repo function-key [[(get-in request [:principal :user-id])
                                  (body-params request)]]))

(defn- online-users [auth-state request]
  (if-let [store (get-in auth-state [:service :session-store])]
    (let [params (:query-params request)
          rows (->> ((:list store))
                    (filter (fn [session]
                              (and (or (str/blank? (get params "username"))
                                       (= (get params "username") (:username session)))
                                   (or (str/blank? (get params "ipAddress"))
                                       (= (get params "ipAddress") "")))))
                    (mapv (fn [session]
                            {:tokenId (:access-jti session)
                             :username (:username session)
                             :ipAddress ""
                             :loginLocation ""
                             :loginTime (:issued-at session)
                             :browser ""
                             :operationSystem ""
                             :deptName (get-in session [:user :deptName])})))]
      (response/ok {:total (count rows) :rows rows}))
    (response/unavailable)))

(defn- force-online-logout [auth-state request]
  (if-let [store (get-in auth-state [:service :session-store])]
    (do
      (when-let [token-id (get-in request [:path-params :tokenId])]
        (when-let [delete (:delete-by-access! store)]
          (delete token-id)))
      (response/ok))
    (response/unavailable)))

(defn- cache-info [cache]
  (if cache
    (response/ok {:cacheName "caffeine"
                  :size (.estimatedSize ^com.github.benmanes.caffeine.cache.Cache cache)})
    (response/unavailable)))

(defn- server-info []
  (let [runtime (Runtime/getRuntime)
        max-memory (.maxMemory runtime)
        total-memory (.totalMemory runtime)
        free-memory (.freeMemory runtime)]
    (response/ok {:jvmInfo {:name (System/getProperty "java.vm.name")
                            :version (System/getProperty "java.version")
                            :total total-memory
                            :free free-memory
                            :max max-memory}
                  :memoryInfo {:total total-memory
                               :free free-memory
                               :used (- total-memory free-memory)
                               :max max-memory}
                  :systemInfo {:osName (System/getProperty "os.name")
                               :osArch (System/getProperty "os.arch")
                               :userDir (System/getProperty "user.dir")}})))

(defn- multipart-part [request names]
  (some #(get-in request [:multipart-params %]) names))

(defn- upload-handler [request]
  (if-let [part (multipart-part request ["file" :file])]
    (response/ok (files/upload! part))
    (response/validation-error "file")))

(defn- download-handler [request]
  (let [file (files/download (get-in request [:query-params "fileName"]))]
    {:status 200
     :headers {"content-type" "application/octet-stream"
               "content-disposition" (str "attachment; filename=\"" (.getName file) "\"")}
     :body (io/input-stream file)}))

(defn- avatar-upload-handler [user-repo request]
  (if-let [part (multipart-part request ["avatarfile" "file" :avatarfile :file])]
    (let [uploaded (files/upload! part)
          user-id (get-in request [:principal :user-id])]
      (when (and user-repo (fn? (:update-avatar! user-repo)))
        ((:update-avatar! user-repo) [user-id (:url uploaded)]))
      (response/ok {:imgUrl (:url uploaded)}))
    (response/validation-error "avatarfile")))

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
            :conflict (response/fail 200 (or code 99999) (ex-message e))
            :unavailable (response/unavailable)
            (response/fail 500 99999 "操作失败"))))
      (catch Exception _
        (response/fail 500 99999 "操作失败")))))

(defn make-handler
  "Inject a real authenticator only after RS256, expiry, and session
   revocation verification exist. nil is fail-closed."
  [{:keys [db cache authenticator dict-repository user-repository admin-repository auth]}]
  (let [repo (or dict-repository
                 (when-let [datasource (:datasource db)]
                   (repository/make-repository datasource cache)))
        user-repo (or user-repository (:repository auth)
                      (when-let [datasource (:datasource db)]
                        (users/make-repository datasource)))
        admin-repo (or admin-repository
                       (when-let [datasource (:datasource db)]
                         (admin/make-repository datasource)))
        effective-authenticator (or authenticator (:authenticate (:service auth)))
        limiter (rate/make-limiter)
        client-key (fn [request] (or (:remote-addr request) "global"))
        global-key (fn [_] "global")
        authorize (fn [handler permission]
                    (security/wrap-protected handler effective-authenticator permission))
        dict-list (with-repository repo (partial list-types repo))
        dict-one (with-repository repo (partial get-type repo))
        dict-data (with-repository repo (partial list-data-by-type repo))
        config-handler (rate/wrap (fn [_] (get-config repo auth)) limiter 10000 5 global-key)
        captcha-handler (rate/wrap (fn [_] (captcha-response auth)) limiter 10000 10 client-key)
        rsa-handler (rate/wrap (fn [_] (rsa-public-key auth)) limiter 60000 60 client-key)
        login-handler (rate/wrap (fn [request] (login auth request)) limiter 60000 10 client-key)
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
        profile-handler (authorize (fn [request] (principal-read user-repo :get-profile request)) nil)
        update-profile-handler (authorize (fn [request]
                                            (principal-body-write user-repo :update-profile! request)) nil)
        update-profile-password-handler (authorize (fn [request]
                                                     (principal-body-write user-repo :update-profile-password! request)) nil)
        avatar-handler (authorize (fn [request] (avatar-upload-handler user-repo request)) nil)
        file-upload-handler (authorize upload-handler nil)
        file-download-handler (fn [request] (download-handler request))
        online-users-handler (authorize (fn [request] (online-users auth request)) "monitor:online:list")
        force-online-logout-handler (authorize (fn [request] (force-online-logout auth request)) "monitor:online:forceLogout")
        cache-info-handler (authorize (fn [_] (cache-info cache)) "monitor:cache:list")
        server-info-handler (authorize (fn [_] (server-info)) "monitor:server:list")
        add-user-handler (authorize (fn [request] (body-write user-repo :add-user! request)) "system:user:add")
        update-user-handler (authorize (fn [request] (id-body-write user-repo :update-user! request)) "system:user:edit")
        delete-users-handler (authorize (fn [request] (ids-write user-repo :delete-users! request :id)) "system:user:remove")
        reset-user-password-handler (authorize (fn [request] (id-body-write user-repo :reset-user-password! request)) "system:user:resetPwd")
        change-user-status-handler (authorize (fn [request] (id-status-write user-repo :change-user-status! request)) "system:user:edit")
        add-role-handler (authorize (fn [request] (body-write user-repo :add-role! request)) "system:role:add")
        update-role-handler (authorize (fn [request] (body-write user-repo :update-role! request)) "system:role:edit")
        delete-role-handler (authorize (fn [request] (id-write user-repo :delete-role! request)) "system:role:remove")
        change-role-status-handler (authorize (fn [request] (id-status-write user-repo :change-role-status! request)) "system:role:edit")
        add-menu-handler (authorize (fn [request] (body-write user-repo :add-menu! request)) "system:menu:add")
        update-menu-handler (authorize (fn [request] (id-body-write user-repo :update-menu! request)) "system:menu:edit")
        delete-menu-handler (authorize (fn [request] (id-write user-repo :delete-menu! request)) "system:menu:remove")
        add-dept-handler (authorize (fn [request] (body-write user-repo :add-dept! request)) "system:dept:add")
        update-dept-handler (authorize (fn [request] (id-body-write user-repo :update-dept! request)) "system:dept:edit")
        delete-dept-handler (authorize (fn [request] (id-write user-repo :delete-dept! request)) "system:dept:remove")
        add-post-handler (authorize (fn [request] (body-write user-repo :add-post! request)) "system:post:add")
        update-post-handler (authorize (fn [request] (body-write user-repo :update-post! request)) "system:post:edit")
        delete-post-handler (authorize (fn [request] (query-ids-write user-repo :delete-posts! request "ids")) "system:post:remove")
        config-list-handler (with-repository-function admin-repo :list-configs
                              (partial management-list admin-repo :list-configs))
        config-detail-handler (with-repository-function admin-repo :get-config
                                (partial management-detail admin-repo :get-config "配置"))
        add-config-handler (authorize (fn [request] (body-write admin-repo :add-config! request)) "system:config:add")
        update-config-handler (authorize (fn [request] (path-id-body-write admin-repo :update-config! request :id)) "system:config:edit")
        refresh-config-handler (authorize (fn [_] (write-call admin-repo :delete-config-cache! [])) "system:config:edit")
        notice-list-handler (with-repository-function admin-repo :list-notices
                              (partial management-list admin-repo :list-notices))
        notice-detail-handler (with-repository-function admin-repo :get-notice
                                (partial management-detail admin-repo :get-notice "公告"))
        add-notice-handler (authorize (fn [request]
                                        (write-call admin-repo :add-notice!
                                                    [(body-params request)
                                                     (get-in request [:principal :user-id])]))
                                      "system:notice:add")
        update-notice-handler (authorize (fn [request] (path-id-body-write admin-repo :update-notice! request :id)) "system:notice:edit")
        delete-notice-handler (authorize (fn [request] (query-ids-write admin-repo :delete-notices! request "noticeIds")) "system:notice:remove")
        login-log-list-handler (with-repository-function admin-repo :list-login-logs
                                 (partial management-list admin-repo :list-login-logs))
        delete-login-log-handler (authorize (fn [request]
                                              (query-ids-write admin-repo :delete-login-logs! request "ids"))
                                            "monitor:logininfor:remove")
        operation-log-list-handler (with-repository-function admin-repo :list-operation-logs
                                     (partial management-list admin-repo :list-operation-logs))
        delete-operation-log-handler (authorize (fn [request]
                                                  (query-ids-write admin-repo :delete-operation-logs! request "operationIds"))
                                                "monitor:operlog:remove")
        job-list-handler (with-repository-function admin-repo :list-jobs
                           (partial management-list admin-repo :list-jobs))
        job-detail-handler (with-repository-function admin-repo :get-job
                             (partial management-detail admin-repo :get-job "任务"))
        job-log-list-handler (with-repository-function admin-repo :list-job-logs
                               (partial management-list admin-repo :list-job-logs))
        add-job-handler (authorize (fn [request] (body-write admin-repo :add-job! request)) "system:job:add")
        update-job-handler (authorize (fn [request] (path-id-body-write admin-repo :update-job! request :id)) "system:job:edit")
        update-job-status-handler (authorize (fn [request] (id-status-write admin-repo :update-job-status! request)) "system:job:changeStatus")
        delete-job-handler (authorize (fn [request] (query-ids-write admin-repo :delete-jobs! request "jobIds")) "system:job:remove")
        run-job-handler (authorize (fn [_] (response/unavailable)) "system:job:run")
        dict-data-list-handler (with-repository-function repo :list-data
                                 (partial management-list repo :list-data))
        dict-data-detail-handler (with-repository-function repo :get-data
                                   (partial management-detail repo :get-data "字典数据"))
        add-dict-type-handler (authorize (fn [request] (body-write repo :add-type! request)) "system:dict:add")
        update-dict-type-handler (authorize (fn [request] (path-id-body-write repo :update-type! request :dictId)) "system:dict:edit")
        delete-dict-type-handler (authorize (fn [request] (path-id-write repo :delete-type! request :dictId)) "system:dict:remove")
        add-dict-data-handler (authorize (fn [request] (body-write repo :add-data! request)) "system:dict:add")
        update-dict-data-handler (authorize (fn [request] (path-id-body-write repo :update-data! request :dictCode)) "system:dict:edit")
        delete-dict-data-handler (authorize (fn [request] (path-id-write repo :delete-data! request :dictCode)) "system:dict:remove")
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
                  {:get (authorize user-list-handler "system:user:list")
                   :post add-user-handler}]
                 ["/system/users/:id"
                  {:get (authorize user-detail-handler "system:user:query")
                   :put update-user-handler
                   :delete delete-users-handler}]
                 ["/system/users/:id/password"
                  {:put reset-user-password-handler}]
                 ["/system/users/:id/status"
                  {:put change-user-status-handler}]
                 ["/system/role/list"
                  {:get (authorize role-list-handler "system:role:list")}]
                 ["/system/role/:id"
                  {:get (authorize role-detail-handler "system:role:query")
                   :delete delete-role-handler}]
                 ["/system/role"
                  {:post add-role-handler
                   :put update-role-handler}]
                 ["/system/role/:id/status"
                  {:put change-role-status-handler}]
                 ["/system/menus"
                  {:get (authorize menu-management-list-handler "system:menu:list")
                   :post add-menu-handler}]
                 ["/system/menus/dropdown"
                  {:get (authorize menu-dropdown-handler nil)}]
                 ["/system/menus/:id"
                  {:get (authorize menu-detail-handler "system:menu:query")
                   :put update-menu-handler
                   :delete delete-menu-handler}]
                 ["/system/depts"
                  {:get (authorize dept-list-handler "system:dept:list")
                   :post add-dept-handler}]
                 ["/system/depts/dropdown"
                  {:get (authorize dept-dropdown-handler nil)}]
                 ["/system/dept/:id"
                  {:get (authorize dept-detail-handler "system:dept:query")
                   :put update-dept-handler
                   :delete delete-dept-handler}]
                 ["/system/post/list"
                  {:get (authorize post-list-handler "system:post:list")}]
                 ["/system/post/:id"
                  {:get (authorize post-detail-handler "system:post:query")}]
                 ["/system/post"
                  {:post add-post-handler
                   :put update-post-handler
                   :delete delete-post-handler}]
                 ["/system/user/profile"
                  {:get profile-handler
                   :put update-profile-handler}]
                 ["/system/user/profile/password"
                  {:put update-profile-password-handler}]
                 ["/system/user/profile/avatar"
                  {:post avatar-handler}]
                 ["/file/upload"
                  {:post file-upload-handler}]
                 ["/file/download"
                  {:get file-download-handler}]
                 ["/system/configs"
                  {:get (authorize config-list-handler "system:config:list")
                   :delete refresh-config-handler}]
                 ["/system/config/:id"
                  {:get (authorize config-detail-handler "system:config:query")
                   :put update-config-handler}]
                 ["/system/config"
                  {:post add-config-handler}]
                 ["/system/notices"
                  {:get (authorize notice-list-handler "system:notice:list")
                   :post add-notice-handler
                   :delete delete-notice-handler}]
                 ["/system/notices/:id"
                  {:get (authorize notice-detail-handler "system:notice:query")
                   :put update-notice-handler}]
                 ["/logs/loginLogs"
                  {:get (authorize login-log-list-handler "monitor:logininfor:list")
                   :delete delete-login-log-handler}]
                 ["/logs/operationLogs"
                  {:get (authorize operation-log-list-handler "monitor:operlog:list")
                   :delete delete-operation-log-handler}]
                 ["/monitor/onlineUsers"
                  {:get online-users-handler}]
                 ["/monitor/onlineUser/:tokenId"
                  {:delete force-online-logout-handler}]
                 ["/monitor/cacheInfo"
                  {:get cache-info-handler}]
                 ["/monitor/serverInfo"
                  {:get server-info-handler}]
                 ["/system/jobs"
                  {:get (authorize job-list-handler "system:job:list")
                   :post add-job-handler
                   :delete delete-job-handler}]
                 ["/system/jobs/invoke-targets"
                  {:get (authorize (fn [_] (response/ok [])) "system:job:list")}]
                 ["/system/jobs/logs"
                  {:get (authorize job-log-list-handler "system:job:list")}]
                 ["/system/jobs/:id"
                  {:get (authorize job-detail-handler "system:job:query")
                   :put update-job-handler}]
                 ["/system/jobs/:id/status"
                  {:put update-job-status-handler}]
                 ["/system/jobs/:id/run"
                  {:post run-job-handler}]
                 ["/system/dict/types"
                  {:get (authorize dict-list "system:dict:list")}]
                 ["/system/dict/type/list"
                  {:get (authorize dict-list "system:dict:list")}]
                 ["/system/dict/type/:dictId"
                  {:get (authorize dict-one "system:dict:query")
                   :put update-dict-type-handler
                   :delete delete-dict-type-handler}]
                 ["/system/dict/type"
                  {:post add-dict-type-handler}]
                 ["/system/dict/data/list"
                  {:get (authorize dict-data-list-handler "system:dict:list")}]
                 ["/system/dict/data/:dictCode"
                  {:get (authorize dict-data-detail-handler "system:dict:query")
                   :put update-dict-data-handler
                   :delete delete-dict-data-handler}]
                 ["/system/dict/data"
                  {:post add-dict-data-handler}]
                 ["/system/dict/data/type/:dictType"
                  {:get (authorize dict-data nil)}]]
                {:conflicts nil})
        default (ring/create-default-handler
                 {:not-found (fn [_] (response/fail 404 10001 "请求资源不存在"))
                  :method-not-allowed (fn [_] (response/fail 405 10002 "不支持的操作"))
                  :not-acceptable (fn [_] (response/fail 406 10002 "不支持的操作"))})]
    (-> (ring/ring-handler router default)
        params/wrap-params
        multipart/wrap-multipart-params
        wrap-json-body
        wrap-errors)))
