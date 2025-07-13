package com.wjp.mianshiba.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.mianshiba.common.ErrorCode;
import com.wjp.mianshiba.constant.CommonConstant;
import com.wjp.mianshiba.exception.BusinessException;
import com.wjp.mianshiba.exception.ThrowUtils;
import com.wjp.mianshiba.mapper.QuestionBankQuestionMapper;
import com.wjp.mianshiba.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.wjp.mianshiba.model.entity.Question;
import com.wjp.mianshiba.model.entity.QuestionBank;
import com.wjp.mianshiba.model.entity.QuestionBankQuestion;
import com.wjp.mianshiba.model.entity.User;

import com.wjp.mianshiba.model.vo.QuestionBankQuestionVO;
import com.wjp.mianshiba.model.vo.UserVO;

import com.wjp.mianshiba.service.QuestionBankQuestionService;
import com.wjp.mianshiba.service.QuestionBankService;
import com.wjp.mianshiba.service.QuestionService;
import com.wjp.mianshiba.service.UserService;
import com.wjp.mianshiba.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 题目题库关联服务实现
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserService userService;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add                  对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);

        Long questionBankId = questionBankQuestion.getQuestionBankId();
        Long questionId = questionBankQuestion.getQuestionId();

        ThrowUtils.throwIf(questionBankId == null, ErrorCode.NOT_FOUND_ERROR, "题库id不存在");
        ThrowUtils.throwIf(questionId == null, ErrorCode.NOT_FOUND_ERROR, "题目id不存在");

        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");

        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");

//        // todo 从对象中取值
//        String title = questionBankQuestion.getTitle();
//        // 创建数据时，参数不能为空
//        if (add) {
//            // todo 补充校验规则
//            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
//        }
//        // 修改数据时，有参数则校验
//        // todo 补充校验规则
//        if (StringUtils.isNotBlank(title)) {
//            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
//        }
    }

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();

        // todo 补充需要的查询条件
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目题库关联封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);

        // endregion

        return questionBankQuestionVO;
    }

    /**
     * 分页获取题目题库关联封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }

    /**
     * 批量添加题目到题库
     *
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     * @return
     */
    @Override
    public void batchAddQuestionsToBank(List<Long> questionIdList, long questionBankId, User loginUser) {
        // 参数校验
        if (CollUtil.isEmpty(questionIdList) || questionBankId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数异常");
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 判断题目是否存在
        // 减少 select * from question
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)  // 初始化，指定实体类型为 Question
                .select(Question::getId)  // 指定查询字段：仅返回 id 列
                .in(Question::getId, questionIdList); // 条件：id 在 questionIdList 集合中

        // 合法的题目 id 列表
        // listObjs: 高效查询单列数据 仅返回查询结果的第一列数据，因此 obj 的值就是每条记录中 id 字段的值
        // (obj) -> (Long) obj 负责将数据库返回的 Object 类型强制转换为 Long 类型（假设 id 字段在数据库中是 BIGINT 等整型）。
        // 转换后得到 List<Long> validQuestionIdList，即符合条件的 id 列表。
        List<Long> validQuestionIdList = questionService.listObjs(questionLambdaQueryWrapper, (obj) -> (Long) obj);
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR);

        // 检查那些题目不存在与题库，避免重复插入
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, questionIdList);

        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);

        // 已存在与题库中的题目id
        Set<Long> existQuestionIdSet = existQuestionList.stream().map(QuestionBankQuestion::getQuestionId)
                .collect(Collectors.toSet());

        // 已存在与题库中的题目id，不要重复添加
        validQuestionIdList = validQuestionIdList.stream().filter(questionId -> !existQuestionIdSet.contains(questionId)).collect(Collectors.toList());

        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "所有题目均已上传到该题库中");

        // 题库是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库列表为空");

        // 自定义线程池 (I/O密集型线程池)
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                20,     // 核心线程数 【实际工作的人】
                50,                // 最大线程数 【临时工】
                60L,               // 线程空闲存活时间
                TimeUnit.SECONDS,  // 线程存活时间单位
                new LinkedBlockingQueue<>(1000), // 阻塞队列容量
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略: 由调用线程处理任务
        );

        // 保存所有批次任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 分批次处理，避免长事务，假设每次处理1000条数据
        int batchSize = 1;
        int totalQuestionListSize = validQuestionIdList.size();
        for (int i = 0; i < totalQuestionListSize; i+=batchSize) {
            // 生成每批次的数据
            List<Long> subList = validQuestionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));
            List<QuestionBankQuestion> questionBankQuestions = subList.stream().map(
                    questionId -> {
                        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                        questionBankQuestion.setQuestionBankId(questionBankId);
                        questionBankQuestion.setQuestionId(questionId);
                        questionBankQuestion.setUserId(loginUser.getId());
                        return questionBankQuestion;
                    }
            ).collect(Collectors.toList());
            // 获取代理
            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionService) AopContext.currentProxy();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // 使用事务处理每批数据
                // 如果想要在当前的Service中 使用 打了@Transational事务的注解的Service，那么必须使用代理对象来调用方法
                // Error: this.batchAddQuestionsToBankInner(questionBankQuestions);
//                batchAddQuestionsToBankInner(questionBankQuestions);
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
            }, customExecutor);
            futures.add(future);

            // 阻塞 等待所有任务完成,在往后面执行
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 关闭线程池
            customExecutor.shutdown();
        }
    }

    /**
     * 批量添加题目到题库(事务: 仅供内部调用)
     *
     * @param questionBankQuestions
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        try {
            // saveBatch 是 MyBatis-Plus 提供的方法，用于批量插入数据到数据库。
            // 它会将传入的实体列表一次性插入数据库，相比单条插入效率更高。
            // 注意：使用时需确保数据量不过大以避免内存问题，且通常需要在事务中执行。
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突违反其他完整性约束,错误信息: {}",  e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库链接问题、事务问题等导致操作失败, 错误信息: {}",  e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库链接问题、事务问题等导致操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库发生未知错误, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "想题库添加题目失败");
        }

    }

    /**
     * 批量移除题目到题库
     *
     * @param questionIdList
     * @param questionBankId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsToBank(List<Long> questionIdList, long questionBankId) {
        // 参数校验
        if (CollUtil.isEmpty(questionIdList) || questionBankId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数异常");
        }

        // 执行关联删除
        for (Long questionId : questionIdList) {
            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();

            QueryWrapper<QuestionBankQuestion> questionBankQuestionQueryWrapper = new QueryWrapper<>();
            questionBankQuestionQueryWrapper.eq("questionBankId", questionBankId);
            questionBankQuestionQueryWrapper.eq("questionId", questionId);
            boolean result = this.remove(questionBankQuestionQueryWrapper);

            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库移除题目失败");

        }

    }

}
