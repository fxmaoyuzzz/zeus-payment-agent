package com.moyu.zeuspaymentagent.order.tool;

import com.moyu.zeuspaymentagent.audit.service.ToolCallAuditService;
import com.moyu.zeuspaymentagent.order.mapper.PaymentOrderMapper;
import com.moyu.zeuspaymentagent.order.model.PaymentOrderView;
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
    private final ToolCallAuditService toolCallAuditService;

    public OrderQueryTool(PaymentOrderMapper paymentOrderMapper, ToolCallAuditService toolCallAuditService) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * 精确查询流程：LLM 提取订单号 -> 查询 MySQL -> 返回适合模型消费的视图对象。
     */
    @Tool(
            name = "query_order_by_order_no",
            description = "根据支付订单号精确查询单个订单。用户提供订单号时优先调用这个工具。")
    public PaymentOrderView queryOrderByOrderNo(
            @ToolParam(description = "支付订单号，例如 P202608250001") String orderNo) {
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {orderNo};
        try {
            var result = paymentOrderMapper.findByOrderNo(orderNo).map(PaymentOrderView::from).orElse(null);
            toolCallAuditService.record("query_order_by_order_no", getClass().getName(),
                    "queryOrderByOrderNo", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("query_order_by_order_no", getClass().getName(),
                    "queryOrderByOrderNo", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    /**
     * 条件查询流程：归一化返回数量 -> Mapper 动态拼 SQL -> 转换为订单视图列表。
     */
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
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {status, userId, startTime, endTime, limit};
        try {
            var result = paymentOrderMapper.searchOrders(status, userId, startTime, endTime, normalizeLimit(limit))
                    .stream()
                    .map(PaymentOrderView::from)
                    .toList();
            toolCallAuditService.record("search_orders", getClass().getName(),
                    "searchOrders", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("search_orders", getClass().getName(),
                    "searchOrders", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
