# 巧见 · 手作市集（Cloud Mall）

一个 **手作 / 生鲜电商全栈微服务实战项目**：Spring Boot 3 + Spring Cloud 后端、Vue 3 双前端（C 端商城 + B 端后台），外加一个 Python **AI 导购助手**（LangGraph Agent + RAG + 文生图）。

> 面向微服务学习者：项目把「注册中心 → 网关 → 服务调用 → 熔断降级 → 担保交易 → 支付回调 → 实时 IM → LLM Agent」这条链路全部跑通，每个知识点都能在代码里找到落点。
>
> 完整文档（启动步骤、Nacos 配置、验证清单）见 **[docs/微服务实战指南.md](docs/微服务实战指南.md)**。

---

## 功能总览

| 端 | 能力 |
|---|---|
| **C 端商城** `web-ui:5173` | 注册登录（密码 / 手机验证码）、商品搜索、购物车、下单结算、**支付宝沙箱支付 + 余额充值**、在线客服、**AI 导购助手**（SSE 流式 / 文生图） |
| **B 端后台** `admin-ui:5174` | 仪表盘、商品管理（含图片上传）、订单管理、用户管理、RBAC 角色权限、操作日志 |
| **AI 助手** `ai-service:8000` | 意图路由 + 工具调用多轮 Agent、RAG 检索增强、模型分级与护栏；**无 Key 自动降级 mock，项目仍可跑通** |

---

## 架构

```
        web-ui:5173              admin-ui:5174
                 └──────────┬──────────┘
                            ▼
                  ┌────────────────────┐
                  │  Gateway :8080     │  Sa-Token 鉴权 / 路由 / 负载均衡
                  └─────┬──────────────┘
        ┌───────────┬───┴────┬─────────┬──────────┬──────────┐
        ▼           ▼        ▼         ▼          ▼          ▼
   user-service product-  order-   account-   admin-     chat-service
      :8081     service   service  service    service       :8087
                :8082     :8084     :8083      :8085
        │                    │         │          │            │
        │   OpenFeign + Resilience4j  │          │        WebSocket
        └──────── 跨服务调用 ──────────┘          │       + RocketMQ
                                                 │
    Python ai-service :8000 ──HTTP──► Gateway ────┘（工具回调走网关，不绕过鉴权）

依赖中间件：Nacos :8848 · MySQL :3306 · Redis :6379 · RocketMQ :9876（可选）· Elasticsearch :9200（可选）
```

| 服务 | 端口 | 库 | 说明 |
|---|---|---|---|
| gateway | 8080 | — | 统一入口，路由前缀 `/user` `/product` `/order` `/cart` `/account` `/admin` `/chat` `/uploads` `/alipay` |
| user-service | 8081 | `user_db` | 用户、登录注册 |
| product-service | 8082 | `product_db` | 商品、库存、ES 检索 |
| account-service | 8083 | `account_db` | 余额、充值、支付宝支付与回调验签 |
| order-service | 8084 | `order_db` | 购物车、订单编排（Feign 聚合下游） |
| admin-service | 8085 | `admin_db` | 后台系统管理，聚合下游数据 |
| chat-service | 8087 | `chat_db` | 客服 WebSocket 会话、消息持久化 |
| ai-service | 8000 | — | AI 导购 Agent，SSE 流式输出 |

---

## 技术栈

Spring Boot 3.2 · Spring Cloud 2023 · Spring Cloud Alibaba 2023 · Nacos 2.x · Spring Cloud Gateway · OpenFeign + LoadBalancer · Resilience4j · Sa-Token · MyBatis-Plus + MySQL 8 · Redis · RocketMQ · Elasticsearch · 支付宝沙箱 SDK · WebSocket + SSE · Vue 3 + Vite + Pinia · LangGraph + FastAPI

---

## 微服务知识点索引

每个知识点解决什么问题、在本项目哪段代码里体现：

