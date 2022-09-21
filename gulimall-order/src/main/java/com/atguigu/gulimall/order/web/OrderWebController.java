package com.atguigu.gulimall.order.web;

import com.atguigu.common.exception.NoStockException;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.OrderConfirmVO;
import com.atguigu.gulimall.order.vo.OrderSubmitVO;
import com.atguigu.gulimall.order.vo.SubmitOrderResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.concurrent.ExecutionException;

@Controller
public class OrderWebController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/toTrade")
    public String toTrade(Model model) throws ExecutionException, InterruptedException {
        OrderConfirmVO vo = orderService.confirmOrder();
        model.addAttribute("orderConfirmData", vo);
        //展示订单确认页信息
        return "confirm";
    }

    /**
     * 下单功能
     * @param vo
     * @return
     */
    @PostMapping("/submitOrder")
    public String submitOrder(OrderSubmitVO vo, Model model, HttpSession session) {
        try {
            SubmitOrderResponseVO responseVO = orderService.submitOrder(vo);
            //下单失败回到订单页重新确认订单信息
            if (responseVO.getCode() == 0) {
                //下单成功
                model.addAttribute("submitOrderResp", responseVO);
                return "pay";
            } else {
                //下单失败
                String msg = "下单失败，";
                switch (responseVO.getCode()) {
                    case 1:
                        msg += "订单信息过期，请刷新";
                        break;
                    case 2:
                        msg += "订单商品价格变动，请重新提交";
                        break;
                    case 3:
                        msg += "库存不足";
                        break;
                }
                session.setAttribute("msg", msg);
                return "redirect:http://order.gulimall.com/toTrade";
            }
        } catch (NoStockException e) {
            //错误页面
            session.setAttribute("msg", e.getMessage());
            return "redirect:http://order.gulimall.com/toTrade";
        }
    }

}
