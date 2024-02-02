package com.siri.judgeservice.judge;

import com.siri.judgeservice.judge.strategy.DefaultJudgeStrategy;
import com.siri.judgeservice.judge.strategy.JavaLanguageJudgeStrategy;
import com.siri.judgeservice.judge.strategy.JudgeContext;
import com.siri.judgeservice.judge.strategy.JudgeStrategy;
import com.siri.model.codesandbox.JudgeInfo;
import com.siri.model.entity.QuestionSubmit;
import org.springframework.stereotype.Service;

/**
 * 判题管理（简化调用）
 */
@Service
public class JudgeManager {

    /**
     * 执行判题
     *
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext) {
        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        String language = questionSubmit.getLanguage();
        JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
        if ("java".equals(language)) {
            judgeStrategy = new JavaLanguageJudgeStrategy();
        }
        return judgeStrategy.doJudge(judgeContext);
    }
}
