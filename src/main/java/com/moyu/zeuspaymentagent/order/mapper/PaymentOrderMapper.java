package com.moyu.zeuspaymentagent.order.mapper;

import com.moyu.zeuspaymentagent.order.model.PaymentOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface PaymentOrderMapper {

    /**
     * 订单号精确查询，供订单查询 Tool 和支付分析 Tool 复用。
     */
    @Select("""
            SELECT
                id,
                order_no AS orderNo,
                user_id AS userId,
                amount,
                currency,
                status,
                payment_channel AS paymentChannel,
                failure_reason AS failureReason,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM `order`
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    Optional<PaymentOrder> findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按状态、用户、时间范围组合查询订单列表。
     */
    @SelectProvider(type = PaymentOrderSqlProvider.class, method = "searchOrders")
    List<PaymentOrder> searchOrders(
            @Param("status") String status,
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);
}
