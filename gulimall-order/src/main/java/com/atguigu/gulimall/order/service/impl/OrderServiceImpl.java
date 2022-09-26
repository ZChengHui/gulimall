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
     * 订单确认页返回需要的数据
     * @return
     */
    @Override
    public OrderConfirmVO confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVO confirmVO = new OrderConfirmVO();
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();

        //主线程请求信息
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            //同步主线程请求信息
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //远程查询收货地址列表
            List<MemberAddressVO> address = memberFeignService.getAddress(memberResponseVO.getId());
            confirmVO.setAddress(address);
        }, executor);

        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            //同步主线程请求信息
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //远程查询购物车选中的购物项
            List<OrderItemVO> items = cartFeignService.getCurrentUserCartItems();
            confirmVO.setItems(items);
        }, executor).thenRunAsync(() -> {
            //查库存
            List<OrderItemVO> items = confirmVO.getItems();
            List<Long> collect = items.stream()
                    .map(item -> item.getSkuId())
                    .collect(Collectors.toList());
            R r = wmsFeignService.getSkuHasStock(collect);
            List<SkuStockVO> list = r.getData("data", new TypeReference<List<SkuStockVO>>() {});
            if (list != null && list.size() > 0) {
                //设置库存信息
                Map<Long, Boolean> map = list.stream().collect(Collectors.toMap(
                        SkuStockVO::getSkuId,
                        SkuStockVO::getHasStock
                ));
                confirmVO.setStocks(map);
            }
        }, executor);

        //查询用户积分
        confirmVO.setIntegration(memberResponseVO.getIntegration());

        //TODO 防重令牌
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
     * 下单操作
     * @param vo
     * @return
     * 只在本地事务有效
     * 改用分布式事务 跨服务回滚
     */
