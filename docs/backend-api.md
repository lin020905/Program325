# 会议室预约系统后端接口

页面与 API 同源部署。开发时若页面由独立开发服务器提供，通过开发服务器将 `/api` 代理到 Spring Boot 的 8080 端口。浏览器同源 `fetch` 会自动携带登录后的 `JSESSIONID`。请求体使用 JSON 和 `Content-Type: application/json`。

成功响应的格式为 `{"code":0,"message":"成功","data":...}`。错误返回 `{"code":非零整数,"message":"可展示的错误原因","data":null}`。未登录为 HTTP 401，权限不足为 403，资源不存在为 404，入参非法为 400，业务冲突为 409。时段冲突的业务码为 40901，容纳人数不足为 40002。页面可直接展示 `message`。

## 登录会话

| 方法与路径 | 参数 | data |
| --- | --- | --- |
| POST /api/login | `{"username":"alice","password":"用户密码"}` | `user_id`、`username`、`role` |
| GET /api/me | 无 | 当前登录用户 |
| POST /api/logout | 无 | null |

角色值为 `USER` 或 `ADMIN`。管理员也可使用个人预约功能。用户和审批人编号来自会话。

## 会议室

| 方法与路径 | 权限 | 请求及结果 |
| --- | --- | --- |
| GET /api/rooms | 登录 | 可选查询参数 `start_time`、`end_time`、`attendees`。带时段须同时提供起止时间，省略人数按 1 人筛选。无筛选返回所有未删除会议室，包括暂停预约的会议室 |
| GET /api/rooms/{id} | 登录 | 返回会议室详情 |
| POST /api/rooms | 管理员 | 用下方对象创建，HTTP 201，data 为 null |
| PUT /api/rooms/{id} | 管理员 | 用下方对象修改并返回会议室。将 `bookable` 设为 false 即可暂停预约 |
| DELETE /api/rooms/{id} | 管理员 | 逻辑删除，保留历史。尚有未结束的待审批或已通过预约时返回 409 |

会议室对象具有 `room_id`、`name`、`room_no`、`capacity`、`area`、`purpose`、`bookable`。新增和修改时提交以下对象。新增时 `bookable` 缺省为 true，修改时缺省保留原值。修改容量不得低于尚未结束的有效预约人数。房号唯一，包括已逻辑删除记录。

```json
{"name":"第一会议室","room_no":"A101","capacity":20,"area":35.50,"purpose":"项目会议","bookable":true}
```

## 预约及审批

| 方法与路径 | 权限 | 请求及结果 |
| --- | --- | --- |
| POST /api/reservations | 登录 | 提交下方预约对象，HTTP 201，返回 PENDING 预约 |
| GET /api/reservations/me | 登录 | 返回当前用户的预约列表 |
| GET /api/reservations/{id} | 本人或管理员 | 返回预约详情 |
| GET /api/reservations | 管理员 | 返回全部预约。可选查询参数 `room_id`、`status`、`from`、`until`，时间条件筛选与区间重叠的预约 |
| POST /api/reservations/{id}/cancel | 本人 | 无请求体。返回取消后的预约 |
| POST /api/reservations/{id}/approval | 管理员 | `{"result":"APPROVED"}` 或 `{"result":"REJECTED","reason":"驳回理由"}`，返回处理后的预约 |

```json
{"room_id":1,"subject":"项目讨论会","attendees":10,"start_time":"2030-01-10T09:00:00","end_time":"2030-01-10T11:00:00"}
```

预约响应字段为 `reservation_id`、`user_id`、`room_id`、`username`、`room_name`、`room_no`、`subject`、`attendees`、`start_time`、`end_time`、`status`、`created_at`、`cancelled_at`、`approver_id`、`approval_result`、`reason`、`approved_at`、`confirmed_at`。未审批或未确认时对应字段为 null。使用确认是报告中的可选功能，本期只读取已有确认记录。

时间使用北京时间的 ISO 本地时间字符串，例如 `2030-01-10T09:00:00`。预约以整点划分，使用小时须在同一天。允许 23:00 至次日 00:00。开始时间须晚于当前时间。PENDING 和 APPROVED 均占用时段，相邻预约可衔接。本人可取消尚未结束且未确认使用的有效预约。审批通过时再次检查时段、容量和会议室状态。审批记录与预约状态在同一事务中写入。

## 启动与账号

使用 JDK 17。在空库执行 C 提供的 `src/main/resources/db/schema.sql`。在 IDEA 启动配置中设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，然后运行 `Program325Application`。数据库验收可在初始化后运行 `mvn test`。

账号由数据库侧预先配置。登录支持 BCrypt 格式 `$2a$`、`$2b$`、`$2y$`，也接受带 `{bcrypt}` 前缀的散列。请使用应用依赖 `spring-security-crypto` 中的 `BCryptPasswordEncoder.encode(password)` 生成散列，写入 `Users.password_hash`，角色写为 `USER` 或 `ADMIN`。应用账号密码与 MySQL 连接密码分别配置。
