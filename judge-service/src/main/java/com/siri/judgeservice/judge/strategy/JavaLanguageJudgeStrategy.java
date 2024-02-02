package com.siri.judgeservice.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.siri.model.codesandbox.JudgeInfo;
import com.siri.model.dto.question.JudgeCase;
import com.siri.model.dto.question.JudgeConfig;
import com.siri.model.entity.Question;
import com.siri.model.enums.JudgeInfoMessageEnum;

import java.util.List;
import java.util.Optional;

/**
 * Java 程序的判题策略
 */
public class JavaLanguageJudgeStrategy implements JudgeStrategy {

    /**
     * 执行判题
     *
     * @param judgeContext 判题上下文
     * @return 判题结果
     */
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        // 获取判题信息
        JudgeInfo judgeInfo = judgeContext.getJudgeInfo();
        // 获取内存限制
        Long memory = Optional.ofNullable(judgeInfo.getMemory()).orElse(0L);
        // 获取时间限制
        Long time = Optional.ofNullable(judgeInfo.getTime()).orElse(0L);
        // 获取输入列表
        List<String> inputList = judgeContext.getInputList();
        // 获取输出列表
        List<String> outputList = judgeContext.getOutputList();
        // 获取题目
        Question question = judgeContext.getQuestion();
        // 获取判题用例列表
        List<JudgeCase> judgeCaseList = judgeContext.getJudgeCaseList();
        // 判断结果消息枚举
        JudgeInfoMessageEnum judgeInfoMessageEnum = JudgeInfoMessageEnum.ACCEPTED;
        // 创建判题结果对象
        JudgeInfo judgeInfoResponse = new JudgeInfo();
        // 设置内存限制
        judgeInfoResponse.setMemory(memory);
        // 设置时间限制
        judgeInfoResponse.setTime(time);
        // 判断沙箱执行的结果输出数量是否和预期输出数量相等
        if (outputList.size() != inputList.size()) {
            // 如果不相等，设置判断结果消息为 WRONG_ANSWER
            judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
            // 设置判断结果消息
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 依次判断每一项输出和预期输出是否相等
        for (int i = 0; i < judgeCaseList.size(); i++) {
            // 获取当前的判题用例
            JudgeCase judgeCase = judgeCaseList.get(i);
            // 如果当前输出和预期输出不相等
            if (!judgeCase.getOutput().equals(outputList.get(i))) {
                // 设置判断结果消息为 WRONG_ANSWER
                judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
                // 设置判断结果消息
                judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
                return judgeInfoResponse;
            }
        }
        // 判断题目限制
        String judgeConfigStr = question.getJudgeConfig();
        // 将字符串转换为JudgeConfig对象
        JudgeConfig judgeConfig = JSONUtil.toBean(judgeConfigStr, JudgeConfig.class);
        // 获取内存限制
        Long needMemoryLimit = judgeConfig.getMemoryLimit();
        // 获取时间限制
        Long needTimeLimit = judgeConfig.getTimeLimit();
        // 如果内存超过了限制
        if (memory > needMemoryLimit) {
            // 设置判断结果消息为 MEMORY_LIMIT_EXCEEDED
            judgeInfoMessageEnum = JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED;
            // 设置判断结果消息
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // Java 程序本身需要额外执行 10 秒钟
        long JAVA_PROGRAM_TIME_COST = 10000L;
        // 如果超过了时间限制
        if ((time - JAVA_PROGRAM_TIME_COST) > needTimeLimit) {
            // 设置判断结果消息为 TIME_LIMIT_EXCEEDED
            judgeInfoMessageEnum = JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED;
            // 设置判断结果消息
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 设置判断结果消息
        judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
        return judgeInfoResponse;
    }
}
