package com.alibaba.smart.framework.engine.overide;

import com.alibaba.smart.framework.engine.common.expression.evaluator.ExpressionEvaluator;

import java.util.Map;

public class MvelExpressionEvaluatorOverride implements ExpressionEvaluator {

    @Override
    public Object eval(String expression, Map<String, Object> vars, boolean needCached) {
        return MvelUtilOverride.eval(expression,vars,  needCached);
    }
}