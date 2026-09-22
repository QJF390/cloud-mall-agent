# 微服务实战框架 —— Nacos 版

## 项目概述

这是一个**微服务骨架项目**，包含基础框架和配置，以及**空的业务骨架文件**（entity / mapper / service / controller / feign / dto）。

所有骨架文件已创建好，你只需在 `TODO` 注释处填充业务逻辑，通过动手实战来熟练微服务核心技术。

---

## 架构图

```
                         ┌─────────────┐
                         │   Nacos      │
                         │ :8848        │
                         │ 注册+配置中心 │
                         └──────┬───────┘
                                │ 服务注册/发现
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│ user-service │        │product-service│        │account-service│
│    :8081     │        │    :8082     │         │    :8083     │
└──────────────┘        └──────────────┘        └──────────────┘
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │ Feign 远程调用
                                ▼
                       ┌────────────────┐
                       │  order-service │
                       │     :8084      │
                       │ Feign + 熔断   │
                       └────────────────┘
                                ▲
                                │
                       ┌────────────────┐
                       │  API 网关       │
                       │  Gateway:8080  │
                       │ 路由/负载/熔断   │
                       └────────────────┘
```

---

## 涉及的核心微服务知识点

| 知识点 | 你的学习目标 |
|---|---|
| **Nacos 服务注册与发现** | 服务启动自动注册到 Nacos，通过服务名互相发现 |
| **Nacos 配置中心** | 配置统一管理、动态刷新（bootstrap.yml） |
| **API 网关 Gateway** | 统一入口路由，`lb://service-name` 负载均衡 |
| **OpenFeign 远程调用** | 像调用本地方法一样调用远程服务 |
| **LoadBalancer 负载均衡** | 请求自动分发到多个实例 |
| **Resilience4j 熔断降级** | 下游服务挂了你也不崩 |
| **统一响应 & 全局异常** | `R<T>` 封装 + `@RestControllerAdvice` |
| **独立数据库** | 每个微服务自己的 H2 数据库 |

---

## 项目结构（骨架文件已创建，TODO 等你填）

```
exdemo/
├── pom.xml                            # 父工程（版本管理）
├── common/                            # 公共模块（你一般不用改）
│   └── src/main/java/.../common/
│       ├── result/R.java              #   统一响应 {code, message, data}
│       └── exception/
│           ├── BizException.java      #   业务异常类
│           └── GlobalExceptionHandler.java
├── gateway/                           # API 网关
│   └── src/main/java/.../gateway/
│       ├── GatewayApplication.java    #   @EnableDiscoveryClient
│       ├── config/CorsConfig.java     #   跨域配置
│       └── controller/FallbackController.java
├── user-service/                      # 用户服务（骨架已创建，填 TODO）
│   └── src/main/java/.../user/
│       ├── entity/User.java           #   MyBatis-Plus 实体
│       ├── mapper/UserMapper.java     #   继承 BaseMapper
│       ├── service/UserService.java   #   继承 IService
│       ├── service/impl/UserServiceImpl.java
│       └── controller/UserController.java
├── product-service/                   # 商品服务（同上）
│   ├── entity/Product.java
│   ├── mapper/ProductMapper.java
│   ├── service/ProductService.java
│   ├── service/impl/ProductServiceImpl.java
│   └── controller/ProductController.java
├── order-service/                     # 订单服务（重点：Feign）
│   ├── entity/Order.java
│   ├── mapper/OrderMapper.java
│   ├── service/OrderService.java
│   ├── service/impl/OrderServiceImpl.java
│   ├── controller/OrderController.java
│   ├── dto/UserDTO.java               #   Feign 跨服务 DTO
│   ├── dto/ProductDTO.java
│   ├── dto/AccountDTO.java
│   ├── feign/UserFeignClient.java     #   @FeignClient(name="user-service")
│   ├── feign/ProductFeignClient.java
│   ├── feign/AccountFeignClient.java
│   └── feign/fallback/                #   熔断降级类
│       ├── UserFeignFallback.java
│       ├── ProductFeignFallback.java
│       └── AccountFeignFallback.java
└── account-service/                   # 账户服务（同上）
    ├── entity/Account.java
    ├── mapper/AccountMapper.java
    ├── service/AccountService.java
    ├── service/impl/AccountServiceImpl.java
    └── controller/AccountController.java
```

---

## 环境准备

