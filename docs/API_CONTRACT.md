# API contract baseline

Upstream: https://github.com/bruceblink/Keystone (main branch, 2026-11-10)

Reference controllers:
- keystone-framework-admin/.../controller/common/LoginController.java
- keystone-framework-admin/.../controller/system/SysDictController.java
- keystone-framework-admin/.../controller/system/SysUserController.java

Success response:
~~~json
{"code":0,"msg":"操作成功","data":null}
~~~

Paginated data:
~~~json
{"total":0,"rows":[]}
~~~

## Implemented vertical slice

- GET /health (original body shape)
- GET /system/dict/types
- GET /system/dict/type/list
- GET /system/dict/type/{dictId}
- GET /system/dict/data/type/{dictType}

Only the health contract is unit-tested against known expected values.
The dictionary endpoints use SQL that targets original table/column names, but require integration and parity tests.

## Not implemented

- POST /login, /refresh-token, /logout-refresh-token
- GET /captchaImage, /getConfig, /getLoginUserInfo, /getRouters
- User/role/menu/dept/post CRUD
- Dictionary create/update/delete, config, notice, log, files, jobs and monitoring

## Contract testing

Run the Java and Clojure backends with separate, identically migrated test databases.
Compare method/path/query/body, HTTP status, code/msg/data, paging, sorting, validation and auth decisions.
Allow explicit normalization only for non-deterministic IDs and timestamps.
Existing frontend should work without business-code changes before declaring a module complete.
