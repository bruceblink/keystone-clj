(ns keystone.security
  (:require [clojure.string :as str]
            [keystone.response :as response]))

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
