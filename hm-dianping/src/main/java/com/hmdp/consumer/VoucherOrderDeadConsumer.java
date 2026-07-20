package com.hmdp.consumer;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 订单死信队列消费者
 * 处理消费失败的订单消息
 */
@Slf4j
@Component
public class VoucherOrderDeadConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 监听死信队列
     * 处理失败的订单消息
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_QUEUE)
    public void handleDeadLetter(VoucherOrder voucherOrder) {
        log.error("收到死信消息，尝试重新处理订单. orderId: {}, voucherId: {}, userId: {}",
                voucherOrder.getId(), voucherOrder.getVoucherId(), voucherOrder.getUserId());
        
        try {
            // 尝试重新处理订单
            voucherOrderService.createVoucherOrder(voucherOrder);
            log.info("死信消息处理成功. orderId: {}", voucherOrder.getId());
        } catch (Exception e) {
            log.error("死信消息处理失败. orderId: {}, error: {}", voucherOrder.getId(), e.getMessage());
            // 如果再次失败，可以记录到数据库或发送告警
        }
    }
}
