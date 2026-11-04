package com.tianji.learning.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.domain.po.PointsBoardSeason;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/pointsBoardSeason")
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;


}
