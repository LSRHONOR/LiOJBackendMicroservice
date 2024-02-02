package com.siri.judgeservice.judge;

import cn.hutool.json.JSONUtil;
import com.siri.common.common.ErrorCode;
import com.siri.common.exception.BusinessException;
import com.siri.judgeservice.judge.codesandbox.CodeSandbox;
import com.siri.judgeservice.judge.codesandbox.CodeSandboxFactory;
import com.siri.judgeservice.judge.codesandbox.CodeSandboxProxy;
import com.siri.judgeservice.judge.strategy.JudgeContext;
import com.siri.model.codesandbox.ExecuteCodeRequest;
import com.siri.model.codesandbox.ExecuteCodeResponse;
import com.siri.model.codesandbox.JudgeInfo;
import com.siri.model.dto.question.JudgeCase;
import com.siri.model.entity.Question;
import com.siri.model.entity.QuestionSubmit;
import com.siri.model.enums.QuestionSubmitStatusEnum;
import com.siri.serviceclient.service.QuestionFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:example}")
    private String type;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionFeignClient.getQuestionSubmitById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionFeignClient.getQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 2）如果题目提交状态不为等待中，就不用重复执行了
        if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }
        // 3）更改判题（题目提交）的状态为 “判题中”，防止重复执行
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean update = questionFeignClient.updateQuestionSubmitById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        question.setSubmitNum(question.getSubmitNum() + 1);
        boolean questionUpdate = questionFeignClient.updateQuestion(question);
        if (!questionUpdate) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目提交数更新错误");
        }
        // 4）调用沙箱，获取到执行结果
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        // 获取输入用例
        String judgeCaseStr = question.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        // 5）根据沙箱的执行结果，设置题目的判题状态和信息
        JudgeInfo judgeInfo;
        if (executeCodeResponse.getStatus() != 1) {
            judgeInfo = executeCodeResponse.getJudgeInfo();
        } else {
            List<String> outputList = executeCodeResponse.getOutputList();
            JudgeContext judgeContext = new JudgeContext();
            judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
            judgeContext.setInputList(inputList);
            judgeContext.setOutputList(outputList);
            judgeContext.setJudgeCaseList(judgeCaseList);
            judgeContext.setQuestion(question);
            judgeContext.setQuestionSubmit(questionSubmit);
            judgeInfo = judgeManager.doJudge(judgeContext);
        }
        // 6）修改数据库中的判题结果
        questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        if (judgeInfo.getMessage().equals("Accepted")) {
            questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
            Question questions = questionFeignClient.getQuestionById(questionId);
            questions.setAcceptedNum(question.getAcceptedNum() + 1);
            boolean flag = questionFeignClient.updateQuestion(question);
            if (!flag) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目通过数更新错误");
            }
        } else {
            questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
        }
//        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        update = questionFeignClient.updateQuestionSubmitById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        return questionFeignClient.getQuestionSubmitById(questionSubmitId);
    }
}
