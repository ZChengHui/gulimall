package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.to.SkuInfoTO;
import com.atguigu.gulimall.cart.to.UserInfoTO;
import com.atguigu.gulimall.cart.vo.Cart;
import com.atguigu.gulimall.cart.vo.CartItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private ThreadPoolExecutor executor;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redis;

    private String CART_PREFIX = "gulimall:cart:";

    @Override
    public CartItem addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();

        //添加新商品到购物车

        String res = (String) cartOps.get(skuId.toString());
        if (StringUtils.isEmpty(res)) {
            //购物车无此商品
            CartItem cartItem = new CartItem();
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                //远程查询后台商品sku信息
                R r = productFeignService.getSkuinfo(skuId);
                SkuInfoTO data = r.getData("skuInfo", new TypeReference<SkuInfoTO>() {
                });

                cartItem.setCheck(true);
                cartItem.setCount(num);
                cartItem.setImage(data.getSkuDefaultImg());
                cartItem.setTitle(data.getSkuTitle());
                cartItem.setSkuId(data.getSkuId());
                cartItem.setPrice(data.getPrice());
            }, executor);

            CompletableFuture<Void> getSaleAttrValues = CompletableFuture.runAsync(() -> {
                //远程查询sku属性组合信息
                List<String> list = productFeignService.getSkuSaleAttrValue(skuId);
                cartItem.setSkuAttr(list);
            }, executor);

            //阻塞等待
            CompletableFuture.allOf(getSkuInfoTask, getSaleAttrValues).get();

            String s = JSON.toJSONString(cartItem);
            cartOps.put(skuId.toString(), s);
            return cartItem;
        } else {
            //购物车有商品 更新数量
            CartItem cartItem = JSON.parseObject(res, CartItem.class);
            cartItem.setCount(cartItem.getCount() + num);

            //redis更新
            cartOps.put(skuId.toString(), JSON.toJSONString(cartItem));

            return cartItem;
        }

    }

    @Override
    public CartItem getCartItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String str = (String) cartOps.get(skuId.toString());
        CartItem item = JSON.parseObject(str, CartItem.class);
        return item;
    }

    @Override
    public Cart getCart() throws ExecutionException, InterruptedException {
        UserInfoTO userInfoTO = CartInterceptor.threadLocal.get();
        Cart cart = new Cart();
        if (userInfoTO.getUserId() != null) {
            //登录的key
            String cartKey = CART_PREFIX + userInfoTO.getUserId();
            //临时的key
            String tempCartKey = CART_PREFIX + userInfoTO.getUserKey();
            List<CartItem> tempCartItem = getCartItems(tempCartKey);
            //如果有临时购物车的数据
            if (tempCartItem != null) {
                //合并数据
                for (CartItem item : tempCartItem) {
                    addToCart(item.getSkuId(), item.getCount());
                }
                //清空临时购物车数据
                clearCart(tempCartKey);
            }
            //获取登录更新后购物车数据
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);
        } else {
            //没登录
            String cartKey = CART_PREFIX + userInfoTO.getUserKey();
            //获取购物车数据
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);
        }
        return cart;
    }

    /**
     * 获取redis中要操作的购物车
     * @return
     */
    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTO userInfoTO = CartInterceptor.threadLocal.get();
        String cartKey = "";
        if (userInfoTO.getUserId() != null) {
            //登录后用id
            cartKey = CART_PREFIX + userInfoTO.getUserId();
        } else {
            //未登录用key
            cartKey = CART_PREFIX + userInfoTO.getUserKey();
        }
        //redis绑定特定操作
        BoundHashOperations<String, Object, Object> hashOps = redis.boundHashOps(cartKey);
        return hashOps;
    }

    public List<CartItem> getCartItems(String cartKey) {
        BoundHashOperations<String, Object, Object> hashOps = redis.boundHashOps(cartKey);
        List<Object> values = hashOps.values();
        //获取购物车数据
        if (values != null && values.size() > 0) {

            List<CartItem> collect = values.stream().map(obj -> {
                String str = (String) obj;
                CartItem cartItem = JSON.parseObject(str, CartItem.class);
                return cartItem;
            }).collect(Collectors.toList());
            return collect;
        } else {
            return null;
        }
    }

    @Override
    public void clearCart(String cartKey) {
        redis.delete(cartKey);
    }

    //勾选购物项
    @Override
    public void checkItem(Long skuId, Integer check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        CartItem cartItem = getCartItem(skuId);
        cartItem.setCheck(check==1?true:false);

        //更新redis
        String s = JSON.toJSONString(cartItem);
        cartOps.put(skuId.toString(), s);
    }

    //增减数量
    @Override
    public void changeItemCount(Long skuId, Integer num) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        CartItem cartItem = getCartItem(skuId);
        cartItem.setCount(num);

        String s = JSON.toJSONString(cartItem);
        cartOps.put(skuId.toString(), s);
    }

    //删除购物项
    @Override
    public void deleteItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId.toString());
    }
}
