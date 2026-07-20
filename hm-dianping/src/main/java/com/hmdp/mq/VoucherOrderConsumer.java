package com.hmdp.mq;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderConsumer {

   @Resource
   private IVoucherOrderService voucherOrderService;

   @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
   public void handleVoucherOrder(VoucherOrderMessage message) {
       log.info("接收到订单消息: {}", message);

       VoucherOrder voucherOrder = new VoucherOrder();
       voucherOrder.setId(message.getOrderId());
       voucherOrder.setVoucherId(message.getVoucherId());
       voucherOrder.setUserId(message.getUserId());

       voucherOrderService.handleVoucherOrder(voucherOrder);
   }
}
