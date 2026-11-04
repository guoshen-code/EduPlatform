package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {
    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;
    /*
    查询榜单
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 1. 判断是否为当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 查询我的积分与排名
        PointsBoard myBoard = isCurrent ? queryMyCurrentBoard(key) : queryMyHistoryBoard(season);

        // 3. 查询榜单列表
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) : queryHistoryBoardList(query);

        // 4. 封装VO
        PointsBoardVO vo = new PointsBoardVO();
        // 处理我的信息
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        // 查询用户信息
        if (CollUtils.isEmpty(list)){
            return vo;
        }
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(uIds.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        // 处理榜单列表
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard p : list) {
            PointsBoardItemVO item = new PointsBoardItemVO();
            items.add(item);
            item.setPoints(p.getPoints());
            item.setRank(p.getRank());
            item.setName(userMap.get(p.getUserId()));
        }
        vo.setBoardList(items);
        return vo;
    }
    /*
     * 创建上个赛季的积分榜
     */
    @Override
    public void createPointsBoardTableOfLastSeason(Integer seasonId) {
        getBaseMapper().createPointsBoardTableOfLastSeason("point_board_" + seasonId);
    }

    // 查询历史赛季的榜单列表
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        return null;
    }

    // 查询当前赛季的榜单列表
    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        // 1. 计算分页
        int from = (pageNo - 1) * pageSize;
        // 2. 查询分数
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(typedTuples)){
            return CollUtils.emptyList();
        }
        // 3. 封装
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            PointsBoard p = new PointsBoard();
            String userId = tuple.getValue();
            Double point = tuple.getScore();
            if (userId == null || point == null){
                continue;
            }
            p.setPoints(point.intValue());
            p.setUserId(Long.valueOf(userId));
            p.setRank(rank++);
            list.add(p);
        }

        return list;
    }

    // 查询历史赛季的我的积分与排名
    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;
    }

    // 查询当前赛季的我的积分与排名
    private PointsBoard queryMyCurrentBoard(String key) {
        // 1. 绑定key
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        // 2. 查询当前用户
        Long userId = UserContext.getUser();
        // 3. 查询积分
        Double point = ops.score(userId);
        // 4. 查询排名
        Long rank = ops.reverseRank(userId);
        // 5. 封装返回
        PointsBoard p = new PointsBoard();
        p.setPoints(point == null ? 0 : point.intValue());
        p.setRank(rank == null ? 0 : rank.intValue() + 1);
        return p;
    }
}
