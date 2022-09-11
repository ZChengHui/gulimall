package com.atguigu.gulimall.search.thread;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//线程池APi测试
public class ThreadTest {

    public static ExecutorService executor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("start");
//        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            System.out.println("运行结果：zch");
//            return "1";//supplyAsync有返回值return，runAsync没返回值
//        }, executor)/*.whenComplete((res, exception) -> {
//            //回调函数
//            //虽然能得到异常，但没法修改返回数据
//            System.out.println("结果是："+res);
//            System.out.println("异常是："+exception);
//        })*//*.exceptionally(throwable -> {
//            //可以感知异常同时返回默认值
//            return "10";
//        })*/.handle((res, throwable) -> {
//
//            //方法完成后的处理
//            if (res!=null) {
//                return "res";
//            }
//            if (throwable!=null) {
//                return "throwable";
//            }
//            return "handle";
//        });
        /**
         * thenRun不能获取上一步执行结果
         * thenAcceptAsync能接收上一步结果，但无返回值
         * thenApplyAsync既能接收上一步结果，也能传递给下一步数据
         */

//        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
//            System.out.println("任务1");
//            return "1";
//        }, executor);
//        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
//            try {
//                Thread.sleep(30000);
//                System.out.println("任务2");
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            return "2";
//        }, executor);
        //当两个任务都完成才执行
        //不接受上一步结果，没有返回值
//        future1.runAfterBothAsync(future2, () -> {
//            System.out.println("任务3开始");
//        });

        //接受上一步结果，没有返回值
//        future1.thenAcceptBothAsync(future2, (f1, f2) -> {
//            System.out.println(f1+f2);
//        });
        //接受上一步结果，有返回值
//        CompletableFuture<String> future3 = future1.thenCombineAsync(future2, (f1, f2) -> {
//            return f1 + f2 + "fff";
//        }, executor);
//        System.out.println(future3.get());

        //两个任务只有一个完成就执行
        //接受上一步结果，没有返回值
//        future1.runAfterEitherAsync(future2, () -> {
//            System.out.println("任务3开始");
//        });
        //future1，future2返回值类型要相同 可接受上一步结果
//        future1.acceptEitherAsync(future2, (res) -> {
//            System.out.println("任务3开始");
//        });
        //可接受上一步结果，有返回值
//        CompletableFuture<Object> future3 = future1.applyToEitherAsync(future2, (res) -> {
//            System.out.println("任务3开始");
//            return "3";
//        }, executor);
//        System.out.println(future3.get());

        //allOf anyOf
        CompletableFuture<String> futureImg1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("查询商品图片");
            return "hello1.jpg";
        }, executor);
        CompletableFuture<String> futureImg2 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("查询商品属性");
            return "hello2.jpg";
        }, executor);
        CompletableFuture<String> futureImg3 = CompletableFuture.supplyAsync(() -> {
            System.out.println("查询商品介绍");
            return "hello3.jpg";
        }, executor);

//        CompletableFuture<Object> allOf = CompletableFuture.anyOf(futureImg1, futureImg2, futureImg3);
//        allOf.get(); //阻塞等待

//        System.out.println("---------------\n"+futureImg1.get()+futureImg2.get()+futureImg3.get());
//        System.out.println("---------------\n"+allOf.get());
    }
    public static class Thread01 extends Thread {
        @Override
        public void run() {
            System.out.println("当前线程:"+Thread.currentThread().getId());

        }
    }
}
