(ns keystone.user.repository
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def jdbc-options {:builder-fn rs/as-unqualified-lower-maps})

(defn- query [datasource sql params]
  (jdbc/execute! datasource (into [sql] params) jdbc-options))

(defn- first-row [datasource sql params]
  (first (query datasource sql params)))

(defn- parse-long-safe [value]
  (try (Long/parseLong (str value)) (catch Exception _ value)))

(defn- parse-meta [value]
  (try
    (if (str/blank? value) {} (json/read-value value))
    (catch Exception _ {})))

(defn- user-row->map [row]
  {:userId (:user_id row)
   :postId (:post_id row)
   :roleId (:role_id row)
   :deptId (:dept_id row)
   :username (:username row)
   :nickname (:nickname row)
   :userType (:user_type row)
   :email (:email row)
   :phoneNumber (:phone_number row)
   :sex (:sex row)
   :avatar (:avatar row)
   :password (:password row)
   :status (:status row)
   :loginIp (:login_ip row)
   :loginDate (some-> (:login_date row) str)
   :isAdmin (= 1 (long (or (:is_admin row) 0)))
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)
   :deptName (:dept_name row)
   :roleName (:role_name row)
   :roleKey (:role_key row)
   :postName (:post_name row)})

(defn- permissions [datasource user]
  (if (:isAdmin user)
    #{"*"}
    (->> (query datasource
                "SELECT DISTINCT m.permission
                   FROM sys_menu m
                   INNER JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
                  WHERE rm.role_id = ?
                    AND m.status = 1
                    AND m.deleted = 0
                    AND m.permission IS NOT NULL
                    AND m.permission <> ''"
                [(:roleId user)])
         (map :permission)
         (remove str/blank?)
         set)))

(def user-select
  "SELECT u.user_id, u.post_id, u.role_id, u.dept_id, u.username,
          u.nickname, u.user_type, u.email, u.phone_number, u.sex, u.avatar,
          u.password, u.status, u.login_ip, u.login_date, u.is_admin,
          u.remark, u.create_time, d.dept_name, r.role_name, r.role_key,
          p.post_name
     FROM sys_user u
     LEFT JOIN sys_dept d ON d.dept_id = u.dept_id AND d.deleted = 0
     LEFT JOIN sys_role r ON r.role_id = u.role_id AND r.deleted = 0
     LEFT JOIN sys_post p ON p.post_id = u.post_id AND p.deleted = 0
    WHERE u.deleted = 0 AND ")

(defn find-by-username [datasource username]
  (when-let [row (first-row datasource
                            (str user-select "u.username = ? LIMIT 1")
                            [username])]
    (let [user (user-row->map row)]
      (assoc user :permissions (permissions datasource user)))))

(defn find-by-id [datasource user-id]
  (when-let [row (first-row datasource
                            (str user-select "u.user_id = ? LIMIT 1")
                            [user-id])]
    (let [user (user-row->map row)]
      (assoc user :permissions (permissions datasource user)))))

(defn current-user [user]
  {:userInfo (dissoc user :password :permissions :roleKey :isAdmin)
   :roleKey (or (:roleKey user) "")
   :permissions (vec (or (:permissions user) []))})

(defn- menu-rows [datasource user]
  (if (:isAdmin user)
    (query datasource
           "SELECT m.menu_id, m.menu_name, m.menu_type, m.router_name,
                   m.parent_id, m.path, m.is_button, m.permission, m.meta_info,
                   m.status, m.remark, m.create_time
              FROM sys_menu m
             WHERE m.deleted = 0 AND m.status = 1
             ORDER BY m.parent_id ASC, m.menu_id ASC"
           [])
    (query datasource
           "SELECT DISTINCT m.menu_id, m.menu_name, m.menu_type, m.router_name,
                   m.parent_id, m.path, m.is_button, m.permission, m.meta_info,
                   m.status, m.remark, m.create_time
              FROM sys_menu m
              INNER JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
             WHERE rm.role_id = ? AND m.deleted = 0 AND m.status = 1
             ORDER BY m.parent_id ASC, m.menu_id ASC"
           [(:roleId user)])))

(defn- menu-row->dto [row]
  {:id (:menu_id row)
   :parentId (:parent_id row)
   :menuName (:menu_name row)
   :routerName (:router_name row)
   :path (:path row)
   :permission (:permission row)
   :menuType (:menu_type row)
   :isButton (= 1 (long (or (:is_button row) 0)))
   :status (:status row)
   :meta (parse-meta (:meta_info row))
   :createTime (some-> (:create_time row) str)})

(defn list-menus [datasource user]
  (mapv menu-row->dto (menu-rows datasource user)))

(defn menu-tree [datasource user]
  (let [rows (mapv menu-row->dto (menu-rows datasource user))
        by-parent (group-by :parentId rows)
        children (fn children [parent-id]
                   (->> (get by-parent parent-id)
                        (sort-by (juxt #(get-in % [:meta "rank"] 0) :id))
                        (mapv (fn [node]
                                (let [nested (children (:id node))]
                                  (cond-> node
                                    (seq nested) (assoc :children nested)))))))]
    (children 0)))

(defn dictionary-data-map [datasource]
  (let [rows (query datasource
                    "SELECT dict_type, dict_label, dict_value, list_class
                       FROM sys_dict_data
                      WHERE deleted = 0 AND status = 1
                      ORDER BY dict_type, dict_sort, dict_code"
                    [])]
    (reduce (fn [result row]
              (update result (:dict_type row) (fnil conj [])
                      {:label (:dict_label row)
                       :value (parse-long-safe (:dict_value row))
                       :cssTag (or (:list_class row) "") }))
            {}
            rows)))

(defn make-repository [datasource]
  {:find-by-username #(find-by-username datasource %)
   :find-by-id #(find-by-id datasource %)
   :current-user current-user
   :list-menus #(list-menus datasource %)
   :menu-tree #(menu-tree datasource %)
   :dictionary-data-map (fn [] (dictionary-data-map datasource))})