| 知识点 | 解决什么问题 | 代码落点 |
|---|---|---|
| Nacos 注册与发现 | 服务 IP 漂移后不用改配置，按服务名寻址 | 各服务 `application.yml` 的 `spring.cloud.nacos.discovery` |
| Nacos 配置中心 | 配置集中管理 + 动态刷新 | 各服务 `bootstrap.yml`，共享配置 `common-config.yaml` |
| Gateway 统一入口 | 前端只认一个域名，鉴权收口 | `gateway/application.yml` 的 `routes`；**运行时以 Nacos `gateway.yaml` 为准** |
| OpenFeign | 跨服务调用像调本地方法 | `order/feign/UserFeignClient.java` 等 |
| Resilience4j 熔断 | 下游挂了不拖垮上游 | `order-service/application.yml` + `order/feign/fallback/` |
| 统一响应 & 全局异常 | 前后端契约一致 | `common/result/R.java`、`GlobalExceptionHandler.java` |
| 防超卖 | 高并发下库存不为负 | `ProductServiceImpl#deductStock`：Redis 锁 + SQL `stock >= quantity` 原子更新 |
| 担保交易状态机 | 钱先冻在平台，确认收货才打款 | `order/constant/OrderStatus.java`；`balance` / `frozenAmount` 分离 |
| 超时关单 | 未支付订单不长期占库存 | `order/task/OrderTimeoutCancelTask` |
| 幂等 | 支付回调重复到达不重复入账 | `account/controller/AlipayCallbackController` |
| AOP 审计日志 | 后台操作可追溯 | `admin/aspect/AdminOperationAspect` |

---

## 快速开始

### 1. 环境

JDK 17+ · Maven 3.8+ · MySQL 8+ · Redis 6+ · Nacos 2.x（standalone）· Node 18+（前端）· Python 3.10+（AI）
> RocketMQ / Elasticsearch 可选，不启动则相关链路走降级逻辑。

### 2. 建库

```bash
mysql -u root -p < sql/schema.sql                  # 用户/商品/订单/账户库
mysql -u root -p < sql/admin-schema.sql            # 后台管理库
mysql -u root -p < sql/chat-schema.sql             # 聊天会话库
mysql -u root -p < sql/alipay_recharge.sql         # 充值流水
mysql -u root -p < sql/insert-sample-products.sql  # 示例商品
mysql -u root -p < sql/kefu-account.sql            # 平台客服账号
```

### 3. Nacos（**必做，否则服务互相找不到**）

1. 新建命名空间，ID 填 `4fbcf13a-853a-4f73-90d8-5569f59d827d`（项目未使用默认 `public`）。
2. 在该命名空间下建配置：`common-config.yaml`（**必建**，公共数据源/MyBatis-Plus/Resilience4j）、`gateway.yaml`（**必建**，网关路由以它为准）。

> 配置模板见根目录 `nacos-config.txt`，按注释拆分粘贴。

### 4. 启动

```bash
nacos/bin/startup.cmd -m standalone     # 0) 注册中心
mvn clean install -DskipTests           # 1) common 模块必须先装
cd gateway && mvn spring-boot:run       # 2) 先启网关
mvn -pl user-service,product-service,order-service,account-service,admin-service,chat-service spring-boot:run
cd web-ui   && npm install && npm run dev     # :5173
cd admin-ui && npm install && npm run dev     # :5174
cd ai-service && python main.py               # :8000
```

### 5. 验证知识点

| 想验证 | 怎么验 |
|---|---|
| 服务注册 | Nacos 控制台看 6 个业务服务是否在线 |
| 网关路由 | `localhost:8081/user/get/1` 与 `localhost:8080/user/get/1` 结果一致 |
| **熔断降级** | 停掉 `product-service` 再下单，应快速返回降级结果而非 500 |
| 防超卖 | `jmeter/stock_deduct_test.jmx` 并发压测，库存不为负 |

---

## 安全说明

- 代码中**不含任何真实凭据**：数据库密码、管理员密码、支付宝密钥全部通过环境变量注入。
- 本地覆盖配置请用 `application-local.yml` / `.env`（已被 `.gitignore` 忽略）。
- 密钥一旦误提交，正确做法是到平台**轮换/作废**，而非仅删除提交——Git 历史仍会保留。
- 支付回调必须**验签 + 幂等**；演示使用支付宝沙箱，不产生真实资金流。

---

## 已知边界

- 图片上传落本地磁盘，生产应换对象存储（`FileStorageService` 已留扩展点）。
- 未引入 Seata，跨服务一致性靠本地事务 + 可靠消息 + 幂等兜底。
- 购物车结算按 B2C 自营处理（一次结算一张订单）；改 C2C 多卖家需在 `CartService#checkout` 拆单。

---

完整实战文档见 **[docs/微服务实战指南.md](docs/微服务实战指南.md)**。本项目仅用于学习交流，二次分发请替换示例数据与密钥。
