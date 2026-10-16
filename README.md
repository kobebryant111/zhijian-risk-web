# 智鉴风控官 Web（zhijian-risk-web）

多组织 Web 工作台，对应桌面端 axiom-risk-agent 的核心能力：

- 总览、风险吹哨、经营守护、准入瞭望、机构名单、规则库、问鉴控、设置
- 组织与成员（数据按组织隔离；创建组织和分配成员仅系统管理员）
- 线索台账（从总览或头像菜单进入）

## 技术栈

- 前端：Vue 3 + Vite + Pinia + Vue Router
- 后端：Spring Boot 3 + Spring Security JWT + MyBatis-Plus
- 数据库：MySQL。连接写在 `backend/src/main/resources/application.yml`，表结构见 `backend/src/main/resources/db/mysql/schema.sql`

## 接口

成功和失败都是同一套信封：

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code` 为 0 表示成功。失败时 `code` 与 HTTP 状态一致，`message` 是原因，`data` 为 `null`。
- Controller 方法直接返回 `ApiResult<T>`。全局异常处理器负责失败信封，访问日志由切面记录。
- 前端 axios 拦截器取出 `data`，页面按业务对象使用；错误读 `message`。

## 启动

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

- 前端：http://localhost:5173
- 后端：http://localhost:8080

用户表为空时才会写入演示账号。库里已有用户时不会覆盖，需要先注册。

| 用户 | 密码 | 说明 |
|------|------|------|
| admin | Admin@123 | 平台管理员，可创建组织并分配成员 |
| alice | User@123 | 演示操作员 |
| bob | User@123 | 演示复核员 |

登录后选择组织。在「设置」中配置大模型与百度搜索 Key 后，吹哨、经营守护、准入瞭望、问鉴控走真实研判。
