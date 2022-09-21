package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.feign.MemberFeignService;
import com.atguigu.gulimall.ware.vo.FareResponseVO;
import com.atguigu.gulimall.ware.vo.MemberAddressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareInfoDao;
import com.atguigu.gulimall.ware.entity.WareInfoEntity;
import com.atguigu.gulimall.ware.service.WareInfoService;
import org.springframework.util.StringUtils;


@Service("wareInfoService")
public class WareInfoServiceImpl extends ServiceImpl<WareInfoDao, WareInfoEntity> implements WareInfoService {

    @Autowired
    private MemberFeignService memberFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareInfoEntity> wrapper =  new QueryWrapper<WareInfoEntity>();

        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.eq("id", key).or()
                    .like("name", key).or()
                    .like("address", key).or()
                    .like("areacode", key);
        }

        IPage<WareInfoEntity> page = this.page(
                new Query<WareInfoEntity>().getPage(params),
               wrapper
        );

        return new PageUtils(page);
    }

    //根据收获地址计算运费
    @Override
    public FareResponseVO getFare(Long addrId) {
        R r = memberFeignService.addrInfo(addrId);
        MemberAddressVO data = r.getData("memberReceiveAddress", new TypeReference<MemberAddressVO>() {});
        FareResponseVO vo = new FareResponseVO();
        vo.setAddress(data);
        if (data != null) {
            String phone = data.getPhone();
            String substring = phone.substring(phone.length() - 1);
            BigDecimal fare = new BigDecimal(substring);
            vo.setFare(fare);
            return vo;
        }
        BigDecimal fare = new BigDecimal(0);
        vo.setFare(fare);
        return vo;
    }

}