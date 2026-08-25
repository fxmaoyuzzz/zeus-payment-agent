# Payment Knowledge Base Index

本文档是 Zeus Payment Agent 的知识库入口，用于后续 V5 RAG 导入和检索。知识库内容围绕支付渠道、错误码、支付失败归因、回调处理、超时补单和排障 SOP 展开。

## 文档清单

| 文件 | 主题 | 适用问题 |
| --- | --- | --- |
| `bank_card_channel_guide.md` | 银行卡支付渠道 | 余额不足、发卡行拒绝、3DS、限额、卡状态异常 |
| `wechat_pay_channel_guide.md` | 微信支付渠道 | 用户取消、支付超时、回调延迟、订单关闭、微信错误码 |
| `alipay_channel_guide.md` | 支付宝渠道 | 风控拒绝、买家取消、交易关闭、回调验签、支付宝错误码 |
| `paypal_channel_guide.md` | PayPal 渠道 | 跨境拒付、账户限制、币种问题、PayPal 拒绝 |
| `payment_failure_sop.md` | 支付失败排障 SOP | 通用支付失败分析流程、证据链、处理建议 |

## 推荐检索策略

后续实现 RAG 时，建议按以下优先级召回知识：

1. 先按错误码精确匹配，例如 `INSUFFICIENT_FUNDS`、`WX_TIMEOUT`、`ALI_RISK_REJECT`。
2. 再按支付方式匹配，例如 `BANK_CARD`、`WECHAT_PAY`、`ALIPAY`、`PAYPAL`。
3. 再按问题类型匹配，例如余额不足、用户取消、渠道超时、风控拒绝、回调异常。
4. 最后召回通用 SOP，用于补充处理流程和建议动作。

## 回答原则

- 订单、流水、日志数据优先级高于知识库说明。
- 知识库用于解释错误码、补充排查路径和给出处理建议。
- 不要仅凭知识库判断某笔订单失败原因，必须结合订单和支付流水证据。
- 如果日志缺失，应明确说明证据不足，并建议继续查询日志或渠道侧状态。
