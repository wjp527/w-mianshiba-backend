package com.wjp.mianshiba.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.wjp.mianshiba.annotation.AuthCheck;
import com.wjp.mianshiba.common.BaseResponse;
import com.wjp.mianshiba.common.DeleteRequest;
import com.wjp.mianshiba.common.ErrorCode;
import com.wjp.mianshiba.common.ResultUtils;
import com.wjp.mianshiba.constant.UserConstant;
import com.wjp.mianshiba.exception.BusinessException;
import com.wjp.mianshiba.exception.ThrowUtils;
import com.wjp.mianshiba.manager.CounterManager;
import com.wjp.mianshiba.model.dto.question.*;
import com.wjp.mianshiba.model.dto.questionBank.QuestionBankQueryRequest;
import com.wjp.mianshiba.model.entity.Question;
import com.wjp.mianshiba.model.entity.User;
import com.wjp.mianshiba.model.vo.QuestionBankVO;
import com.wjp.mianshiba.model.vo.QuestionVO;
import com.wjp.mianshiba.service.QuestionService;
import com.wjp.mianshiba.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.blobstore.BlobStoreException;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 题目接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 检测和处置爬虫
        User loginUser = userService.getLoginUser(request);
        crawlerDetect(loginUser.getId());

        String key = "question_detail_" + id;
        if(JdHotKeyStore.isHotKey(key)) {
            Object cacheQuestion = JdHotKeyStore.get(key);
            if (cacheQuestion != null) {
                return ResultUtils.success((QuestionVO) cacheQuestion);
            }
        }
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);

        // 设置缓存
        JdHotKeyStore.smartSet(key, questionService.getQuestionVO(question, request));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    @Resource
    private CounterManager counterManager;

    /**
     * 检测爬虫
     * @param loginUserId 登录用户id
     */
    private void crawlerDetect(long loginUserId) {
        // 调用多少次时 预警
        final int WARN_COUNT = 10;
        // 调用多少次时 封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 一分钟内访问次数，180秒后过期
        long count = counterManager.incrAndGetCounter(key, 60, TimeUnit.MINUTES, 180);
        // 是否封号
        if(count >= BAN_COUNT) {
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            User user = new User();
            user.setId(loginUserId);
            user.setUserRole("ban");
            // 更新
            boolean res = userService.updateById(user);
            if(!res) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新用户状态失败");
            }
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "您的账号已被封号");
        }
        // 是否告警
        if(count == WARN_COUNT) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "您的账号存在恶意访问，请勿频发访问");
        }
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
       ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取题目列表（封装类 - 限流版）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/sentinel")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 基于 IP 限流
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try{
            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, remoteAddr);
            // 被保护的业务逻辑
            // 查询数据库
            Page<Question> questionPage = questionService.page(new Page<>(current, size),
                    questionService.getQueryWrapper(questionQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch(Throwable ex) {
            // 普通业务异常
            if(!BlockException.isBlockException(ex)) {
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }
            // 降级操作
            if(ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
        } finally {
            // 释放资源
            if (entry != null) {
                entry.exit(1, remoteAddr);
            }
        }
    }

    /**
     * listQuestionVOByPageSentinel： 降级操作：直接返回本地数据
     * @param questionQueryRequest
     * @param request
     * @param ex
     * @return
     */
    public BaseResponse<Page<QuestionVO>> handleFallback(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        // 限流操作
        return ResultUtils.success(null);
    }


    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion

    /**
     * 删除题目
     *
     * @param questionBatchDeleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchRemoveQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest, HttpServletRequest request) {
        if (questionBatchDeleteRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<Long> questionIdList = questionBatchDeleteRequest.getQuestionIdList();

        User user = userService.getLoginUser(request);

        // 仅本人或管理员可删除
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
         questionService.batchRemoveQuestions(questionIdList);
        return ResultUtils.success(true);
    }

}
