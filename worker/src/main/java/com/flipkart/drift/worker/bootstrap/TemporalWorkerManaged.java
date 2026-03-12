package com.flipkart.drift.worker.bootstrap;


import com.flipkart.drift.worker.activities.*;
import com.flipkart.drift.worker.config.DriftWorkerConfiguration;
import com.flipkart.drift.worker.temporal.OptionsStore;
import com.flipkart.drift.worker.workflows.GenericWorkflowImpl;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.uber.m3.tally.Scope;
import io.dropwizard.lifecycle.Managed;
import io.temporal.client.WorkflowClient;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class TemporalWorkerManaged implements Managed {

    private final WorkerFactory workerFactory;
    private final long terminationTimeoutInSec;

    public TemporalWorkerManaged(Injector injector, DriftWorkerConfiguration configuration, Scope metricsScope) {
        this.workerFactory = createWorkerFactory(injector, configuration, metricsScope);
        this.terminationTimeoutInSec = configuration.getAwaitTerminationTimeoutInSec();
    }

    @Override
    public void start() {
        log.info("Starting Temporal Worker");
        workerFactory.start();
        log.info("Started Temporal Worker !!!!");
    }

    @Override
    public void stop() throws Exception {
        log.info("Shutting down Temporal Worker gracefully");
        workerFactory.shutdown();
        workerFactory.awaitTermination(terminationTimeoutInSec, TimeUnit.SECONDS);
        log.info("Temporal Worker shutdown complete");
    }
    
    private WorkerFactory createWorkerFactory(Injector injector, DriftWorkerConfiguration driftWorkerConfiguration, Scope metricsScope) {
        // Create a stub that accesses a Temporal Service
        WorkflowServiceStubsOptions stubsOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(driftWorkerConfiguration.getTemporalFrontEnd())
                .setMetricsScope(metricsScope)
                .build();
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newServiceStubs(stubsOptions);
        WorkflowClient client = WorkflowClient.newInstance(serviceStub);
        WorkerFactoryOptions workerFactoryOptions = WorkerFactoryOptions.newBuilder()
                .setWorkflowCacheSize(driftWorkerConfiguration.getWorkerDynamicOptions().getWorkflowCacheSize())
                .setMaxWorkflowThreadCount(driftWorkerConfiguration.getWorkerDynamicOptions().getMaxWorkflowThreadCount())
                .build();
        WorkerFactory factory = WorkerFactory.newInstance(client, workerFactoryOptions);
        WorkerOptions workerOptions = WorkerOptions.newBuilder()
                .setMaxConcurrentWorkflowTaskPollers(driftWorkerConfiguration.getWorkerDynamicOptions().getWorkflowTaskPoller())  // Number of workflow pollers
                .setMaxConcurrentActivityTaskPollers(driftWorkerConfiguration.getWorkerDynamicOptions().getActivityTaskPoller()) // Number of activity pollers
                .build();

        Worker worker = factory.newWorker(driftWorkerConfiguration.getTemporalTaskQueue(), workerOptions);
        worker.registerWorkflowImplementationTypes(OptionsStore.workflowImplementationOptions, GenericWorkflowImpl.class);
        worker.registerActivitiesImplementations(injector.getInstance(HttpNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(GroovyNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(BranchNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(ReturnControlActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(SuccessNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(FailureNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(InstructionNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(ProcessorNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(WorkflowContextManagerActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(FetchNodeDefinitionActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(FetchWorkflowActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(WaitNodeNodeActivityImpl.class));
        worker.registerActivitiesImplementations(injector.getInstance(ChildNodeActivityImpl.class));

        return factory;
    }
}
