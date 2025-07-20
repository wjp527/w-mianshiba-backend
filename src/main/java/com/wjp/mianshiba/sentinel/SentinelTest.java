package com.wjp.mianshiba.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 限流测试
 */
public class SentinelTest {
    public static void main(String[] args) {
        // 配置规则.
        initFlowRules();

        while (true) {
            // 1.5.0 版本开始可以直接利用 try-with-resources 特性
            try (Entry entry = SphU.entry("HelloWorld")) {
                // 被保护的逻辑
                System.out.println("hello world");
            } catch (BlockException ex) {
                // 处理被流控的逻辑
                System.out.println("blocked!");
            }
        }
    }

    private static void initFlowRules(){
        // 定义规则列表
        List<FlowRule> rules = new ArrayList<>();
        // 定义限流规则
        FlowRule rule = new FlowRule();
        // 对哪个资源【HelloWorld】进行限流
        rule.setResource("HelloWorld");
        // 设置限流策略
        // QPS: 判断你每秒访问的次数
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // Set limit QPS to 20.
        rule.setCount(20);
        // 添加规则
        rules.add(rule);
        // 使用加载器进行加载规则
        FlowRuleManager.loadRules(rules);
    }
}
