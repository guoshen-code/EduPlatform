package com.tianji.promotion.utils;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {
    private final RedissonClient redissonClient;

    private final MyLockFactory lockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        // 1. 创建锁对象
//        RLock lock = redissonClient.getLock(myLock.name());
        RLock lock = lockFactory.getLock(myLock.lockType(), myLock.name()); // 锁类型
        // 2. 尝试获取锁
        boolean isLock = myLock.lockStrategy().tryLock(lock, myLock); //锁策略
        // 3. 判断是否成功
        if (!isLock){
            // 失败，快速结束
            return null;
        }
        try {
            // 成功，执行业务
            return pjp.proceed();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