| 组件 | 说明 |
|---|---|
| JDK 17+ | 必须 |
| Maven 3.8+ | 必须 |
| Nacos 2.x | [下载 Nacos](https://github.com/alibaba/nacos/releases)，解压后 `bin/startup.cmd -m standalone` |

---

## 启动顺序

```bash
# 0. 启动 Nacos（单机模式）
cd nacos/bin
startup.cmd -m standalone
# 访问 http://127.0.0.1:8848/nacos 确认启动成功

# 1. 编译项目
cd d:/demo/exdemo
mvn clean install -DskipTests

# 2. 启动网关（必须先启动，否则路由不通）
cd gateway
mvn spring-boot:run

# 3. 启动业务服务（可以并行启动，任意顺序）
cd ../user-service     && mvn spring-boot:run
cd ../product-service  && mvn spring-boot:run
cd ../account-service  && mvn spring-boot:run
cd ../order-service    && mvn spring-boot:run
```

启动后访问 Nacos 控制台 `http://127.0.0.1:8848/nacos` → 服务管理 → 服务列表，应该看到 5 个服务都在线。

---

## 你现在需要做的事情

按照微服务开发流程，逐一手写各模块的业务逻辑（骨架文件已创建，只需填 TODO）：

### 第 1 步：从 user-service 开始（最简单）

在 `user-service` 的骨架文件里填充业务逻辑：

```
user/
├── entity/User.java          # MyBatis-Plus 实体（@TableName + @TableId）
├── mapper/UserMapper.java    # 继承 BaseMapper<T>，复杂 SQL 写 @Select
├── service/UserService.java  # 继承 IService<T>
├── service/impl/UserServiceImpl.java  # 继承 ServiceImpl<Mapper, T>
└── controller/UserController.java     # REST API
```

### 第 2 步：写 product-service

同样建 entity → **mapper** → service → controller，在骨架上填充逻辑

> MyBatis-Plus 比 JPA 更简洁：Mapper 继承 `BaseMapper<T>` 即可拥有全套 CRUD，无需手写 SQL。

### 第 3 步：写 account-service

同上

### 第 4 步：写 order-service（重点：Feign 远程调用）

```
order/
├── entity/Order.java
├── mapper/OrderMapper.java        # MyBatis-Plus BaseMapper
├── dto/                           # 跨服务 DTO
│   ├── UserDTO.java
│   ├── ProductDTO.java
│   └── AccountDTO.java
├── feign/                         # Feign 客户端
│   ├── UserFeignClient.java       # @FeignClient(name="user-service")
│   ├── ProductFeignClient.java
│   ├── AccountFeignClient.java
│   └── fallback/                  # 熔断降级
│       ├── UserFeignFallback.java
│       ├── ProductFeignFallback.java
│       └── AccountFeignFallback.java
├── service/OrderService.java      # 编排逻辑（Feign 调用 + 事务）
└── controller/OrderController.java
```

### 第 5 步：在 Nacos 配置中心添加配置（进阶）

在 Nacos 控制台创建配置：
- Data ID: `user-service.yaml`
- Group: `DEFAULT_GROUP`
- 内容：数据库连接、自定义业务参数等

---

## Feign 远程调用示例（骨架）

```java
// 在 order-service 中调用 user-service
@FeignClient(name = "user-service", fallback = UserFeignFallback.class)
public interface UserFeignClient {

    @GetMapping("/user/{id}")
    R<UserDTO> getUserById(@PathVariable("id") Long id);
}
```

关键点：
- `name = "user-service"` → 与 Nacos 中注册的服务名一致
- `fallback` → 熔断降级类
- 方法签名对应被调用服务的 Controller 接口

---

## Nacos 配置中心使用（可选）

你的 `bootstrap.yml` 已经配好了，在 Nacos 控制台创建配置后可以：
- 动态修改配置无需重启
- 用 `@RefreshScope` + `@Value` 实现热更新
- 不同环境（dev/test/prod）用不同 namespace 隔离

---

## 核心文件速查

| 要看的文件 | 重点 |
|---|---|
| `gateway/application.yml` | 路由规则 `lb://` + CircuitBreaker 熔断 |
| `order-service/application.yml` | Feign 超时 + Resilience4j 配置 |
| `common/R.java` | 统一响应格式，所有接口都用它 |
| `common/GlobalExceptionHandler.java` | 全局异常拦截 |

---

## 学习建议

1. **先不要写 Feign**，把 user-service 写完，用 Postman 通过网关 `http://localhost:8080/api/user/xxx` 调通
2. 再写 product-service、account-service，同样各自治通
3. 最后写 order-service，练习 Feign 跨服务调用
4. 尝试停掉 product-service 再调 order 接口，观察熔断降级效果
5. 尝试在 Nacos 配置中心加配置，看 `@RefreshScope` 是否生效
