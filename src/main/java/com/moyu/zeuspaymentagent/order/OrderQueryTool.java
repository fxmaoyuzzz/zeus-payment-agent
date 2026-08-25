package com.moyu.zeuspaymentagent.order;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final PaymentOrderMapper paymentOrderMapper;

    public OrderQueryTool(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Tool(
            name = "query_order_by_order_no",
            description = "根据支付订单号精确查询单个订单。用户提供订单号时优先调用这个工具。")
    public PaymentOrderView queryOrderByOrderNo(
            @ToolParam(description = "支付订单号，例如 P202608250001") String orderNo) {
        return paymentOrderMapper.findByOrderNo(orderNo)
                .map(PaymentOrderView::from)
                .orElse(null);
    }

    @Tool(
            name = "search_orders",
            description = "按订单状态、用户ID或创建时间范围查询最近的支付订单列表。不要用于精确订单号查询。")
    public List<PaymentOrderView> searchOrders(
            @ToolParam(required = false, description = "订单状态，例如 SUCCESS、FAILED、PENDING")
                    String status,
            @ToolParam(required = false, description = "用户ID") String userId,
            @ToolParam(required = false, description = "开始时间，ISO-8601 格式，例如 2026-08-25T00:00:00")
                    LocalDateTime startTime,
            @ToolParam(required = false, description = "结束时间，ISO-8601 格式，例如 2026-08-25T23:59:59")
                    LocalDateTime endTime,
            @ToolParam(required = false, description = "返回数量，默认10，最大50") Integer limit) {
        return paymentOrderMapper.searchOrders(status, userId, startTime, endTime, normalizeLimit(limit))
                .stream()
                .map(PaymentOrderView::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
