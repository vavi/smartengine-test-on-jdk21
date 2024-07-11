package com.alibaba.smart.framework.engine.test.cases;

import com.alibaba.smart.framework.engine.SmartEngine;
import com.alibaba.smart.framework.engine.configuration.ProcessEngineConfiguration;
import com.alibaba.smart.framework.engine.configuration.impl.DefaultProcessEngineConfiguration;
import com.alibaba.smart.framework.engine.configuration.impl.DefaultSmartEngine;
import com.alibaba.smart.framework.engine.extension.scanner.SimpleAnnotationScanner;
import com.alibaba.smart.framework.engine.persister.custom.session.PersisterSession;
import com.alibaba.smart.framework.engine.service.command.ExecutionCommandService;
import com.alibaba.smart.framework.engine.service.command.ProcessCommandService;
import com.alibaba.smart.framework.engine.service.command.RepositoryCommandService;
import com.alibaba.smart.framework.engine.service.query.ExecutionQueryService;
import com.alibaba.smart.framework.engine.service.query.ProcessQueryService;
import com.alibaba.smart.framework.engine.service.query.RepositoryQueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;


public class CustomBaseTestCase {

    protected ProcessEngineConfiguration processEngineConfiguration = new DefaultProcessEngineConfiguration();

    protected SmartEngine smartEngine = new DefaultSmartEngine();

    //3. 部署流程定义
    protected RepositoryCommandService repositoryCommandService;
    //2.获得常用服务
    protected ProcessCommandService processCommandService;
    protected ProcessQueryService processQueryService;

    protected ExecutionQueryService executionQueryService;
    protected ExecutionCommandService executionCommandService;
    protected RepositoryQueryService repositoryQueryService ;

    private static   SimpleAnnotationScanner simpleAnnotationScanner ;

    @BeforeEach
    public void setUp() {
       simpleAnnotationScanner = new SimpleAnnotationScanner(SmartEngine.class.getPackage().getName());


        PersisterSession.create();


        initProcessConfiguration();

        smartEngine = new DefaultSmartEngine();
        smartEngine.init(processEngineConfiguration);

        //3. 部署流程定义
        repositoryCommandService = smartEngine
            .getRepositoryCommandService();
          repositoryQueryService = smartEngine
            .getRepositoryQueryService();

        processCommandService = smartEngine.getProcessCommandService();
        processQueryService = smartEngine.getProcessQueryService();
        executionQueryService = smartEngine.getExecutionQueryService();
        executionCommandService = smartEngine.getExecutionCommandService();


    }

    protected void initProcessConfiguration() {
        processEngineConfiguration = new DefaultProcessEngineConfiguration();
    }

    @AfterAll
    public static void clear() {

        if   ( null !=   PersisterSession.currentSession()){
            PersisterSession.destroySession();
        }
        if(null != simpleAnnotationScanner ){
            simpleAnnotationScanner.clear();
        }

    }

}