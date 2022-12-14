package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.OrderConstant;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTO;
import com.atguigu.common.to.mq.SeckillOrderTO;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVO;
import com.atguigu.common.vo.SkuInfoVO;
import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WmsFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTO;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.annotation.Resource;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    private ThreadLocal<OrderSubmitVO> submitVOThreadLocal = new ThreadLocal<>();

    @Autowired
    private PaymentInfoService paymentInfoService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private ThreadPoolExecutor executor;

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private CartFeignService cartFeignService;

    @Autowired
    private WmsFeignService wmsFeignService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RabbitTemplate rabbit;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * ????????????????????????????????????
     * @return
     */
    @Override
    public OrderConfirmVO confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVO confirmVO = new OrderConfirmVO();
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();

        //?????????????????????
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            //???????????????????????????
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //??????????????????????????????
            List<MemberAddressVO> address = memberFeignService.getAddress(memberResponseVO.getId());
            confirmVO.setAddress(address);
        }, executor);

        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            //???????????????????????????
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //???????????????????????????????????????
            List<OrderItemVO> items = cartFeignService.getCurrentUserCartItems();
            confirmVO.setItems(items);
        }, executor).thenRunAsync(() -> {
            //?????????
            List<OrderItemVO> items = confirmVO.getItems();
            List<Long> collect = items.stream()
                    .map(item -> item.getSkuId())
                    .collect(Collectors.toList());
            R r = wmsFeignService.getSkuHasStock(collect);
            List<SkuStockVO> list = r.getData("data", new TypeReference<List<SkuStockVO>>() {});
            if (list != null && list.size() > 0) {
                //??????????????????
                Map<Long, Boolean> map = list.stream().collect(Collectors.toMap(
                        SkuStockVO::getSkuId,
                        SkuStockVO::getHasStock
                ));
                confirmVO.setStocks(map);
            }
        }, executor);

        //??????????????????
        confirmVO.setIntegration(memberResponseVO.getIntegration());

        //TODO ????????????
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(
                OrderConstant.USER_ORDER_TOKEN_PREFIX+memberResponseVO.getId(),
                token,
                30,
                TimeUnit.MINUTES);
        confirmVO.setOrderToken(token);

        CompletableFuture.allOf(addressFuture, cartFuture).get();
        System.out.println(confirmVO);
        return confirmVO;
    }

    /**
     * ????????????
     * @param vo
     * @return
     * ????????????????????????
     * ????????????????????? ???????????????
     */
//    @GlobalTransactional seata?????????
    @Transactional//??????????????????????????????????????????????????????????????????????????????????????????????????????
    @Override
    public SubmitOrderResponseVO submitOrder(OrderSubmitVO vo) {
        //????????????
        submitVOThreadLocal.set(vo);
        SubmitOrderResponseVO orderResponseVO = new SubmitOrderResponseVO();

        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        //???????????? ???????????????
        //LUA?????? ??????0?????? 1????????????????????????
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String orderToken = vo.getOrderToken();
        String key = OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId();
        //????????????????????????
        Long res = redis.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(key), orderToken);
        if (res == 0) {
            //????????????
            orderResponseVO.setCode(1);
            return orderResponseVO;
        } else {
            //????????????
            orderResponseVO.setCode(0);
            //????????????
            OrderCreateTO order = createOrder();
            //??????
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            //????????????
            if (Math.abs(payAmount.subtract(payPrice).doubleValue())<0.01) {
                //TODO ????????????
                saveOrder(order);
                //???????????? ??????????????????
                //????????? ?????????
                WareSkuLockVO lockVO = new WareSkuLockVO();
                lockVO.setOrderSn(order.getOrder().getOrderSn());

                List<OrderItemVO> collect = order.getOrderItems().stream().map(item -> {
                    OrderItemVO itemVO = new OrderItemVO();
                    itemVO.setSkuId(item.getSkuId());
                    itemVO.setCount(item.getSkuQuantity());
                    itemVO.setTitle(item.getSkuName());
                    return itemVO;
                }).collect(Collectors.toList());
                lockVO.setLocks(collect);
                //TODO ???????????????
                R r = wmsFeignService.orderLockStock(lockVO);
                if (r.getCode() == 0) {
                    //???????????????
                    orderResponseVO.setOrder(order.getOrder());
//                    int i = 10/0;
                    //TODO ?????????????????????????????????MQ
                    rabbit.convertAndSend(
                            "order-event-exchange",
                            "order.create.order",
                            order.getOrder()
                    );
                    return orderResponseVO;
                } else {
                    //?????? ????????????
                    orderResponseVO.setCode(3);
                    throw new NoStockException();
//                    return orderResponseVO;
                }
            } else {
                orderResponseVO.setCode(2);
                return orderResponseVO;
            }

        }
