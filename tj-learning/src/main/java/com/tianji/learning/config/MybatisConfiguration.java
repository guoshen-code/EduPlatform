package com.tianji.learning.config;



import ch.qos.logback.classic.db.names.TableName;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.tianji.learning.utils.TableInfoContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MybatisConfiguration {

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInterceptor() {
        // ① 创建一个 Map，key = 需要被替换的逻辑表名，value = 替换策略
        Map<String, TableNameHandler> map = new HashMap<>(1);
        // ② 注册规则：当 SQL 中出现 "point_board" 时，
        //    调用 TableInfoContext.getInfo() 获取当前线程绑定的真实表名来替换
        map.put("points_board", (sql, tableName) -> TableInfoContext.getInfo());
        // ③ 创建 MyBatis-Plus 的动态表名拦截器插件
        return new DynamicTableNameInnerInterceptor(map);
    }

}
