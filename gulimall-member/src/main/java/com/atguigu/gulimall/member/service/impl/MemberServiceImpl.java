package com.atguigu.gulimall.member.service.impl;

import com.atguigu.gulimall.member.dao.MemberLevelDao;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UserNameExistException;
import com.atguigu.gulimall.member.vo.LoginVO;
import com.atguigu.gulimall.member.vo.RegisterVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.service.MemberService;

import javax.annotation.Resource;


@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Resource
    private MemberLevelDao memberLevelDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void register(RegisterVO vo) {
        MemberDao dao = this.baseMapper;
        MemberEntity entity = new MemberEntity();

        //设置默认等级
        MemberLevelEntity memberLevel = memberLevelDao.getDefaultLevel();
        entity.setLevelId(memberLevel.getId());

        //设置基本信息
        //唯一性 以异常机制来控制程序执行
        checkPhoneUnique(vo.getPhone());
        checkUserNameUnique(vo.getUsername());

        entity.setMobile(vo.getPhone());
        entity.setUsername(vo.getUsername());

        //密码加密 spring
        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        String encode = bCryptPasswordEncoder.encode(vo.getPassword());
        entity.setPassword(encode);

        entity.setCreateTime(new Date());

        dao.insert(entity);
    }

    @Override
    public void checkPhoneUnique(String phone) throws PhoneExistException{
        Integer count =this.count(
                new QueryWrapper<MemberEntity>()
                        .eq("mobile", phone)
        );
        if (count != 0) throw new PhoneExistException();
    }

    @Override
    public void checkUserNameUnique(String username) throws UserNameExistException{
        Integer count = this.count(
                new QueryWrapper<MemberEntity>()
                        .eq("username", username)
        );
        if (count != 0) throw new UserNameExistException();
    }

    @Override
    public MemberEntity login(LoginVO vo) {
        String loginname = vo.getLoginname();
        //前端明文密码
        String password = vo.getPassword();

        //去数据库查询
        MemberDao dao = this.baseMapper;
        MemberEntity entity = dao.selectOne(
                new QueryWrapper<MemberEntity>()
                        .eq("username", loginname)
                        .or()
                        .eq("mobile", loginname)
        );
        if (entity != null) {
            //密码是否正确
            //数据库加密密码
            String dbPwd = entity.getPassword();
            BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
            //前明文，后密文 调用匹配方法
            Boolean matches = bCryptPasswordEncoder.matches(password, dbPwd);
            if (matches) {
                //登录成功
                return entity;
            } else {
                //登录失败
                return null;
            }
        } else {
            //登录失败
            return null;
        }
    }

}