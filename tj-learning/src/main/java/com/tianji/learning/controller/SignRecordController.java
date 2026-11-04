package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "签到记录相关接口")
@RestController
@RequestMapping("/sign-records")
@RequiredArgsConstructor
public class SignRecordController {
    private final ISignRecordService signRecordService;


    @ApiOperation("签到功能相关接口")
    @PostMapping
    public SignResultVO addSignRecords(){
        return signRecordService.addSignRecords();
    }

}
