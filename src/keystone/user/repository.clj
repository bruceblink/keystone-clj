(ns keystone.user.repository
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [org.mindrot.jbcrypt BCrypt]))

(def jdbc-options {:builder-fn rs/as-unqualified-lower-maps})

(declare first-row find-by-id)

(defn- query [datasource sql params]
  (jdbc/execute! datasource (into [sql] params) jdbc-options))

(defn- execute! [datasource sql params]
  (jdbc/execute! datasource (into [sql] params) jdbc-options))

(defn- body-value [body key]
  (or (get body key) (get body (keyword key))))

(defn- required-body [body key]
  (let [value (body-value body key)]
    (when (or (nil? value) (and (string? value) (str/blank? value)))
      (throw (ex-info (str key " is required") {:type :validation})))
    value))

(defn- generated-id [tx]
  (:id (first (execute! tx "SELECT LAST_INSERT_ID() AS id" []))))

(defn- insert-role-menus! [tx role-id menu-ids]
  (doseq [menu-id (distinct (map #(Long/parseLong (str %)) (or menu-ids [])))]
    (execute! tx "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)"
              [role-id menu-id])))

(defn- split-ids [value]
  (->> (str/split (str (or value "")) #",")
       (remove str/blank?)
       (map #(try (Long/parseLong %) (catch Exception _
                                      (throw (ex-info "Invalid ID" {:type :validation})))))
       distinct
       vec))

(defn add-user! [datasource body]
  (let [username (required-body body "username")
        nickname (or (body-value body "nickname") username)
        password (required-body body "password")]
    (jdbc/with-transaction [tx datasource]
      (execute! tx
                "INSERT INTO sys_user
                 (post_id, role_id, dept_id, username, nickname, user_type, email,
                  phone_number, sex, avatar, password, status, is_admin, remark,
                  create_time, deleted)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, NOW(), 0)"
                [(body-value body "postId") (body-value body "roleId")
                 (body-value body "deptId") username nickname
                 (or (body-value body "userType") 0) (or (body-value body "email") "")
                 (or (body-value body "phoneNumber") "") (or (body-value body "sex") 2)
                 (or (body-value body "avatar") "")
                 (BCrypt/hashpw password (BCrypt/gensalt 10))
                 (or (body-value body "status") 1) (or (body-value body "remark") "")]))
    nil))

(defn update-user! [datasource user-id body]
  (let [fields [["nickname" "nickname"] ["email" "email"] ["phoneNumber" "phone_number"]
                ["phone" "phone_number"] ["deptId" "dept_id"] ["postId" "post_id"]
                ["roleId" "role_id"] ["sex" "sex"] ["avatar" "avatar"]
                ["status" "status"] ["remark" "remark"]]
        selected (keep (fn [[source column]]
                         (when (some? (body-value body source))
                           [column (body-value body source)])) fields)]
    (when (empty? selected)
      (throw (ex-info "No user fields to update" {:type :validation})))
    (jdbc/with-transaction [tx datasource]
      (execute! tx
                (str "UPDATE sys_user SET "
                     (str/join ", " (map (fn [[column _]] (str column " = ?")) selected))
                     ", update_time = NOW() WHERE user_id = ? AND deleted = 0")
                (conj (mapv second selected) user-id)))
    nil))

(defn reset-user-password! [datasource user-id body]
  (let [password (required-body body "password")]
    (execute! datasource
              "UPDATE sys_user SET password = ?, update_time = NOW() WHERE user_id = ? AND deleted = 0"
              [(BCrypt/hashpw password (BCrypt/gensalt 10)) user-id])
    nil))

(defn delete-users! [datasource ids]
  (let [ids (split-ids ids)]
    (when (empty? ids) (throw (ex-info "userIds is required" {:type :validation})))
    (execute! datasource
              (str "UPDATE sys_user SET deleted = 1, update_time = NOW() WHERE user_id IN ("
                   (str/join "," (repeat (count ids) "?")) ")") ids)
    nil))

(defn change-user-status! [datasource user-id body]
  (let [status (required-body body "status")]
    (execute! datasource
              "UPDATE sys_user SET status = ?, update_time = NOW() WHERE user_id = ? AND deleted = 0"
              [status user-id])
    nil))

(defn add-role! [datasource body]
  (jdbc/with-transaction [tx datasource]
    (execute! tx
              "INSERT INTO sys_role
               (role_name, role_key, role_sort, data_scope, dept_id_set, status,
                remark, create_time, deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 0)"
              [(required-body body "roleName") (required-body body "roleKey")
               (or (body-value body "roleSort") 0) (or (body-value body "dataScope") 2)
               (str/join "," (or (body-value body "selectedDeptList") []))
               (or (body-value body "status") 1) (or (body-value body "remark") "")])
    (insert-role-menus! tx (generated-id tx) (body-value body "menuIds")))
  nil)

(defn update-role! [datasource body]
  (let [role-id (Long/parseLong (str (required-body body "roleId")))]
    (jdbc/with-transaction [tx datasource]
      (execute! tx
                "UPDATE sys_role SET role_name = ?, role_key = ?, role_sort = ?,
                        data_scope = ?, dept_id_set = ?, status = ?, remark = ?, update_time = NOW()
                   WHERE role_id = ? AND deleted = 0"
                [(required-body body "roleName") (required-body body "roleKey")
                 (or (body-value body "roleSort") 0) (or (body-value body "dataScope") 2)
                 (str/join "," (or (body-value body "selectedDeptList") []))
                 (or (body-value body "status") 1) (or (body-value body "remark") "") role-id])
      (execute! tx "DELETE FROM sys_role_menu WHERE role_id = ?" [role-id])
      (insert-role-menus! tx role-id (body-value body "menuIds")))
    nil))

(defn delete-role! [datasource role-id]
  (jdbc/with-transaction [tx datasource]
    (execute! tx "UPDATE sys_role SET deleted = 1, update_time = NOW() WHERE role_id = ?" [role-id])
    (execute! tx "DELETE FROM sys_role_menu WHERE role_id = ?" [role-id]))
  nil)

(defn change-role-status! [datasource role-id body]
  (execute! datasource "UPDATE sys_role SET status = ?, update_time = NOW() WHERE role_id = ? AND deleted = 0"
            [(required-body body "status") role-id])
  nil)

(defn add-menu! [datasource body]
  (let [meta (or (body-value body "meta") {})]
    (execute! datasource
              "INSERT INTO sys_menu
               (menu_name, menu_type, router_name, parent_id, path, is_button,
                permission, meta_info, status, remark, create_time, deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 0)"
              [(required-body body "menuName") (or (body-value body "menuType") 1)
               (or (body-value body "routerName") "") (or (body-value body "parentId") 0)
               (or (body-value body "path") "") (if (body-value body "isButton") 1 0)
               (or (body-value body "permission") "") (json/write-value-as-string meta)
               (or (body-value body "status") 1) (or (body-value body "remark") "")]))
  nil)

(defn update-menu! [datasource menu-id body]
  (let [meta (or (body-value body "meta") {})]
    (execute! datasource
              "UPDATE sys_menu SET menu_name = ?, menu_type = ?, router_name = ?,
                      parent_id = ?, path = ?, is_button = ?, permission = ?, meta_info = ?,
                      status = ?, update_time = NOW()
                 WHERE menu_id = ? AND deleted = 0"
              [(required-body body "menuName") (or (body-value body "menuType") 1)
               (or (body-value body "routerName") "") (or (body-value body "parentId") 0)
               (or (body-value body "path") "") (if (body-value body "isButton") 1 0)
               (or (body-value body "permission") "") (json/write-value-as-string meta)
               (or (body-value body "status") 1) menu-id]))
  nil)

(defn delete-menu! [datasource menu-id]
  (let [children (first-row datasource "SELECT COUNT(*) AS total FROM sys_menu WHERE parent_id = ? AND deleted = 0"
                             [menu-id])
        assigned (first-row datasource "SELECT COUNT(*) AS total FROM sys_role_menu WHERE menu_id = ?" [menu-id])]
    (when (pos? (long (:total children)))
      (throw (ex-info "存在子菜单不允许删除" {:type :conflict :code 10904})))
    (when (pos? (long (:total assigned)))
      (throw (ex-info "菜单已分配给角色，不允许" {:type :conflict :code 10905})))
    (execute! datasource "UPDATE sys_menu SET deleted = 1, update_time = NOW() WHERE menu_id = ?" [menu-id]))
  nil)

(defn add-dept! [datasource body]
  (execute! datasource
            "INSERT INTO sys_dept
             (parent_id, ancestors, dept_name, order_num, leader_name, phone, email,
              status, create_time, deleted)
             VALUES (?, '', ?, ?, ?, ?, ?, ?, NOW(), 0)"
            [(or (body-value body "parentId") 0) (required-body body "deptName")
             (or (body-value body "orderNum") 0) (or (body-value body "leaderName") "")
             (or (body-value body "phone") "") (or (body-value body "email") "")
             (or (body-value body "status") 1)])
  nil)

(defn update-dept! [datasource dept-id body]
  (execute! datasource
            "UPDATE sys_dept SET parent_id = ?, dept_name = ?, order_num = ?, leader_name = ?,
                    phone = ?, email = ?, status = ?, update_time = NOW()
               WHERE dept_id = ? AND deleted = 0"
            [(or (body-value body "parentId") 0) (required-body body "deptName")
             (or (body-value body "orderNum") 0) (or (body-value body "leaderName") "")
             (or (body-value body "phone") "") (or (body-value body "email") "")
             (or (body-value body "status") 1) dept-id])
  nil)

(defn delete-dept! [datasource dept-id]
  (let [children (first-row datasource "SELECT COUNT(*) AS total FROM sys_dept WHERE parent_id = ? AND deleted = 0"
                             [dept-id])
        users (first-row datasource "SELECT COUNT(*) AS total FROM sys_user WHERE dept_id = ? AND deleted = 0"
                          [dept-id])]
    (when (pos? (long (:total children)))
      (throw (ex-info "该部门存在下级部门不允许删除" {:type :conflict :code 10804})))
    (when (pos? (long (:total users)))
      (throw (ex-info "该部门存在关联的用户不允许删除" {:type :conflict :code 10805})))
    (execute! datasource "UPDATE sys_dept SET deleted = 1, update_time = NOW() WHERE dept_id = ?" [dept-id]))
  nil)

(defn add-post! [datasource body]
  (execute! datasource
            "INSERT INTO sys_post (post_code, post_name, post_sort, status, remark, create_time, deleted)
             VALUES (?, ?, ?, ?, ?, NOW(), 0)"
            [(required-body body "postCode") (required-body body "postName")
             (or (body-value body "postSort") 0) (or (body-value body "status") 1)
             (or (body-value body "remark") "")])
  nil)

(defn update-post! [datasource body]
  (execute! datasource
            "UPDATE sys_post SET post_code = ?, post_name = ?, post_sort = ?, status = ?,
                    remark = ?, update_time = NOW() WHERE post_id = ? AND deleted = 0"
            [(required-body body "postCode") (required-body body "postName")
             (or (body-value body "postSort") 0) (or (body-value body "status") 1)
             (or (body-value body "remark") "") (Long/parseLong (str (required-body body "postId")))])
  nil)

(defn delete-posts! [datasource ids]
  (let [ids (split-ids ids)]
    (when (empty? ids) (throw (ex-info "ids is required" {:type :validation})))
    (let [placeholders (str/join "," (repeat (count ids) "?"))]
      (when (pos? (long (:total (first-row datasource
                                           (str "SELECT COUNT(*) AS total FROM sys_user WHERE post_id IN (" placeholders ") AND deleted = 0")
                                           ids))))
        (throw (ex-info "职位已分配给用户，请先取消分配再删除" {:type :conflict :code 10703})))
      (execute! datasource (str "UPDATE sys_post SET deleted = 1, update_time = NOW() WHERE post_id IN ("
                               placeholders ")") ids)))
  nil)

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
   :dataScope (:data_scope row)
   :deptIdSet (split-ids (:dept_id_set row))
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
          r.data_scope, r.dept_id_set,
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

(defn- page-value [params key default]
  (try
    (max 1 (Long/parseLong (str (or (get params key) default))))
    (catch Exception _ default)))

(defn- page-result [datasource count-sql count-params rows-sql rows-params page size row-fn]
  {:total (or (:total (first-row datasource count-sql count-params)) 0)
   :rows (mapv row-fn (query datasource rows-sql (into rows-params [size (* (dec page) size)])))})

(defn- public-user [user]
  (dissoc user :password :permissions :roleKey :isAdmin))

(defn list-users [datasource params]
  (let [principal (:principal params)
        params (dissoc params :principal)
        scope (long (or (:data-scope principal) 1))
        principal-user-id (or (:user-id principal) (get-in principal [:claims "sub"]))
        principal-dept-id (or (:dept-id principal) (get-in principal [:claims "deptId"]))
        principal-dept-ids (or (:dept-ids principal) (get-in principal [:claims "deptIds"]) [])
        scope-condition (case scope
                          5 "u.user_id = ?"
                          3 "u.dept_id = ?"
                          4 "u.dept_id IN (SELECT dept_id FROM sys_dept WHERE FIND_IN_SET(?, ancestors) OR dept_id = ?)"
                          2 (when (seq principal-dept-ids)
                              (str "u.dept_id IN (" (str/join "," (repeat (count principal-dept-ids) "?")) ")"))
                          nil)
        scope-values (case scope
                       5 [principal-user-id]
                       3 [principal-dept-id]
                       4 [principal-dept-id principal-dept-id]
                       2 (vec principal-dept-ids)
                       [])
        page (page-value params "pageNum" 1)
        size (min 100 (page-value params "pageSize" 10))
        conditions (cond-> (cond-> ["u.deleted = 0"]
                             scope-condition (conj scope-condition))
                     (not (str/blank? (get params "username")))
                     (conj "u.username LIKE ?")
                     (not (str/blank? (get params "phoneNumber")))
                     (conj "u.phone_number LIKE ?")
                     (some? (get params "status"))
                     (conj "u.status = ?")
                     (some? (get params "deptId"))
                     (conj "u.dept_id = ?"))
        values (cond-> scope-values
                 (not (str/blank? (get params "username")))
                 (conj (str "%" (get params "username") "%"))
                 (not (str/blank? (get params "phoneNumber")))
                 (conj (str "%" (get params "phoneNumber") "%"))
                 (some? (get params "status"))
                 (conj (get params "status"))
                 (some? (get params "deptId"))
                 (conj (get params "deptId")))
        where (str/join " AND " conditions)
        count-sql (str "SELECT COUNT(*) AS total FROM sys_user u WHERE " where)
        rows-sql (str "SELECT u.user_id, u.post_id, u.role_id, u.dept_id, u.username,
                              u.nickname, u.user_type, u.email, u.phone_number, u.sex,
                              u.avatar, u.status, u.login_ip, u.login_date, u.is_admin,
                              u.remark, u.create_time, d.dept_name, r.role_name,
                              p.post_name
                         FROM sys_user u
                         LEFT JOIN sys_dept d ON d.dept_id = u.dept_id AND d.deleted = 0
                         LEFT JOIN sys_role r ON r.role_id = u.role_id AND r.deleted = 0
                         LEFT JOIN sys_post p ON p.post_id = u.post_id AND p.deleted = 0
                        WHERE " where
                        " ORDER BY u.user_id DESC LIMIT ? OFFSET ?")]
    (page-result datasource count-sql values rows-sql values page size
                 #(public-user (user-row->map %)))))

(defn get-user-detail [datasource user-id]
  (some-> (find-by-id datasource user-id)
          public-user))

(defn get-profile [datasource user-id]
  (when-let [user (find-by-id datasource user-id)]
    {:postName (:postName user)
     :roleName (:roleName user)
     :user (public-user user)}))

(defn update-profile! [datasource user-id body]
  (let [nickname (or (body-value body "nickName") (body-value body "nickname"))
        fields (cond-> []
                 (some? nickname) (conj ["nickname" nickname])
                 (some? (body-value body "email")) (conj ["email" (body-value body "email")])
                 (some? (body-value body "phoneNumber"))
                 (conj ["phone_number" (body-value body "phoneNumber")])
                 (some? (body-value body "sex")) (conj ["sex" (body-value body "sex")]))]
    (when (empty? fields)
      (throw (ex-info "No profile fields to update" {:type :validation})))
    (execute! datasource
              (str "UPDATE sys_user SET "
                   (str/join ", " (map (fn [[column _]] (str column " = ?")) fields))
                   ", update_time = NOW() WHERE user_id = ? AND deleted = 0")
              (conj (mapv second fields) user-id)))
  nil)

(defn update-profile-password! [datasource user-id body]
  (let [old-password (required-body body "oldPassword")
        new-password (required-body body "newPassword")
        user (find-by-id datasource user-id)]
    (when-not (and user (BCrypt/checkpw old-password (:password user)))
      (throw (ex-info "用户密码错误" {:type :invalid-credentials :code 10510})))
    (when (= old-password new-password)
      (throw (ex-info "用户新密码与旧密码相同" {:type :validation})))
    (execute! datasource
              "UPDATE sys_user SET password = ?, update_time = NOW() WHERE user_id = ? AND deleted = 0"
              [(BCrypt/hashpw new-password (BCrypt/gensalt 10)) user-id]))
  nil)

(defn update-avatar! [datasource user-id avatar]
  (execute! datasource
            "UPDATE sys_user SET avatar = ?, update_time = NOW() WHERE user_id = ? AND deleted = 0"
            [avatar user-id])
  nil)

(defn- role-row->dto [row selected-menu-ids]
  {:roleId (:role_id row)
   :roleName (:role_name row)
   :roleKey (:role_key row)
   :roleSort (:role_sort row)
   :dataScope (:data_scope row)
   :selectedDeptList (->> (str/split (or (:dept_id_set row) "") #",")
                           (remove str/blank?)
                           (map parse-long-safe)
                           vec)
   :selectedMenuList (vec selected-menu-ids)
   :status (:status row)
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(defn list-roles [datasource params]
  (let [page (page-value params "pageNum" 1)
        size (min 100 (page-value params "pageSize" 10))
        conditions (cond-> ["deleted = 0"]
                     (not (str/blank? (get params "roleName")))
                     (conj "role_name LIKE ?")
                     (not (str/blank? (get params "roleKey")))
                     (conj "role_key LIKE ?")
                     (some? (get params "status"))
                     (conj "status = ?"))
        values (cond-> []
                 (not (str/blank? (get params "roleName")))
                 (conj (str "%" (get params "roleName") "%"))
                 (not (str/blank? (get params "roleKey")))
                 (conj (str "%" (get params "roleKey") "%"))
                 (some? (get params "status"))
                 (conj (get params "status")))
        where (str/join " AND " conditions)
        count-sql (str "SELECT COUNT(*) AS total FROM sys_role WHERE " where)
        rows-sql (str "SELECT role_id, role_name, role_key, role_sort, data_scope,
                              dept_id_set, status, remark, create_time
                         FROM sys_role WHERE " where
                        " ORDER BY role_sort ASC, role_id ASC LIMIT ? OFFSET ?")]
    (page-result datasource count-sql values rows-sql values page size
                 #(role-row->dto % []))))

(defn get-role [datasource role-id]
  (when-let [row (first-row datasource
                            "SELECT role_id, role_name, role_key, role_sort, data_scope,
                                    dept_id_set, status, remark, create_time
                               FROM sys_role WHERE role_id = ? AND deleted = 0"
                            [role-id])]
    (let [menu-ids (query datasource "SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id"
                           [role-id])]
      (role-row->dto row (mapv :menu_id menu-ids)))))

(defn- menu-status [status] (if (= 1 (long (or status 0))) "正常" "停用"))
(defn- menu-type [type] ({1 "菜单" 2 "目录" 3 "内链" 4 "外链" 0 "按钮"} type))

(defn- menu-row->management-dto [row]
  (let [meta (parse-meta (:meta_info row))]
    {:id (:menu_id row)
     :parentId (:parent_id row)
     :menuName (:menu_name row)
     :routerName (:router_name row)
     :path (:path row)
     :permission (:permission row)
     :rank (get meta "rank")
     :icon (get meta "icon")
     :menuType (:menu_type row)
     :menuTypeStr (menu-type (:menu_type row))
     :isButton (= 1 (long (or (:is_button row) 0)))
     :status (:status row)
     :statusStr (menu-status (:status row))
     :meta meta
     :createTime (some-> (:create_time row) str)}))

(def menu-select
  "SELECT menu_id, menu_name, menu_type, router_name, parent_id, path,
          is_button, permission, meta_info, status, remark, create_time
     FROM sys_menu WHERE deleted = 0")

(defn list-menu-management [datasource params]
  (let [button (get params "isButton")
        rows (query datasource
                    (if (nil? button)
                      (str menu-select " ORDER BY parent_id ASC, menu_id ASC")
                      (str menu-select " AND is_button = ? ORDER BY parent_id ASC, menu_id ASC"))
                    (if (nil? button) [] [(if (= "true" (str/lower-case button)) 1 0)]))]
    (mapv menu-row->management-dto rows)))

(defn get-menu [datasource menu-id]
  (some-> (first-row datasource (str menu-select " AND menu_id = ?") [menu-id])
          menu-row->management-dto))

(defn menu-dropdown [datasource]
  (let [rows (mapv menu-row->management-dto
                   (query datasource (str menu-select " AND is_button = 0 ORDER BY parent_id, menu_id") []))
        grouped (group-by :parentId rows)
        build (fn build [parent-id]
                (mapv (fn [row]
                        (cond-> {:id (:id row) :parentId (:parentId row) :label (:menuName row)}
                          (seq (get grouped (:id row)))
                          (assoc :children (build (:id row)))))
                      (get grouped parent-id)))]
    (build 0)))

(defn- dept-row->dto [row]
  {:id (:dept_id row)
   :parentId (:parent_id row)
   :deptName (:dept_name row)
   :orderNum (:order_num row)
   :leaderName (:leader_name row)
   :phone (:phone row)
   :email (:email row)
   :status (:status row)
   :statusStr (if (= 1 (long (or (:status row) 0))) "正常" "停用")
   :createTime (some-> (:create_time row) str)})

(defn list-depts [datasource params]
  (let [conditions (cond-> ["deleted = 0"]
                     (some? (get params "parentId")) (conj "parent_id = ?"))
        values (cond-> [] (some? (get params "parentId")) (conj (get params "parentId")))]
    (mapv dept-row->dto
          (query datasource
                 (str "SELECT dept_id, parent_id, dept_name, order_num, leader_name,
                              phone, email, status, create_time FROM sys_dept WHERE "
                      (str/join " AND " conditions)
                      " ORDER BY parent_id, order_num, dept_id")
                 values))))

(defn get-dept [datasource dept-id]
  (some-> (first-row datasource
                     "SELECT dept_id, parent_id, dept_name, order_num, leader_name,
                             phone, email, status, create_time
                        FROM sys_dept WHERE dept_id = ? AND deleted = 0"
                     [dept-id])
          dept-row->dto))

(defn dept-dropdown [datasource]
  (let [rows (list-depts datasource {})
        grouped (group-by :parentId rows)
        build (fn build [parent-id]
                (mapv (fn [row]
                        (cond-> {:id (:id row) :parentId (:parentId row) :label (:deptName row)}
                          (seq (get grouped (:id row)))
                          (assoc :children (build (:id row)))))
                      (get grouped parent-id)))]
    (build 0)))

(defn- post-row->dto [row]
  {:postId (:post_id row)
   :postCode (:post_code row)
   :postName (:post_name row)
   :postSort (:post_sort row)
   :status (:status row)
   :statusStr (if (= 1 (long (or (:status row) 0))) "正常" "停用")
   :remark (:remark row)
   :createTime (some-> (:create_time row) str)})

(defn list-posts [datasource params]
  (let [page (page-value params "pageNum" 1)
        size (min 100 (page-value params "pageSize" 10))
        conditions (cond-> ["deleted = 0"]
                     (not (str/blank? (get params "postCode"))) (conj "post_code LIKE ?")
                     (not (str/blank? (get params "postName"))) (conj "post_name LIKE ?")
                     (some? (get params "status")) (conj "status = ?"))
        values (cond-> []
                 (not (str/blank? (get params "postCode")))
                 (conj (str "%" (get params "postCode") "%"))
                 (not (str/blank? (get params "postName")))
                 (conj (str "%" (get params "postName") "%"))
                 (some? (get params "status")) (conj (get params "status")))
        where (str/join " AND " conditions)]
    (page-result datasource
                 (str "SELECT COUNT(*) AS total FROM sys_post WHERE " where)
                 values
                 (str "SELECT post_id, post_code, post_name, post_sort, status, remark,
                              create_time FROM sys_post WHERE " where
                      " ORDER BY post_sort ASC, post_id ASC LIMIT ? OFFSET ?")
                 values page size post-row->dto)))

(defn get-post [datasource post-id]
  (some-> (first-row datasource
                     "SELECT post_id, post_code, post_name, post_sort, status, remark,
                             create_time FROM sys_post WHERE post_id = ? AND deleted = 0"
                     [post-id])
          post-row->dto))

(defn make-repository [datasource]
  {:find-by-username #(find-by-username datasource %)
   :find-by-id #(find-by-id datasource %)
   :current-user current-user
   :list-menus #(list-menus datasource %)
   :menu-tree #(menu-tree datasource %)
   :dictionary-data-map (fn [] (dictionary-data-map datasource))
   :list-users #(list-users datasource %)
   :get-user-detail #(get-user-detail datasource %)
   :list-roles #(list-roles datasource %)
   :get-role #(get-role datasource %)
   :list-menu-management #(list-menu-management datasource %)
   :get-menu #(get-menu datasource %)
   :menu-dropdown (fn [] (menu-dropdown datasource))
   :list-depts #(list-depts datasource %)
   :get-dept #(get-dept datasource %)
   :dept-dropdown (fn [] (dept-dropdown datasource))
   :list-posts #(list-posts datasource %)
   :get-post #(get-post datasource %)
   :get-profile #(get-profile datasource %)
   :update-profile! (fn [[id body]] (update-profile! datasource id body))
   :update-profile-password! (fn [[id body]] (update-profile-password! datasource id body))
   :update-avatar! (fn [[id avatar]] (update-avatar! datasource id avatar))
   :add-user! #(add-user! datasource %)
   :update-user! (fn [[id body]] (update-user! datasource id body))
   :reset-user-password! (fn [[id body]] (reset-user-password! datasource id body))
   :delete-users! #(delete-users! datasource %)
   :change-user-status! (fn [[id body]] (change-user-status! datasource id body))
   :add-role! #(add-role! datasource %)
   :update-role! #(update-role! datasource %)
   :delete-role! #(delete-role! datasource %)
   :change-role-status! (fn [[id body]] (change-role-status! datasource id body))
   :add-menu! #(add-menu! datasource %)
   :update-menu! (fn [[id body]] (update-menu! datasource id body))
   :delete-menu! #(delete-menu! datasource %)
   :add-dept! #(add-dept! datasource %)
   :update-dept! (fn [[id body]] (update-dept! datasource id body))
   :delete-dept! #(delete-dept! datasource %)
   :add-post! #(add-post! datasource %)
   :update-post! #(update-post! datasource %)
   :delete-posts! #(delete-posts! datasource %)})
