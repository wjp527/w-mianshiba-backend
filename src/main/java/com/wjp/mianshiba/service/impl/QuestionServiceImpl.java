package com.wjp.mianshiba.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.mianshiba.common.ErrorCode;
import com.wjp.mianshiba.constant.CommonConstant;
import com.wjp.mianshiba.exception.ThrowUtils;
import com.wjp.mianshiba.mapper.QuestionMapper;
import com.wjp.mianshiba.model.dto.question.QuestionQueryRequest;
import com.wjp.mianshiba.model.entity.Question;
import com.wjp.mianshiba.model.entity.QuestionBankQuestion;
import com.wjp.mianshiba.model.entity.User;
import com.wjp.mianshiba.model.enums.ReviewStatusEnum;
import com.wjp.mianshiba.model.vo.QuestionVO;
import com.wjp.mianshiba.model.vo.UserVO;
import com.wjp.mianshiba.service.QuestionBankQuestionService;
import com.wjp.mianshiba.service.QuestionService;
import com.wjp.mianshiba.service.UserService;
import com.wjp.mianshiba.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserService userService;

    @Resource
    private QuestionMapper questionMapper;

    @Lazy
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        // todo 从对象中取值
        String title = question.getTitle();
        String content = question.getContent();
        // 创建数据时，参数不能为空
        if (add) {
            // todo 补充校验规则
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        // todo 补充校验规则
        if (StringUtils.isNotBlank(title)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content)) {
            ThrowUtils.throwIf(content.length() > 10240, ErrorCode.PARAMS_ERROR, "内容过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String picture = questionQueryRequest.getPicture();
        String answer = questionQueryRequest.getAnswer();
        Long userId = questionQueryRequest.getUserId();
        int current = questionQueryRequest.getCurrent();
        int pageSize = questionQueryRequest.getPageSize();
        Integer reviewStatus = questionQueryRequest.getReviewStatus();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();





        // todo 补充需要的查询条件
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);

        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);

        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 对象转封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);
        // 2. 已登录，获取用户点赞、收藏状态
        long questionId = question.getId();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            // 获取点赞
            QueryWrapper<Question> questionThumbQueryWrapper = new QueryWrapper<>();
            questionThumbQueryWrapper.in("id", questionId);
            questionThumbQueryWrapper.eq("userId", loginUser.getId());
            Question questionThumb = questionMapper.selectOne(questionThumbQueryWrapper);
            questionVO.setHasThumb(questionThumb != null);
            // 获取收藏
            QueryWrapper<Question> questionFavourQueryWrapper = new QueryWrapper<>();
            questionFavourQueryWrapper.in("id", questionId);
            questionFavourQueryWrapper.eq("userId", loginUser.getId());
            Question questionFavour = questionMapper.selectOne(questionFavourQueryWrapper);
            questionVO.setHasFavour(questionFavour != null);
        }
        // endregion

        return questionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.objToVo(question);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 已登录，获取用户点赞、收藏状态
        Map<Long, Boolean> questionIdHasThumbMap = new HashMap<>();
        Map<Long, Boolean> questionIdHasFavourMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            Set<Long> questionIdSet = questionList.stream().map(Question::getId).collect(Collectors.toSet());
            loginUser = userService.getLoginUser(request);
            // 获取点赞
            QueryWrapper<Question> questionThumbQueryWrapper = new QueryWrapper<>();
            questionThumbQueryWrapper.in("id", questionIdSet);
            questionThumbQueryWrapper.eq("userId", loginUser.getId());
            List<Question> questionQuestionThumbList = questionMapper.selectList(questionThumbQueryWrapper);
            questionQuestionThumbList.forEach(questionQuestionThumb -> questionIdHasThumbMap.put(questionQuestionThumb.getId(), true));
            // 获取收藏
            QueryWrapper<Question> questionFavourQueryWrapper = new QueryWrapper<>();
            questionFavourQueryWrapper.in("id", questionIdSet);
            questionFavourQueryWrapper.eq("userId", loginUser.getId());
            List<Question> questionFavourList = questionMapper.selectList(questionFavourQueryWrapper);
            questionFavourList.forEach(questionFavour -> questionIdHasFavourMap.put(questionFavour.getId(), true));
        }
        // 填充信息
        questionVOList.forEach(questionVO -> {
            Long userId = questionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionVO.setUser(userService.getUserVO(user));
            questionVO.setHasThumb(questionIdHasThumbMap.getOrDefault(questionVO.getId(), false));
            questionVO.setHasFavour(questionIdHasFavourMap.getOrDefault(questionVO.getId(), false));
        });
        // endregion

        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 根据题库id查询对应的题目【给管理员使用】
     * @param questionQueryRequest
     * @return
     */
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        int current = questionQueryRequest.getCurrent();
        int size = questionQueryRequest.getPageSize();

        // 题目表的查询条件
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        // 获取到题库id
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if(questionBankId != null) {
            // 创建 LambdaQueryWrapper 查询 QuestionBankQuestion 表
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    // 指定查询字段为 questionId，只获取题目 id
                    .select(QuestionBankQuestion::getQuestionId)
                    // 添加查询条件：根据题库 id（questionBankId）匹配关联记录
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            // 查询 QuestionBankQuestion 表，获取关联的题目 id 列表
            List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
            if(CollUtil.isNotEmpty(questionList)) {
                // 取出题目 id集合
                Set<Long> questionIdSet = questionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
                // 添加查询条件：题目 id 在 questionIdSet 集合中
                queryWrapper.in("id", questionIdSet);
            }
        }
        // 查询数据库
        Page<Question> questionPage = this.page(new Page<>(current, size), queryWrapper);
        return questionPage;
    }

}
