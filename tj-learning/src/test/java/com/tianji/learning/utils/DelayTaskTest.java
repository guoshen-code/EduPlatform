package com.tianji.learning.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;



class DelayTaskTest {
    @Test
    void testDelayTask() throws InterruptedException {
        // 初始化延迟队列
        DelayQueue<DelayTask<String>> delayQueue = new DelayQueue<>();

        delayQueue.add(new DelayTask<>("task3", Duration.ofSeconds(3)));
        delayQueue.add(new DelayTask<>("task1", Duration.ofSeconds(1)));
        delayQueue.add(new DelayTask<>("task2", Duration.ofSeconds(2)));


        while (true){
            DelayTask<String> task = delayQueue.take();
            System.out.println("执行任务: " + task.getData());
        }
    }
}