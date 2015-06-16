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

package org.camunda.bpm.engine.impl.jobexecutor;

import java.util.List;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.management.Metrics;

/**
 *
 * @author Daniel Meyer
 */
public class CallerRunsRejectedJobsHandler implements RejectedJobsHandler {

  public void jobsRejected(List<String> jobIds, ProcessEngineImpl processEngine) {
    logRejectionHandling(jobIds, processEngine);
    new ExecuteJobsRunnable(jobIds, processEngine).run();
  }

  protected void logRejectionHandling(List<String> jobIds, ProcessEngineImpl processEngine) {
    if (processEngine.getProcessEngineConfiguration().isMetricsEnabled()) {
      processEngine
        .getProcessEngineConfiguration()
        .getMetricsRegistry()
        .markOccurrence(Metrics.JOB_EXECUTED_BY_ACQUISITION, jobIds.size());

    }
  }

}
