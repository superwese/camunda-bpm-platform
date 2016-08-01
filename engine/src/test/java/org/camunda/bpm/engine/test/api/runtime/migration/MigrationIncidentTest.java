/*
 * Copyright 2016 camunda services GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.runtime.migration;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.api.runtime.FailingDelegate;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class MigrationIncidentTest {


  public static class NewDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
    }
  }

  public static final String FAIL_CALLED_PROC_KEY = "calledProc";
  public static final BpmnModelInstance FAIL_CALLED_PROC  = Bpmn.createExecutableProcess(FAIL_CALLED_PROC_KEY)
    .startEvent("start")
    .serviceTask("task")
      .camundaAsyncBefore()
      .camundaClass(FailingDelegate.class.getName())
    .endEvent("end")
    .done();

  public static final String FAIL_CALL_PROC_KEY = "oneFailingServiceTaskProcess";
  public static final BpmnModelInstance FAIL_CALL_ACT_JOB_PROC  = Bpmn.createExecutableProcess(FAIL_CALL_PROC_KEY)
    .startEvent("start")
      .callActivity("calling")
      .calledElement(FAIL_CALLED_PROC_KEY)
    .endEvent("end")
    .done();



  public static final String NEW_CALLED_PROC_KEY = "newCalledProc";
  public static final BpmnModelInstance NEW_CALLED_PROC = Bpmn.createExecutableProcess(NEW_CALLED_PROC_KEY)
    .startEvent("start")
    .serviceTask("taskV2")
      .camundaAsyncBefore()
      .camundaClass(NewDelegate.class.getName())
    .endEvent("end")
    .done();

  public static final String NEW_CALL_PROC_KEY = "newServiceTaskProcess";
  public static final BpmnModelInstance NEW_CALL_ACT_PROC = Bpmn.createExecutableProcess(NEW_CALL_PROC_KEY)
    .startEvent("start")
      .callActivity("callingV2")
      .calledElement(NEW_CALLED_PROC_KEY)
    .endEvent("end")
    .done();

  
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  public ProcessEngineTestRule testHelper = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain chain = RuleChain.outerRule(engineRule).around(testHelper);

  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/api/runtime/migration/calledProcess.bpmn",
                           "org/camunda/bpm/engine/test/api/runtime/migration/callingProcess.bpmn",
                           "org/camunda/bpm/engine/test/api/runtime/migration/callingProcess_v2.bpmn"})
  public void testCallActivityExternalTaskIncidentMigration() throws Exception {
    // Given we create a new process instance
    ProcessDefinition callingProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey("callingProcess")
        .singleResult();
    ProcessInstance processInstance = engineRule.getRuntimeService().startProcessInstanceById(callingProcess.getId());

    LockedExternalTask task = engineRule.getExternalTaskService().fetchAndLock(1, "foo")
      .topic("foo", 1000L)
      .execute()
      .get(0);
    // creating an incident in the called and calling process
    engineRule.getExternalTaskService().handleFailure(task.getId(), "foo", "error", 0, 1000L);

    Incident incidentInCallingProcess = engineRule.getRuntimeService().createIncidentQuery().processDefinitionId(callingProcess.getId()).singleResult();

    // when
    ProcessDefinition callingProcessV2 = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey("callingProcessV2")
        .singleResult();

    MigrationPlan migrationPlan = engineRule.getRuntimeService()
        .createMigrationPlan(callingProcess.getId(), callingProcessV2.getId())
        .mapEqualActivities()
            .mapActivities("CallActivity", "CallActivityV2")
        .build();

    engineRule.getRuntimeService()
            .newMigration(migrationPlan)
            .processInstanceIds(processInstance.getId())
            .execute();

    // then
    Incident incidentAfterMigration = engineRule.getRuntimeService().createIncidentQuery().incidentId(incidentInCallingProcess.getId()).singleResult();
    Assert.assertEquals(callingProcessV2.getId(), incidentAfterMigration.getProcessDefinitionId());
    Assert.assertEquals("CallActivityV2", incidentAfterMigration.getActivityId());

  }


  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/api/runtime/migration/calledProcess.bpmn",
                           "org/camunda/bpm/engine/test/api/runtime/migration/calledProcess_v2.bpmn"})
  public void testExternalTaskIncidentMigration() throws Exception {

    // Given we create a new process instance
    ProcessDefinition callingProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey("calledProcess")
        .singleResult();
    ProcessInstance processInstance = engineRule.getRuntimeService().startProcessInstanceById(callingProcess.getId());

    LockedExternalTask task = engineRule.getExternalTaskService().fetchAndLock(1, "foo")
      .topic("foo", 1000L)
      .execute()
      .get(0);
    // creating an incident in the called and calling process
    engineRule.getExternalTaskService().handleFailure(task.getId(), "foo", "error", 0, 1000L);

    Incident incidentInCallingProcess = engineRule.getRuntimeService().createIncidentQuery().processDefinitionId(callingProcess.getId()).singleResult();

    // when
    ProcessDefinition callingProcessV2 = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey("calledProcessV2")
        .singleResult();

    MigrationPlan migrationPlan = engineRule.getRuntimeService()
        .createMigrationPlan(callingProcess.getId(), callingProcessV2.getId())
        .mapEqualActivities()
            .mapActivities("ServiceTask_1p58ywb", "ServiceTask_V2")
        .build();

    engineRule.getRuntimeService()
            .newMigration(migrationPlan)
            .processInstanceIds(processInstance.getId())
            .execute();

    // then
    Incident incidentAfterMigration = engineRule.getRuntimeService().createIncidentQuery().incidentId(incidentInCallingProcess.getId()).singleResult();
    Assert.assertEquals(callingProcessV2.getId(), incidentAfterMigration.getProcessDefinitionId());
    Assert.assertEquals("ServiceTask_V2", incidentAfterMigration.getActivityId());
  }



  @Test
  public void testCallActivityJobIncidentMigration() {
    // Given we deploy process definitions
    testHelper.deploy(FAIL_CALLED_PROC, FAIL_CALL_ACT_JOB_PROC, NEW_CALLED_PROC, NEW_CALL_ACT_PROC);

    ProcessDefinition failingProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(FAIL_CALL_PROC_KEY)
        .singleResult();

    ProcessDefinition newProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(NEW_CALL_PROC_KEY)
        .singleResult();

    //create process instance and execute job which fails
    ProcessInstance processInstance = engineRule.getRuntimeService().startProcessInstanceByKey(FAIL_CALL_PROC_KEY);
    testHelper.executeAvailableJobs();

    Incident incidentInCallingProcess = engineRule.getRuntimeService()
            .createIncidentQuery()
            .processDefinitionId(failingProcess.getId())
            .singleResult();

    // when
    MigrationPlan migrationPlan = engineRule.getRuntimeService()
        .createMigrationPlan(failingProcess.getId(), newProcess.getId())
        .mapEqualActivities()
            .mapActivities("calling", "callingV2")
        .build();

    engineRule.getRuntimeService()
            .newMigration(migrationPlan)
            .processInstanceIds(processInstance.getId())
            .execute();

    // then
    Incident incidentAfterMigration = engineRule.getRuntimeService()
            .createIncidentQuery()
            .incidentId(incidentInCallingProcess.getId())
            .singleResult();
    Assert.assertEquals(newProcess.getId(), incidentAfterMigration.getProcessDefinitionId());
    Assert.assertEquals("callingV2", incidentAfterMigration.getActivityId());
  }



  @Test
  public void testJobIncidentMigration() {
    // Given we deploy process definitions
    testHelper.deploy(FAIL_CALLED_PROC, NEW_CALLED_PROC);

    ProcessDefinition failingProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(FAIL_CALLED_PROC_KEY)
        .singleResult();

    ProcessDefinition newProcess = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(NEW_CALLED_PROC_KEY)
        .singleResult();

    //create process instance and execute job which fails
    ProcessInstance processInstance = engineRule.getRuntimeService().startProcessInstanceByKey(FAIL_CALLED_PROC_KEY);
    testHelper.executeAvailableJobs();

    Incident incidentInCallingProcess = engineRule.getRuntimeService()
            .createIncidentQuery()
            .processDefinitionId(failingProcess.getId())
            .singleResult();

    // when
    MigrationPlan migrationPlan = engineRule.getRuntimeService()
        .createMigrationPlan(failingProcess.getId(), newProcess.getId())
        .mapEqualActivities()
            .mapActivities("task", "taskV2")
        .build();

    engineRule.getRuntimeService()
            .newMigration(migrationPlan)
            .processInstanceIds(processInstance.getId())
            .execute();

    // then
    Incident incidentAfterMigration = engineRule.getRuntimeService()
            .createIncidentQuery()
            .incidentId(incidentInCallingProcess.getId())
            .singleResult();
    Assert.assertEquals(newProcess.getId(), incidentAfterMigration.getProcessDefinitionId());
    Assert.assertEquals("taskV2", incidentAfterMigration.getActivityId());

  }
}
