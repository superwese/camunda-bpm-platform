/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.test.dmn.businessruletask;

import static org.junit.Assert.assertEquals;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.exception.dmn.DecisionDefinitionNotFoundException;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class DmnBusinessRuleTaskTest {

  public static final String DECISION_PROCESS = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionRef.bpmn20.xml";
  public static final String DECISION_PROCESS_EXPRESSION = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionRefExpression.bpmn20.xml";
  public static final String DECISION_PROCESS_LATEST = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionRefLatestBinding.bpmn20.xml";
  public static final String DECISION_PROCESS_DEPLOYMENT = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionRefDeploymentBinding.bpmn20.xml";
  public static final String DECISION_PROCESS_VERSION = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionRefVersionBinding.bpmn20.xml";
  public static final String DECISION_OKAY_DMN = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionOkay.dmn11.xml";
  public static final String DECISION_NOT_OKAY_DMN = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testDecisionNotOkay.dmn11.xml";
  public static final String DECISION_POJO_DMN = "org/camunda/bpm/engine/test/dmn/businessruletask/DmnBusinessRuleTaskTest.testPojo.dmn11.xml";

  public static final String DECISION_LITERAL_EXPRESSION_DMN = "org/camunda/bpm/engine/test/dmn/deployment/DecisionWithLiteralExpression.dmn";
  public static final String DRD_DISH_RESOURCE = "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml";

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  protected RuntimeService runtimeService;

  @Before
  public void init() {
    runtimeService = engineRule.getRuntimeService();
  }

  @Deployment(resources = { DECISION_PROCESS, DECISION_PROCESS_EXPRESSION, DECISION_OKAY_DMN })
  @Test
  public void decisionRef() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");
    assertEquals("okay", getDecisionResult(processInstance));

    processInstance = startExpressionProcess("testDecision", 1);
    assertEquals("okay", getDecisionResult(processInstance));
  }

  @Deployment(resources = DECISION_PROCESS)
  @Test
  public void noDecisionFound() {
    thrown.expect(DecisionDefinitionNotFoundException.class);
    thrown.expectMessage("no decision definition deployed with key 'testDecision'");

    runtimeService.startProcessInstanceByKey("testProcess");
  }

  @Deployment(resources = DECISION_PROCESS_EXPRESSION)
  @Test
  public void noDecisionFoundRefByExpression() {
    thrown.expect(DecisionDefinitionNotFoundException.class);
    thrown.expectMessage("no decision definition deployed with key = 'testDecision', version = '1' and tenant-id 'null");

   startExpressionProcess("testDecision", 1);
  }

  @Deployment(resources = { DECISION_PROCESS_LATEST, DECISION_OKAY_DMN })
  @Test
  public void decisionRefLatestBinding() {
    testRule.deploy(DECISION_NOT_OKAY_DMN);

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");
    assertEquals("not okay", getDecisionResult(processInstance));
  }

  @Deployment(resources = { DECISION_PROCESS_DEPLOYMENT, DECISION_OKAY_DMN })
  @Test
  public void decisionRefDeploymentBinding() {
    testRule.deploy(DECISION_NOT_OKAY_DMN);

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");
    assertEquals("okay", getDecisionResult(processInstance));
  }

  @Deployment(resources = { DECISION_PROCESS_VERSION, DECISION_PROCESS_EXPRESSION, DECISION_OKAY_DMN })
  @Test
  public void decisionRefVersionBinding() {
    testRule.deploy(DECISION_NOT_OKAY_DMN);
    testRule.deploy(DECISION_OKAY_DMN);

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");
    assertEquals("not okay", getDecisionResult(processInstance));

    processInstance = startExpressionProcess("testDecision", 2);
    assertEquals("not okay", getDecisionResult(processInstance));
  }

  @Deployment(resources = {DECISION_PROCESS, DECISION_POJO_DMN})
  @Test
  public void testPojo() {
    VariableMap variables = Variables.createVariables()
      .putValue("pojo", new TestPojo("okay", 13.37));
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess", variables);

    assertEquals("okay", getDecisionResult(processInstance));
  }

  @Deployment( resources = DECISION_LITERAL_EXPRESSION_DMN )
  @Test
  public void evaluateDecisionWithLiteralExpression() {
    testRule.deploy(Bpmn.createExecutableProcess("process")
        .startEvent()
        .businessRuleTask()
          .camundaDecisionRef("decisionLiteralExpression")
          .camundaResultVariable("result")
          .camundaMapDecisionResult("singleEntry")
        .endEvent()
          .camundaAsyncBefore()
        .done());

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("process", Variables.createVariables()
        .putValue("a", 2)
        .putValue("b", 3));

    assertEquals(5, getDecisionResult(processInstance));
  }

  @Deployment( resources = DRD_DISH_RESOURCE )
  @Test
  public void evaluateDecisionWithRequiredDecisions() {
    testRule.deploy(Bpmn.createExecutableProcess("process")
        .startEvent()
        .businessRuleTask()
          .camundaDecisionRef("dish-decision")
          .camundaResultVariable("result")
          .camundaMapDecisionResult("singleEntry")
        .endEvent()
          .camundaAsyncBefore()
        .done());

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("process", Variables.createVariables()
        .putValue("temperature", 32)
        .putValue("dayType", "Weekend"));

    assertEquals("Light salad", getDecisionResult(processInstance));
  }

  protected ProcessInstance startExpressionProcess(Object decisionKey, Object version) {
    VariableMap variables = Variables.createVariables()
        .putValue("decision", decisionKey)
        .putValue("version", version);
    return runtimeService.startProcessInstanceByKey("testProcessExpression", variables);
  }

  protected Object getDecisionResult(ProcessInstance processInstance) {
    // the single entry of the single result of the decision result is stored as process variable
    return runtimeService.getVariable(processInstance.getId(), "result");
  }

}
