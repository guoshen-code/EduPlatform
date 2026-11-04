package com.tianji.promotion.utils;

import com.tianji.common.autoconfigure.redisson.enums.LockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/*
 * 自定义锁注解

 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyLock {

    String name();

    long waitTime() default 1;

    long leaseTime() default -1; // -1时看门狗不会失效

    TimeUnit unit() default TimeUnit.SECONDS;

    // 定义锁类型
    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;


    // 定义锁策略
    MyLockStrategy lockStrategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT;
}