//        String redisToken = redis.opsForValue().get();
//        if (orderToken != null && redisToken != null && orderToken.equals(redisToken)) {
//            //???????????? ?????????
//            redis.delete(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId());
//        } else {
//
//        }
    }

    //???????????????????????????
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity orderEntity = this.getOne(
                new QueryWrapper<OrderEntity>()
                        .eq("order_sn", orderSn)
        );
        return orderEntity;
    }

    @Override
    public void closeOrder(OrderEntity entity) {
        //??????????????????????????????
        OrderEntity orderEntity = this.getById(entity.getId());
        //???????????????????????? ???????????????
        if (orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()) {
            //??????????????????
            OrderEntity updateOrder = new OrderEntity();
            updateOrder.setId(orderEntity.getId());
            updateOrder.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(updateOrder);

            OrderTO orderTO = new OrderTO();
            BeanUtils.copyProperties(orderEntity, orderTO);

            //?????????????????? ??????MQ
            try {
                //TODO ???????????????????????? ??????????????????????????????????????????????????????
                //TODO ????????????????????????????????????????????????
                rabbit.convertAndSend(
                        "order-event-exchange",
                        "order.release.other",
                        orderTO
                );
            } catch (Exception e) {
                //TODO ??????????????????????????????

            }
        }
    }

    //??????????????????????????????
    @Override
    public PayVo getOrderPay(String orderSn) {
        PayVo payVo = new PayVo();
        OrderEntity orderEntity = this.getOrderByOrderSn(orderSn);
        //??????
        BigDecimal bigDecimal = orderEntity.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount(bigDecimal.toString());

        List<OrderItemEntity> list = orderItemService.list(
                new QueryWrapper<OrderItemEntity>()
                        .eq("order_sn", orderSn)
        );
        OrderItemEntity itemEntity = list.get(0);

        payVo.setSubject(itemEntity.getSkuName()+"...");
        payVo.setBody("X"+itemEntity.getSkuQuantity()+"???...");
        payVo.setOut_trade_no(orderEntity.getOrderSn());

        return payVo;
    }

    //????????????????????????????????????
    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        //????????????
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
                        .eq("member_id", memberResponseVO.getId())
                        .orderByDesc("id")
        );
        //???????????????
        List<OrderEntity> collect = page.getRecords().stream().map(order -> {
            List<OrderItemEntity> list = orderItemService.list(
                    new QueryWrapper<OrderItemEntity>()
                            .eq("order_sn", order.getOrderSn())
            );
            order.setItemEntities(list);
            return order;
        }).collect(Collectors.toList());
        //??????????????????
        page.setRecords(collect);
        return new PageUtils(page);
    }

    /**
     * TODO ??????????????????
     * ??????????????????
     */
    @Override
    public String handlePayResult(PayAsyncVo vo) {
        //?????????????????? oms_payment_info
        PaymentInfoEntity paymentInfo = new PaymentInfoEntity();
        paymentInfo.setAlipayTradeNo(vo.getTrade_no());
        paymentInfo.setOrderSn(vo.getOut_trade_no());
        paymentInfo.setPaymentStatus(vo.getTrade_status());
        paymentInfo.setCallbackTime(vo.getNotify_time());
        paymentInfoService.save(paymentInfo);
        //??????????????????
        String tradeStatus = vo.getTrade_status();
        if ( tradeStatus.equals("TRADE_SUCCESS") || tradeStatus.equals("TRADE_FINISHED") ) {
            //??????????????????
            String outTradeNo = vo.getOut_trade_no();
            this.baseMapper.updateOrderStatus(outTradeNo, OrderStatusEnum.PAYED.getCode());
        }
        return "success";
    }

    //??????????????????
    @Override
    public void createSeckillOrder(SeckillOrderTO seckillOrder) {
        //TODO ????????????
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(seckillOrder.getOrderSn());
        orderEntity.setMemberId(seckillOrder.getMemberId());
        orderEntity.setCreateTime(new Date());
        BigDecimal total = seckillOrder.getSeckillPrice().multiply(BigDecimal.valueOf(seckillOrder.getNum()));
        orderEntity.setPayAmount(total);
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        this.save(orderEntity);

        //TODO ??????????????? ????????????sku????????????????????????
        OrderItemEntity itemEntity = new OrderItemEntity();
        SkuInfoVO skuInfo = seckillOrder.getSkuInfo();

        itemEntity.setOrderSn(seckillOrder.getOrderSn());
        itemEntity.setRealAmount(total);
        itemEntity.setSkuQuantity(seckillOrder.getNum());
        itemEntity.setSkuName(skuInfo.getSkuName());
        itemEntity.setSkuPic(skuInfo.getSkuDefaultImg());

        orderItemService.save(itemEntity);

    }


    //??????????????????
    private void saveOrder(OrderCreateTO order) {
        OrderEntity orderEntity = order.getOrder();
        this.save(orderEntity);

        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);
    }

    //?????????????????????
    private OrderCreateTO createOrder() {
        OrderCreateTO orderCreateTO = new OrderCreateTO();
        //???????????????
        String orderSn = IdWorker.getTimeId();
        //????????????
        OrderEntity order = buildOrder(orderSn);
        //?????????????????????
        List<OrderItemEntity> itemEntities = buildOrderItems(orderSn);
        //??????????????????
        computePrice(order, itemEntities);

        orderCreateTO.setOrderItems(itemEntities);
        orderCreateTO.setOrder(order);
        return orderCreateTO;
    }

    private void computePrice(OrderEntity order, List<OrderItemEntity> itemEntities) {
        BigDecimal total = new BigDecimal(0);
        BigDecimal coupon = new BigDecimal(0);
        BigDecimal promotion = new BigDecimal(0);
        BigDecimal integration = new BigDecimal(0);
        BigDecimal giftGrowth = new BigDecimal(0);
        BigDecimal giftIntegration = new BigDecimal(0);
        //???????????????????????????
        for (OrderItemEntity entity : itemEntities) {
            total = total.add(entity.getRealAmount());
            //????????????????????????
            promotion = promotion.add(entity.getPromotionAmount());
            coupon = coupon.add(entity.getCouponAmount());
            integration = integration.add(entity.getIntegrationAmount());
            //???????????? ????????????
            giftGrowth = giftGrowth.add(new BigDecimal(entity.getGiftIntegration()));
            giftIntegration = giftIntegration.add(new BigDecimal(entity.getGiftGrowth()));
        }
        //????????????????????????
        //????????????
        order.setTotalAmount(total);
        //???????????? (?????????)
        order.setPayAmount(total.add(order.getFreightAmount()));
        //????????????
        order.setPromotionAmount(promotion);
        order.setIntegrationAmount(integration);
        order.setCouponAmount(coupon);
        //?????? ?????????
        order.setGrowth(giftGrowth.intValue());
        order.setIntegration(giftIntegration.intValue());


    }

    //????????????????????????
    private OrderEntity buildOrder(String orderSn) {
        //???????????????
        OrderSubmitVO submitVO = submitVOThreadLocal.get();
        //???????????????
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn);
        //????????????id
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        entity.setMemberId(memberResponseVO.getId());
        //????????????
        R r = wmsFeignService.getFare(submitVO.getAddrId());
        FareResponseVO fareResponseVO = r.getData("data", new TypeReference<FareResponseVO>() {});
        //????????????
        BigDecimal fare = fareResponseVO.getFare();
        entity.setFreightAmount(fare);
        //????????????
        MemberAddressVO address = fareResponseVO.getAddress();
        entity.setReceiverCity(address.getCity());
        entity.setReceiverDetailAddress(address.getDetailAddress());
        entity.setReceiverName(address.getName());
        entity.setReceiverPhone(address.getPhone());
        entity.setReceiverPostCode(address.getPostCode());
        entity.setReceiverProvince(address.getProvince());
        entity.setReceiverCity(address.getCity());
        entity.setReceiverRegion(address.getRegion());

        //??????
        entity.setCreateTime(new Date());
        entity.setModifyTime(new Date());

        //????????????????????????
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setDeleteStatus(0);
        return entity;
    }

    /**
     * ?????????????????????
     */
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        //??????????????? ?????????????????????
        List<OrderItemVO> currentUserCartItems = cartFeignService.getCurrentUserCartItems();
        if (!CollectionUtils.isEmpty(currentUserCartItems)) {
            List<OrderItemEntity> collect = currentUserCartItems.stream().map(item -> {
                //???????????????
                OrderItemEntity itemEntity = buildOrderItem(item);
                itemEntity.setOrderSn(orderSn);
                return itemEntity;
            }).collect(Collectors.toList());
            return collect;
        }
        return null;
    }

    /**
     * ?????????????????????
     * @param cartItem
     * @return
     */
    private OrderItemEntity buildOrderItem(OrderItemVO cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        //??????spu??????
        Long skuId = cartItem.getSkuId();
        R r = productFeignService.getSpuIdBySkuId(skuId);
        SpuInfoVO spuInfoVO = r.getData("data", new TypeReference<SpuInfoVO>() {});
        itemEntity.setSpuId(spuInfoVO.getId());
        itemEntity.setSpuName(spuInfoVO.getSpuName());
        itemEntity.setSpuBrand(spuInfoVO.getBrandId().toString());
        itemEntity.setCategoryId(spuInfoVO.getCatalogId());
        //??????sku??????
        itemEntity.setSkuId(cartItem.getSkuId());
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        itemEntity.setSkuAttrsVals(StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(),";"));
        itemEntity.setSkuQuantity(cartItem.getCount());
        //????????????
        itemEntity.setGiftGrowth(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        itemEntity.setGiftIntegration(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        //????????????
        //??????
        itemEntity.setPromotionAmount(new BigDecimal(0));
        //?????????
        itemEntity.setCouponAmount(new BigDecimal(0));
        //??????
        itemEntity.setIntegrationAmount(new BigDecimal(0));
        //????????????
        BigDecimal originPrice = itemEntity.getSkuPrice().multiply(
                new BigDecimal(itemEntity.getSkuQuantity()));
        //????????????
        BigDecimal subtractPrice = originPrice.subtract(itemEntity.getCouponAmount())
                .subtract(itemEntity.getPromotionAmount())
                .subtract(itemEntity.getIntegrationAmount());
        itemEntity.setRealAmount(subtractPrice);
        return itemEntity;
    }

}