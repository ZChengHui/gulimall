package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.common.constant.WareConstant;
import com.atguigu.gulimall.ware.entity.PurchaseDetailEntity;
import com.atguigu.gulimall.ware.service.PurchaseDetailService;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.MergeVO;
import com.atguigu.gulimall.ware.vo.PurchaseDoneVO;
import com.atguigu.gulimall.ware.vo.PurchaseItemDoneVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.PurchaseDao;
import com.atguigu.gulimall.ware.entity.PurchaseEntity;
import com.atguigu.gulimall.ware.service.PurchaseService;
import org.springframework.transaction.annotation.Transactional;


@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {

    @Autowired
    private PurchaseDetailService purchaseDetailService;

    @Autowired
    private WareSkuService wareSkuService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                new QueryWrapper<PurchaseEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPageUnreceive(Map<String, Object> params) {
        QueryWrapper<PurchaseEntity> wrapper = new QueryWrapper<PurchaseEntity>();
        wrapper.eq("status", 0).or().eq("status", 1);

        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void mergePurchase(MergeVO mergeVO) {
        Long purchaseId = mergeVO.getPurchaseId();
        //没有指定采购单，创建采购单，并未分配员工
        if (purchaseId == null) {
            PurchaseEntity purchaseEntity = new PurchaseEntity();
            purchaseEntity.setCreateTime(new Date());
            purchaseEntity.setUpdateTime(new Date());
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.CREATED.getCode());
            this.save(purchaseEntity);
            purchaseId = purchaseEntity.getId();
        }

        List<Long> items = mergeVO.getItems();
        Long finalPurchaseId = purchaseId;
        //给采购需求修改采购单id
        List<PurchaseDetailEntity> collect = items.stream().map(i -> {
            PurchaseDetailEntity detailEntity = new PurchaseDetailEntity();
            detailEntity.setId(i);
            detailEntity.setPurchaseId(finalPurchaseId);
            detailEntity.setStatus(WareConstant.PurchaseDetailStatusEnum.ASSIGNED.getCode());
            return detailEntity;
        }).collect(Collectors.toList());

        purchaseDetailService.updateBatchById(collect);

        //更新时间
        PurchaseEntity purchaseEntity = new PurchaseEntity();
        purchaseEntity.setId(purchaseId);
        purchaseEntity.setUpdateTime(new Date());
        this.updateById(purchaseEntity);
    }

    //领取采购单
    @Transactional
    @Override
    public void received(List<Long> ids) {
        //采购单
        List<PurchaseEntity> collect = ids.stream().map(id -> {
            PurchaseEntity byId = this.getById(id);
            return byId;
        }).filter(item -> {
            //过滤掉新建和分配状态的采购单
            return (item.getStatus() == WareConstant.PurchaseStatusEnum.CREATED.getCode()
                    || item.getStatus() == WareConstant.PurchaseStatusEnum.ASSIGNED.getCode());
        }).map(item -> {
            //设置接收状态
            item.setStatus(WareConstant.PurchaseStatusEnum.RECEIVE.getCode());
            item.setUpdateTime(new Date());
            return item;
        }).collect(Collectors.toList());

        if (collect != null && collect.size() > 0) {
            this.updateBatchById(collect);
            //包含的采购需求
            collect.forEach(item -> {
                List<PurchaseDetailEntity> entities = purchaseDetailService.listDetailPurchaseId(item.getId());
                if (entities != null && entities.size() > 0) {
                    List<PurchaseDetailEntity> detailEntities = entities.stream().map(entity -> {

                        PurchaseDetailEntity purchaseDetail = new PurchaseDetailEntity();
                        purchaseDetail.setId(entity.getId());
                        purchaseDetail.setStatus(WareConstant.PurchaseDetailStatusEnum.BUYING.getCode());
                        return purchaseDetail;

                    }).collect(Collectors.toList());

                    purchaseDetailService.updateBatchById(detailEntities);
                }
            });
        }
    }

    @Transactional
    @Override
    public void done(PurchaseDoneVO doneVO) {

        //改变每个采购项状态
        List<PurchaseItemDoneVO> items = doneVO.getItems();
        Boolean flag = true;
        List<PurchaseDetailEntity> updates = new ArrayList<>();

        for(PurchaseItemDoneVO item : items) {
            PurchaseDetailEntity detail = new PurchaseDetailEntity();
            if (item.getStatus() == WareConstant.PurchaseDetailStatusEnum.ERROR.getCode()) {
                flag = false;
                detail.setStatus(item.getStatus());
            } else {
                detail.setStatus(WareConstant.PurchaseDetailStatusEnum.FINISH.getCode());
                //成功采购就入库
                PurchaseDetailEntity entity = purchaseDetailService.getById(item.getItemId());
                wareSkuService.addStock(entity.getSkuId(), entity.getWareId(), entity.getSkuNum());
            }
            detail.setId(item.getItemId());
            updates.add(detail);
        }
        purchaseDetailService.updateBatchById(updates);

        //改变采购单状态
        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(doneVO.getId());
        purchase.setStatus(
                flag ? WareConstant.PurchaseStatusEnum.FINISH.getCode()
                        : WareConstant.PurchaseStatusEnum.ERROR.getCode()
        );
        purchase.setUpdateTime(new Date());
        this.updateById(purchase);

    }

}