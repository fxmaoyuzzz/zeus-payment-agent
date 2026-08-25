# PayPal Channel Guide

## 概述

PayPal 常用于跨境支付，涉及账户状态、币种、国家地区、风控、买家付款方式和 PayPal 侧拒绝等因素。PayPal 支付失败不能简单等同于系统异常，很多失败来自买家账户限制、资金来源不可用、币种不支持或 PayPal 风控。

跨境支付排障时要重点关注交易币种、买家国家地区、PayPal 错误码、授权状态和捕获状态。

## 常见失败类型

| 内部失败码 | 渠道错误码示例 | 原因类型 | 说明 | 建议 |
| --- | --- | --- | --- | --- |
| `PAYPAL_REJECTED` | `PAYPAL_DECLINED` | `CHANNEL` | PayPal 拒绝本次交易 | 建议用户检查 PayPal 账户或更换支付方式 |
| `ACCOUNT_LIMITED` | `PAYPAL_ACCOUNT_LIMITED` | `USER` | 买家 PayPal 账户受限 | 建议用户登录 PayPal 处理账户限制 |
| `FUNDING_SOURCE_FAILED` | `PAYPAL_FUNDING_FAILED` | `USER` | 买家绑定的卡或资金来源不可用 | 建议用户更换 PayPal 资金来源 |
| `CURRENCY_NOT_SUPPORTED` | `PAYPAL_CURRENCY_NOT_SUPPORTED` | `SYSTEM` | 当前币种不被商户或 PayPal 支持 | 建议检查币种配置 |
| `COUNTRY_NOT_SUPPORTED` | `PAYPAL_COUNTRY_BLOCKED` | `RISK` | 买家地区不支持或被风控限制 | 建议更换支付方式 |
| `CHANNEL_TIMEOUT` | `PAYPAL_TIMEOUT` | `NETWORK` | 请求 PayPal 超时 | 建议查询 PayPal 订单状态 |
| `CAPTURE_FAILED` | `PAYPAL_CAPTURE_FAILED` | `CHANNEL` | 授权成功但捕获失败 | 建议查询授权状态并决定是否重新捕获 |

## PayPal 交易阶段

| 阶段 | 说明 | 排查重点 |
| --- | --- | --- |
| Create Order | 创建 PayPal 订单 | 参数、币种、金额、returnUrl |
| Approve | 买家在 PayPal 页面授权 | 用户是否取消、账户是否受限 |
| Capture | 商户捕获款项 | 捕获状态、资金来源、风控 |
| Webhook | PayPal 异步通知 | Webhook 签名和事件类型 |
| Query | 主动查询订单状态 | 最终状态确认 |

## 典型场景

### PayPal 拒绝交易

证据通常包括：

- 支付方式为 `PAYPAL`。
- 渠道错误码为 `PAYPAL_DECLINED`。
- 支付流水状态为 `FAILED`。
- 日志中 `CHANNEL_RESPONSE` 为 `FAILED`。

建议回答方向：

该订单 PayPal 支付失败，PayPal 拒绝了本次交易。建议用户检查 PayPal 账户状态、绑定卡状态或更换支付方式。

### 授权成功但捕获失败

证据通常包括：

- Create Order 成功。
- Approve 成功或用户完成授权。
- Capture 阶段失败。
- 订单状态没有成功。

建议回答方向：

该问题发生在捕获阶段，不是用户未授权。需要查询 PayPal 订单和授权状态，确认是否可以重新捕获或释放授权。

### 币种不支持

证据通常包括：

- 订单币种不是商户支持币种。
- 渠道错误码为 `PAYPAL_CURRENCY_NOT_SUPPORTED`。
- 请求参数中 currency 与商户配置不匹配。

建议回答方向：

该订单失败原因是币种配置不支持。建议检查商户账户币种、商品币种和 PayPal 收款配置。

## 注意事项

- PayPal 失败分析要区分授权失败和捕获失败。
- PayPal Webhook 可能异步到达，最终状态应结合主动查询。
- 跨境支付涉及合规和地区限制，不能简单建议用户反复重试。
