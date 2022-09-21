package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.OrderConstant;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVO;
import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WmsFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.to.OrderCreateTO;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
     */
    @Transactional
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
                //保存订单
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

    //构建订单
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