package com.alibaba.smart.framework.engine.test.delegation;

import com.alibaba.smart.framework.engine.context.ExecutionContext;
import com.alibaba.smart.framework.engine.delegation.JavaDelegation;
import com.alibaba.smart.framework.engine.delegation.TccDelegation;
import com.alibaba.smart.framework.engine.delegation.TccResult;
import com.alibaba.smart.framework.engine.model.instance.ActivityInstance;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ExclusiveTaskDelegation implements JavaDelegation {

    private static final AtomicLong counter = new AtomicLong(100);

    public static Long getCounter() {
        return counter.get();
    }

    public static void resetCounter() {
        counter.set(100L);
    }

    @Override
    public void execute(ExecutionContext executionContext) {
        List<ActivityInstance> activityInstances = executionContext.getProcessInstance().getActivityInstances();
        counter.addAndGet(1);
    }

}