//    @GlobalTransactional seata性能低
    @Transactional//事务使用代理对象控制，所以同一个对象内方法互调默认失效（绕过了代理）
    @Override
    public SubmitOrderResponseVO submitOrder(OrderSubmitVO vo) {
        //线程传值
        submitVOThreadLocal.set(vo);
        SubmitOrderResponseVO orderResponseVO = new SubmitOrderResponseVO();

        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        //验证令牌 原子性操作
        //LUA脚本 返回0失败 1对比删除令牌成功
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String orderToken = vo.getOrderToken();
        String key = OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId();
        //执行对比删除令牌
        Long res = redis.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(key), orderToken);
        if (res == 0) {
            //验证失败
            orderResponseVO.setCode(1);
            return orderResponseVO;
        } else {
            //验证成功
            orderResponseVO.setCode(0);
            //创建订单
            OrderCreateTO order = createOrder();
            //验价
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            //金额对比
            if (Math.abs(payAmount.subtract(payPrice).doubleValue())<0.01) {
                //TODO 保存订单
                saveOrder(order);
                //库存锁定 有异常则回滚
                //订单号 订单项
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
                //TODO 远程锁库存
                R r = wmsFeignService.orderLockStock(lockVO);
                if (r.getCode() == 0) {
                    //锁库存成功
                    orderResponseVO.setOrder(order.getOrder());
//                    int i = 10/0;
                    //TODO 订单创建成功发送消息给MQ
                    rabbit.convertAndSend(
                            "order-event-exchange",
                            "order.create.order",
                            order.getOrder()
                    );
                    return orderResponseVO;
                } else {
                    //失败 库存不足
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
//            //令牌通过 删令牌
//            redis.delete(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId());
//        } else {
//
//        }
    }

    //根据订单号获取订单
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
        //查询当前订单最新状态
        OrderEntity orderEntity = this.getById(entity.getId());
        //如果是待付款状态 则取消订单
        if (orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()) {
            //修改订单状态
            OrderEntity updateOrder = new OrderEntity();
            updateOrder.setId(orderEntity.getId());
            updateOrder.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(updateOrder);

            OrderTO orderTO = new OrderTO();
            BeanUtils.copyProperties(orderEntity, orderTO);

            //订单释放消息 发给MQ
            try {
                //TODO 保证消息一定发送 每个消息做好日志记录（数据库持久化）
                //TODO 定期扫描数据库将失败消息重新发送
                rabbit.convertAndSend(
                        "order-event-exchange",
                        "order.release.other",
                        orderTO
                );
            } catch (Exception e) {
                //TODO 将发送失败的消息重试

            }
        }
    }

    //获取当前订单支付信息
    @Override
    public PayVo getOrderPay(String orderSn) {
        PayVo payVo = new PayVo();
        OrderEntity orderEntity = this.getOrderByOrderSn(orderSn);
        //金额
        BigDecimal bigDecimal = orderEntity.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount(bigDecimal.toString());

        List<OrderItemEntity> list = orderItemService.list(
                new QueryWrapper<OrderItemEntity>()
                        .eq("order_sn", orderSn)
        );
        OrderItemEntity itemEntity = list.get(0);

        payVo.setSubject(itemEntity.getSkuName()+"...");
        payVo.setBody("X"+itemEntity.getSkuQuantity()+"件...");
        payVo.setOut_trade_no(orderEntity.getOrderSn());

        return payVo;
    }

    //查询当前用户订单及订单项
    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        //查出订单
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
                        .eq("member_id", memberResponseVO.getId())
                        .orderByDesc("id")
        );
        //填充订单项
        List<OrderEntity> collect = page.getRecords().stream().map(order -> {
            List<OrderItemEntity> list = orderItemService.list(
                    new QueryWrapper<OrderItemEntity>()
                            .eq("order_sn", order.getOrderSn())
            );
            order.setItemEntities(list);
            return order;
        }).collect(Collectors.toList());
        //更新返回数据
        page.setRecords(collect);
        return new PageUtils(page);
    }

    /**
     * TODO 支付成功回调
     * 处理支付结果
     */
    @Override
    public String handlePayResult(PayAsyncVo vo) {
        //保存交易流水 oms_payment_info
        PaymentInfoEntity paymentInfo = new PaymentInfoEntity();
        paymentInfo.setAlipayTradeNo(vo.getTrade_no());
        paymentInfo.setOrderSn(vo.getOut_trade_no());
        paymentInfo.setPaymentStatus(vo.getTrade_status());
        paymentInfo.setCallbackTime(vo.getNotify_time());
        paymentInfoService.save(paymentInfo);
        //修改订单状态
        String tradeStatus = vo.getTrade_status();
        if ( tradeStatus.equals("TRADE_SUCCESS") || tradeStatus.equals("TRADE_FINISHED") ) {
            //支付成功状态
            String outTradeNo = vo.getOut_trade_no();
            this.baseMapper.updateOrderStatus(outTradeNo, OrderStatusEnum.PAYED.getCode());
        }
        return "success";
    }

    //创建秒杀订单
    @Override
    public void createSeckillOrder(SeckillOrderTO seckillOrder) {
        //TODO 保存订单
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(seckillOrder.getOrderSn());
        orderEntity.setMemberId(seckillOrder.getMemberId());
        orderEntity.setCreateTime(new Date());
        BigDecimal total = seckillOrder.getSeckillPrice().multiply(BigDecimal.valueOf(seckillOrder.getNum()));
        orderEntity.setPayAmount(total);
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        this.save(orderEntity);

        //TODO 订单项信息 获取当前sku详细信息进行设置
        OrderItemEntity itemEntity = new OrderItemEntity();
        SkuInfoVO skuInfo = seckillOrder.getSkuInfo();

        itemEntity.setOrderSn(seckillOrder.getOrderSn());
        itemEntity.setRealAmount(total);
        itemEntity.setSkuQuantity(seckillOrder.getNum());
        itemEntity.setSkuName(skuInfo.getSkuName());
        itemEntity.setSkuPic(skuInfo.getSkuDefaultImg());

        orderItemService.save(itemEntity);

    }


    //保存订单数据
    private void saveOrder(OrderCreateTO order) {
        OrderEntity orderEntity = order.getOrder();
        this.save(orderEntity);

        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);
    }

    //创建订单总方法
    private OrderCreateTO createOrder() {
        OrderCreateTO orderCreateTO = new OrderCreateTO();
        //生成订单号
        String orderSn = IdWorker.getTimeId();
        //创建订单
        OrderEntity order = buildOrder(orderSn);
        //获取所有订单项
        List<OrderItemEntity> itemEntities = buildOrderItems(orderSn);
        //计算价格相关
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
        //叠加每个订单项价格
        for (OrderItemEntity entity : itemEntities) {
            total = total.add(entity.getRealAmount());
            //各项优惠价格汇总
            promotion = promotion.add(entity.getPromotionAmount());
            coupon = coupon.add(entity.getCouponAmount());
            integration = integration.add(entity.getIntegrationAmount());
            //积分信息 成长信息
            giftGrowth = giftGrowth.add(new BigDecimal(entity.getGiftIntegration()));
            giftIntegration = giftIntegration.add(new BigDecimal(entity.getGiftGrowth()));
        }
        //订单价格相关数据
        //订单总额
        order.setTotalAmount(total);
        //应付总额 (含运费)
        order.setPayAmount(total.add(order.getFreightAmount()));
        //各项优惠
        order.setPromotionAmount(promotion);
        order.setIntegrationAmount(integration);
        order.setCouponAmount(coupon);
        //积分 成长值
        order.setGrowth(giftGrowth.intValue());
        order.setIntegration(giftIntegration.intValue());


    }

    //构建订单基本信息
    private OrderEntity buildOrder(String orderSn) {
        //线程获取值
        OrderSubmitVO submitVO = submitVOThreadLocal.get();
        //创建订单号
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn);
        //设置会员id
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        entity.setMemberId(memberResponseVO.getId());
        //地址信息
        R r = wmsFeignService.getFare(submitVO.getAddrId());
        FareResponseVO fareResponseVO = r.getData("data", new TypeReference<FareResponseVO>() {});
        //设置运费
        BigDecimal fare = fareResponseVO.getFare();
        entity.setFreightAmount(fare);
        //设置地址
        MemberAddressVO address = fareResponseVO.getAddress();
        entity.setReceiverCity(address.getCity());
        entity.setReceiverDetailAddress(address.getDetailAddress());
        entity.setReceiverName(address.getName());
        entity.setReceiverPhone(address.getPhone());
        entity.setReceiverPostCode(address.getPostCode());
        entity.setReceiverProvince(address.getProvince());
        entity.setReceiverCity(address.getCity());
        entity.setReceiverRegion(address.getRegion());

        //时间
        entity.setCreateTime(new Date());
        entity.setModifyTime(new Date());

        //设置订单状态信息
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setDeleteStatus(0);
        return entity;
    }

    /**
     * 构建所有订单项
     */
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        //获取订单项 确定购物项价格
        List<OrderItemVO> currentUserCartItems = cartFeignService.getCurrentUserCartItems();
        if (!CollectionUtils.isEmpty(currentUserCartItems)) {
            List<OrderItemEntity> collect = currentUserCartItems.stream().map(item -> {
                //构建订单项
                OrderItemEntity itemEntity = buildOrderItem(item);
                itemEntity.setOrderSn(orderSn);
                return itemEntity;
            }).collect(Collectors.toList());
            return collect;
        }
        return null;
    }

    /**
     * 构建单个订单项
     * @param cartItem
     * @return
     */
    private OrderItemEntity buildOrderItem(OrderItemVO cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        //商品spu信息
        Long skuId = cartItem.getSkuId();
        R r = productFeignService.getSpuIdBySkuId(skuId);
        SpuInfoVO spuInfoVO = r.getData("data", new TypeReference<SpuInfoVO>() {});
        itemEntity.setSpuId(spuInfoVO.getId());
        itemEntity.setSpuName(spuInfoVO.getSpuName());
        itemEntity.setSpuBrand(spuInfoVO.getBrandId().toString());
        itemEntity.setCategoryId(spuInfoVO.getCatalogId());
        //商品sku信息
        itemEntity.setSkuId(cartItem.getSkuId());
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        itemEntity.setSkuAttrsVals(StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(),";"));
        itemEntity.setSkuQuantity(cartItem.getCount());
        //积分信息
        itemEntity.setGiftGrowth(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        itemEntity.setGiftIntegration(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        //价格信息
        //促销
        itemEntity.setPromotionAmount(new BigDecimal(0));
        //优惠劵
        itemEntity.setCouponAmount(new BigDecimal(0));
        //积分
        itemEntity.setIntegrationAmount(new BigDecimal(0));
        //实际价格
        BigDecimal originPrice = itemEntity.getSkuPrice().multiply(
                new BigDecimal(itemEntity.getSkuQuantity()));
        //减去优惠
        BigDecimal subtractPrice = originPrice.subtract(itemEntity.getCouponAmount())
                .subtract(itemEntity.getPromotionAmount())
                .subtract(itemEntity.getIntegrationAmount());
        itemEntity.setRealAmount(subtractPrice);
        return itemEntity;
    }

}