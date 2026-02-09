//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.alibaba.smart.framework.engine.overide;

import com.alibaba.smart.framework.engine.common.util.StringUtil;
import org.mvel2.MVEL;
import org.mvel2.ast.ASTNode;
import org.mvel2.ast.BinaryOperation;
import org.mvel2.compiler.ExecutableAccessor;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MvelUtilOverride {
    private static final int defaultCacheSize = 128;
    private static final ConcurrentHashMap<String, Serializable> expCache = new ConcurrentHashMap(128);
    private static final String START_TAG = "${";
    private static final String END_TAG = "}";

    public static Object eval(String expression, Map<String, Object> vars, boolean needCached) {
        Serializable compiledExp = compileExp(expression, needCached);
        return MVEL.executeExpression(compiledExp, vars);
    }

    private static Serializable compileExp(String expression, boolean needCached) {
        String processedExp = expression.trim();
        if (processedExp.startsWith("${")) {
            processedExp = StringUtil.removeStart(processedExp, "${");
            processedExp = StringUtil.removeEnd(processedExp, "}");
        }

        Serializable compiledExp = (Serializable)expCache.get(processedExp);
        if (null == compiledExp) {
            compiledExp = MVEL.compileExpression(processedExp);
            if (needCached) {
                expCache.put(processedExp, compiledExp);
            }
        }

        return compiledExp;
    }

    public static Number getRightValueForBinaryOperationExpression(String expression) {
        Serializable serializable = compileExp(expression, true);
        ExecutableAccessor executableAccessor = (ExecutableAccessor)serializable;
        BinaryOperation binaryOperation = (BinaryOperation)executableAccessor.getNode();
        ASTNode right = binaryOperation.getRight();
        Number rightValue = (Number)right.getLiteralValue();
        return rightValue;
    }
}
