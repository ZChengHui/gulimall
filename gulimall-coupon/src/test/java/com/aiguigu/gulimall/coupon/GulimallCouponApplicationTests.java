package com.aiguigu.gulimall.coupon;

import com.atguigu.gulimall.coupon.GulimallCouponApplication;
import org.junit.Test;
//import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

//@SpringBootTest(classes = GulimallCouponApplication.class)
//@RunWith(SpringRunner.class)
public class GulimallCouponApplicationTests {

    @Test
    public void contextLoads() {
//        LocalDate now = LocalDate.now();
//        LocalDate plus1Day = now.plusDays(1);
//        LocalDate plus2Day = now.plusDays(2);
//        LocalDate plus3Day = now.plusDays(3);
//        System.out.println(plus1Day+"\n"+plus2Day+"\n"+plus3Day);
//
//        LocalTime min = LocalTime.MIN;
//        LocalTime max = LocalTime.MAX;
//
//        LocalDateTime of = LocalDateTime.of(plus1Day, min);
//        System.out.println(of);

        LocalDate now = LocalDate.now().plusDays(2);
        LocalTime max = LocalTime.MAX;
        LocalDateTime end = LocalDateTime.of(now, max);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println(end.format(formatter));
    }

}
