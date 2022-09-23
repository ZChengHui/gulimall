package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTO;
import com.atguigu.common.to.mq.StockDetailTO;
import com.atguigu.common.to.mq.StockLockedTO;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVO;
import com.atguigu.gulimall.ware.vo.OrderVO;
import com.atguigu.gulimall.ware.vo.SkuHasStockVO;
import com.atguigu.gulimall.ware.vo.WareSkuLockVO;
import com.rabbitmq.client.Channel;
import lombok.Data;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;


@RabbitListener(queues = "stock.release.stock.queue")
@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Resource
    private WareSkuDao wareSkuDao;

    @Resource
    private WareOrderTaskDetailService taskDetailService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private WareOrderTaskDetailService orderTaskDetailService;

    @Autowired
    private WareOrderTaskService orderTaskService;

    @Autowired
    private OrderFeignService orderFeignService;

    /**
     * 1.库存自动解锁
     * 下单成功，库存锁定成功，其他业务调用失败，导致订单回滚
     *      之前锁定的库存就要自动解锁
     * 2.订单失败
     * 库存不足，锁库存失败
     *
     * 解锁库存消息失败，启动手动ack channel com.rabbitmq.client.Channel;
     */

    //解锁库存业务
    @Override
    public void unLockStock(StockLockedTO to) {
        //try {
            StockDetailTO detail = to.getDetail();
            Long detailId = detail.getId();//库存工作单详情id
            /** 解锁
             *  查询数据库中锁定库存订单信息
             *  有 库存锁定成功
             *          1）、没有订单（订单回滚了），必须解锁库存
             *          2）、有订单，订单状态（系统取消，用户取消）
             *              已取消：解锁库存
             *              未取消：不能解锁
             *  没有 库存不足导致锁定库存失败，无需解锁
             */
            WareOrderTaskDetailEntity byId = orderTaskDetailService.getById(detailId);
            if (byId != null) {
                //解锁
                Long id = to.getId();//库存工作单id
                WareOrderTaskEntity orderTaskEntity = orderTaskService.getById(id);
                String orderSn = orderTaskEntity.getOrderSn();
                //获取下单订单
                R r = orderFeignService.getOrderStatus(orderSn);
                if (r.getCode() == 0) {
                    //订单数据返回成功
                    OrderVO data = r.getData("data", new TypeReference<OrderVO>() {
                    });
                    //下单订单不存在 或 下单订单取消状态
                    if (data == null || data.getStatus() == 4) {
                        //库存订单状态为锁定 才能解锁库存
                        if (byId.getLockStatus() == 1) {
                            unLockStock(detail.getSkuId(), detail.getWareId(), detail.getSkuNum(), detailId);
                        }
                    }
                } else {
                    throw new RuntimeException("远程服务失败");
                }
            }
    }

    //增设订单取消，释放库存 / 防止订单消息卡顿，库存消息优先到期
    @Transactional
    @Override
    public void unLockStock(OrderTO order) {
        String orderSn = order.getOrderSn();
        //查看最新库存状态，防止重复解锁库存
        WareOrderTaskEntity task = orderTaskService.getOrderTaskByOrderSn(orderSn);
        Long taskId = task.getId();
        //按照工作单找到所有没有解锁的库存 进行解锁
        List<WareOrderTaskDetailEntity> list = orderTaskDetailService.list(
                new QueryWrapper<WareOrderTaskDetailEntity>()
                        .eq("task_id", taskId)
                        .eq("lock_status", 1)
        );
        //Long skuId, Long wareId, Integer num, Long taskDetailId
        for (WareOrderTaskDetailEntity entity : list) {
            unLockStock(entity.getSkuId(), entity.getWareId(), entity.getSkuNum(), entity.getId());
        }
    }

    //释放库存
    private void unLockStock(Long skuId, Long wareId, Integer num, Long taskDetailId) {
        //库存回滚
        wareSkuDao.unLockStock(skuId, wareId, num);
        //库存订单状态修改
        WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity();
        entity.setId(taskDetailId);
        entity.setLockStatus(2);//解锁状态
        taskDetailService.updateById(entity);
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        String skuId = (String) params.get("skuId");
        if (!StringUtils.isEmpty(skuId)) {
            wrapper.eq("sku_id", skuId);
        }

        String wareId = (String) params.get("wareId");
        if (!StringUtils.isEmpty(wareId)) {
            wrapper.eq("ware_id", wareId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        //判断如果还没有库存记录，就是新增操作
        Integer count = wareSkuDao.selectCount(
                new QueryWrapper<WareSkuEntity>()
                        .eq("sku_id", skuId).eq("ware_id", wareId)
        );
        if (count == 0) {
            WareSkuEntity skuEntity = new WareSkuEntity();
            skuEntity.setSkuId(skuId);
            skuEntity.setWareId(wareId);
            skuEntity.setStock(skuNum);
            //TODO 远程调用设置商品名字 失败后自己catch
            //TODO 优化服务降级处理
            try {
                R r = productFeignService.info(skuId);
                Map<String, Object> data = (Map<String, Object>) r.get("skuInfo");
                if (r.getCode() == 0) {
                    skuEntity.setSkuName(data.get("skuName").toString());
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            wareSkuDao.insert(skuEntity);
        } else {
            wareSkuDao.addStock(skuId, wareId, skuNum);
        }
    }

    /**
     * 远程调用查询库存
     * @param skuIds
     * @return
     */
    @Override
    public List<SkuHasStockVO> hasStock(List<Long> skuIds) {
        List<SkuHasStockVO> collect = skuIds.stream().map(sku -> {
            SkuHasStockVO vo = new SkuHasStockVO();
            Long count = wareSkuDao.getSkuStock(sku);
            vo.setSkuId(sku);
            vo.setHasStock(count == null ? false : count>0);
            return vo;
        }).collect(Collectors.toList());
        return collect;
    }

    /**
     * 为订单锁定库存 运行时异常就回滚
     * @param vo
     * @return
     *
     * 库存解锁的场景
     * 1）、下单成功，订单过期或手动取消
     *      都要解锁库存
     * 2）、下单成功，库存锁定成功，其他业务调用失败，导致订单回滚
     *      之前锁定的库存就要自动解锁
     * 3）、
     */
    @Transactional
    @Override
    public boolean orderLockStock(WareSkuLockVO vo) {
        /**
         * 保存库存工作单 wms_ware_order_task
         * 追溯
         */
        WareOrderTaskEntity orderTaskEntity = new WareOrderTaskEntity();
        orderTaskEntity.setOrderSn(vo.getOrderSn());
        orderTaskService.save(orderTaskEntity);


        //找到每个商品在 那个仓库 有库存
        //订单中有多个商品
        //一个商品 存放在 多个仓库
        List<OrderItemVO> locks = vo.getLocks();
        List<SkuWareHasStock> collect = locks.stream().map(item -> {
            SkuWareHasStock stock = new SkuWareHasStock();
            Long skuId = item.getSkuId();
            stock.setSkuId(skuId);
            //商品需求数量
            stock.setNum(item.getCount());
            //查询这个商品 在哪里 有库存
            List<Long> wareId = wareSkuDao.listWareIdHasSkuStock(skuId);
            stock.setWareId(wareId);
            return stock;
        }).collect(Collectors.toList());
        //是否都可以锁定库存
        boolean allLock = true;
        //锁定库存
        for (SkuWareHasStock hasStock : collect) {
            //此商品是否可锁定库存
            boolean skuStock = false;
            Long skuId = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();
            if (wareIds == null || wareIds.size() == 0) {
                //这个商品 没有任何仓库有库存
                allLock = false;
                throw new NoStockException(skuId);
            }
            //尝试每个仓库，如果可以锁定就结束，不可以则遍历其余仓库
            //如果每一个商品都锁定成功，将当前商品锁定了几件的工作单记录发给MQ
            //锁定失败，前面保存的工作单信息就回滚了
            for (Long wareId : wareIds) {
                int count = wareSkuDao.lockSkuStock(skuId, wareId, hasStock.getNum());
                if (count == 1) {
                    skuStock = true;
                    //TODO 锁定成功
                    /**
                     * 锁成功库存单 wms_ware_order_task_detail
                     */
                    WareOrderTaskDetailEntity orderTaskDetailEntity = new WareOrderTaskDetailEntity(
                            null,
                            skuId,
                            "",
                            hasStock.getNum(),//商品需求数量
                            orderTaskEntity.getId(),
                            wareId,
                            1//锁定成功状态码
                    );
                    orderTaskDetailService.save(orderTaskDetailEntity);
                    //TODO 发送消息 为了追溯库存 回滚操作
                    StockLockedTO stockLockedTO = new StockLockedTO();
                    stockLockedTO.setId(orderTaskEntity.getId());//设置工作单id
                    StockDetailTO stockDetailTO = new StockDetailTO();
                    BeanUtils.copyProperties(orderTaskDetailEntity, stockDetailTO);//设置工作单详情
                    stockLockedTO.setDetail(stockDetailTO);

                    rabbit.convertAndSend(
                            "stock-event-exchange",
                            "stock.locked",
                            stockLockedTO
                    );
                    break;
                }
            }
            //存在商品没能锁定库存
            if (!skuStock) {
                allLock = false;
                throw new NoStockException(skuId);
            }
        }
        return allLock;
    }

    @Data
    class SkuWareHasStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }

}