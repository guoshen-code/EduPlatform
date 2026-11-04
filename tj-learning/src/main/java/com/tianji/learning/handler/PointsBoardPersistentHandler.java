package com.tianji.learning.handler;


import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;


    /*
     * 每年1月1日0点3分执行，创建上一赛季的榜单数据
     */
//    @Scheduled(cron = "0 0 3 1 * ?") // 每年1月1日0点3分执行，创建上一赛季的榜单数据
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        // 获取上个月时间
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);

        // 查询赛季id
        Integer seasonId = seasonService.querySeasonByTime(lastMonth);
        if (seasonId == null) {
            return;
        }
        // 创建表
        pointsBoardService.createPointsBoardTableOfLastSeason(seasonId);
    }

    @XxlJob("savePointsBoard3DB")
    public void savePointsBoard3DB() {
        // 1. 获取上个月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2. 计算动态表名
        // 2.1 查询赛季id
        Integer seasonId = seasonService.querySeasonByTime(time);
        // 2.1 存入ThreadLocal
        TableInfoContext.setInfo("points_board_" + seasonId);
        // 3 查询榜单数据
        // 3.1 拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.2 查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 1000;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4. 持久化到数据库
            // 4.1 把排名信息写入id，不再单独作为字段
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            // 4.2 持久化
            pointsBoardService.saveBatch(boardList);
            // 5. 翻页
            pageNo += total;
        }
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        // 1. 获取上个月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2 拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3. 删除缓存
        redisTemplate.unlink(key);

    }

}
