# 智鉴风控官 Web（zhijian-risk-web）

多组织版 Web 工作台，平迁桌面端 axiom-risk-agent 核心能力：

- 风险总览 / 线索台账 / 吹哨人 / 准入官 / 经营哨 / 规则库 / 问鉴控 / 机构名单 / 设置
- 登录注册、组织切换、成员 RBAC、组织隔离数据

## 技术栈

- 前端：Vue 3 + Vite + Pinia + Vue Router
- 后端：Spring Boot 3 + Spring Security JWT + JPA
- 数据库：默认 H2；MySQL 见下方「数据库」与 `db/mysql/schema.sql`


## 数据库

默认 profile 使用 **H2** 文件库（开发免装库）。

### 切换 MySQL

1. 建库并执行脚本：

```bash
mysql -u... -p -e "CREATE DATABASE zhijian_risk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u... -p zhijian_risk < backend/src/main/resources/db/mysql/schema.sql
# 或使用同内容副本：docs/sql/schema-mysql.sql
```

2. 配置连接（二选一，**自己填 URL/用户/密码**）：

```bash
cd backend/src/main/resources
cp application-mysql-local.yml.example application-mysql-local.yml
# 编辑 application-mysql-local.yml
```

或环境变量：

```bash
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/zhijian_risk?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export MYSQL_USERNAME='your_user'
export MYSQL_PASSWORD='your_password'
```

3. 启动（激活 mysql profile）：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# 或
java -jar target/zhijian-risk-web-0.1.0.jar --spring.profiles.active=mysql
```

可选：用 docker-compose 起本地 MySQL（会自动执行 schema.sql）：

```bash
docker compose up -d mysql
```

连接示例（仅当使用 compose 默认账号时）：

```yaml
url: jdbc:mysql://127.0.0.1:3306/zhijian_risk?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
username: zhijian
password: zhijian
```

MySQL 模式下 `ddl-auto` 默认为 `validate`（表由脚本创建，不让 Hibernate 改表）。需要临时自动建表可设 `JPA_DDL_AUTO=update`。

## 启动

```bash
# 后端
cd backend && ./mvnw spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

- 前端：http://localhost:5173
- 后端：http://localhost:8080

### 演示账号

| 用户 | 密码 | 说明 |
|------|------|------|
| admin | Admin@123 | 平台管理员 |
| alice | User@123 | 合作机构组操作员 |
| bob | User@123 | 运营辅助组复核员 |

登录后选择组织，即可使用各业务模块。在「设置」中配置大模型与百度搜索 Key 后，吹哨/准入/经营/问鉴控将走真实研判；未配置时提供演示模式结果。
