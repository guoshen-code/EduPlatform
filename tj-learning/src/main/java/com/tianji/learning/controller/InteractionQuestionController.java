package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.AIService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.domain.po.InteractionQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 控制器
 * </p>
 *
 * @author author
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/questions")
@Api(tags = "互动问题相关接口")
public class InteractionQuestionController {

    private final IInteractionQuestionService interactionQuestionService;
    private final AIService aiService;

    @PostMapping
    @ApiOperation("新增互动问题")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO) {
        InteractionQuestion interactionQuestion = interactionQuestionService.saveQuestion(questionDTO);
        // 自动回复
        aiService.autoReply(interactionQuestion);
    }


    @GetMapping("/page")
    @ApiOperation("分页查询互动问题")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return interactionQuestionService.queryQuestionPage(query);
    }


    @GetMapping("/{id}")
    @ApiOperation("根据id查询互动问题")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id){
        return interactionQuestionService.queryQuestionById(id);
    }




}
