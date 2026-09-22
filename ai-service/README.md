# ai-service

「拾光志」手作市集的 AI 服务（Python / FastAPI）。

## 定位

独立的 Python 服务，通过网关 `/ai/**` 暴露：
- `POST /ai/chat`：SSE 流式对话
- `POST /ai/image/generate`：受理图片生成任务（异步占位）

身份鉴权由网关 + Sa-Token 统一负责，本服务只透传 `satoken`。

## 启动

```powershell
cd d:/demo/exdemo/ai-service

# 1. 创建虚拟环境
python -m venv .venv

# 2. 激活（PowerShell）
.venv\Scripts\Activate.ps1

# 3. 安装依赖
pip install -r requirements.txt

# 4. 复制并编辑环境变量
Copy-Item .env.example .env
# 用编辑器打开 .env，填入 LLM_API_KEY

# 5. 启动
python main.py
```

服务默认运行在 http://localhost:8000

## 测试对话接口

```powershell
curl -N -X POST http://localhost:8000/ai/chat `
  -H "Content-Type: application/json" `
  -H "satoken: xxx" `
  -d '{"session_id":"test-001","message":"你好"}'
```

## 演进路线

1. Phase 1（当前骨架）：裸写 FastAPI，SSE 流式，mock LLM / 空检索
2. Phase 2：接入 LangChain 组件，做平台规则 RAG
3. Phase 3：升级 LangGraph，加意图路由 + ReAct + 工具调用
4. Phase 4：接入 `/order/my`、ES `/product/search` 等工具
5. Phase 5：图片生成独立 worker（复用 RocketMQ）

## 学习与设计文档（先读这两份）

| 文档 | 作用 |
|---|---|
| [`docs/AI-AGENT-学习地图.md`](docs/AI-AGENT-学习地图.md) | **总纲**：目录职责、五步实现路线、每步验收标准、记忆等级 |
| [`docs/前置改造清单-Java侧.md`](docs/前置改造清单-Java侧.md) | 🔴 **开工前必读**：ai-service 落地前 Java 侧必须先改的事（含一个现存越权漏洞） |

## 代码骨架的分层（每层只做一件事）

```
app/
├── api/          HTTP 入口层 —— 只做参数校验 + SSE，不含业务逻辑
├── chains/       LangChain 层 —— 单轮确定性链路（LCEL）
├── rag/          RAG 专项层 —— 语料入库 + 混合检索/重排/门控
├── agents/       LangGraph 层 —— 意图路由 + ReAct 多跳 + 校验闭环
├── tools/        工具层 —— Agent 与后端微服务的唯一桥梁（🔴 安全核心）
├── core/         基础设施层 —— 模型工厂 / 防幻觉护栏 / 可观测性
└── services/     已有薄封装层 —— 底层 HTTP 调用 + 会话存储
```

**依赖方向是单向的**：`api → agents → tools → core`，**不允许反向调用**。

> 📌 所有骨架文件里**只有函数契约 + 注释**，没有实现逻辑 —— 逻辑由你自己填。
> 每个文件末尾都有 `TODO(你来实现)`，并写清了「实现要点」和「验收标准」。

## 语料库（RAG 的知识来源）

`rag_data/` 是 `app/rag/ingest.py` 的输入，已按四类语料的切分策略建好骨架：

```
rag_data/
├── README.md                       🔴 先读：文件格式约定 + metadata 规范 + 待确认参数清单
├── platform/                       source=policy（条款级切分，一条规则一个 chunk）
│   ├── 01-平台总则与术语.md         担保交易 / 孤品 / 订单状态
│   ├── 02-交易与担保支付规则.md     资金冻结、自动确认收货、结算与提现、拆单
│   ├── 03-发货与物流规则.md         发货时效、备货期、运费、物流异常
│   ├── 04-退换货与退款规则.md       七天无理由、质量问题、退款时效
│   ├── 05-定制商品规则.md           定制流程、定金退款、设计确认
│   ├── 06-创作者入驻与商品发布规范.md 发布要求、AI 图红线、禁止类目
│   ├── 07-违规行为与信用分规则.md   加分扣分、封禁、纠纷与申诉
│   └── 08-隐私与数据保护.md         收货信息可见范围、数据权利
├── faq/                            source=faq（一问一答成块）
│   ├── 01-买家常见问题.md
│   └── 02-创作者常见问题.md
└── guides/                         source=guide（按小节切，chunk 带标题前缀）
    ├── 01-手作保养指南.md
    └── 02-材质与工艺指南.md
```

🔴 **写语料前必看 `rag_data/README.md` 第 5 节**：里面列了标 `🟡 待确认` 的业务参数
（发货时效、包邮门槛、佣金比例等），这些是**会进入用户回答的硬数字**，必须由你确认或改写。

## 模型接入状态

已经接好阿里云百炼（OpenAI 兼容模式），并用真实 key 做过连通性验证：

| 能力 | 落点 | 说明 |
|---|---|---|
| 统一出口 | `core/llm_factory.py` | `get_router_llm()` / `get_chat_llm()` / `get_verify_llm()` / `get_embeddings()`，进程内单例，复用连接池 |
| 底层调用 | `services/llm.py` | 流式 `stream_chat()` + 非流式 `chat()`，带单次调用超时 |
| 向量化 | `services/embeddings.py` | 批量 `embed_documents()` + 单条 `embed_query()`，内部自动切批 + 指数退避重试 |
| 配置 | `core/config.py` + `.env` | 模型分级、embedding 维度、图片生成、超时与 token 上限 |

⚠️ `.env` 里有真实 key，**不要提交**；如果不慎泄露，去百炼控制台重置。
