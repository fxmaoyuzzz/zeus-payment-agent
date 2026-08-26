package com.moyu.zeuspaymentagent.payment.mapper;

import com.moyu.zeuspaymentagent.payment.model.PaymentTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface PaymentTransactionQueryMapper {

    /**
     * 按用户输入条件查询支付流水明细。
     */
    @SelectProvider(type = PaymentTransactionSqlProvider.class, method = "queryTransactions")
    List<PaymentTransaction> queryTransactions(
            @Param("transactionNo") String transactionNo,
            @Param("orderNo") String orderNo,
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("channelCode") String channelCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);
}
