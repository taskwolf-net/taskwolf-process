package net.taskwolf.process.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.core.workflow.WorkflowDatabaseTable;
import net.taskwolf.core.workflow.WorkflowEntry;
import net.taskwolf.process.structure.Process;
import net.taskwolf.process.structure.ProcessDatabaseTable;
import net.taskwolf.process.structure.connection.ProcessConnection;
import net.taskwolf.process.structure.connection.ProcessConnectionDatabaseTable;
import net.taskwolf.process.structure.step.ProcessStep;
import net.taskwolf.process.structure.step.ProcessStepDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProcessInformationController extends ProcessController {
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private ProcessInformationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProcessDatabaseTable processDatabaseTable,
    ProcessStepDatabaseTable processStepDatabaseTable,
    ProcessConnectionDatabaseTable processConnectionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable
  ) {
    super(secretKey, userDatabaseTable, processDatabaseTable,
      processStepDatabaseTable, processConnectionDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.workflowDatabaseTable = workflowDatabaseTable;
  }

  @RequestMapping(path = "/process/workflows/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> findProcessWorkflows(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return userTargetDatabaseTable().findTargetSecured(userId)
      .thenCompose(target -> findProcessWorkflowOwner(userId, target)
        .thenCompose(this::findProcessWorkflows));
  }

  private CompletableFuture<Map<String, Object>> findProcessWorkflows(UUID owner) {
    return workflowDatabaseTable.findWorkflowByModule(owner, "process")
      .thenApply(workflows -> Map.of("workflows", workflows.stream().map(
        workflow -> Map.of("id", workflow.id(), "name", workflow.name())).toList()));
  }

  private CompletableFuture<UUID> findProcessWorkflowOwner(
    UUID userId, UUID targetId
  ) {
    if (userId.equals(targetId)) {
      return CompletableFuture.completedFuture(userId);
    }
    return teamTargetDatabaseTable().findTargetSecured(userId)
      .thenApply(teamTarget -> teamTarget.orElse(targetId));
  }

  @RequestMapping(path = "/process/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user -> performProcessOperation(user,
      body.getUUID("process"), process -> gatherProcessInformation(process)
        .thenAccept(futureResponse::complete),
      () -> futureResponse.complete(Maps.newHashMap())));
    return futureResponse;
  }

  @RequestMapping(path = "/processes/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> selectedProcesses(
    HttpServletRequest request
  ) {
    return findUser(request).thenCompose(user -> findViewableProcesses(user.id())
      .thenCompose(this::collectProcessInformation));
  }

  private CompletableFuture<Map<String, Object>> collectProcessInformation(
    List<Process> processes
  ) {
    if (processes.isEmpty()) {
      return CompletableFuture.completedFuture(Map.of("processes",
        Lists.newArrayList()));
    }
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    AsyncIterator.execute(processes, this::gatherProcessInformation).thenAccept(
      information -> futureResponse.complete(Map.of("processes", information)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> gatherProcessInformation(
    Process process
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    userDatabaseTable().findUserIfExists(process.creatorId()).thenAccept(creator ->
      processStepDatabaseTable().findProcessStepsByProcess(process.id())
        .thenAccept(steps -> findStepsWorkflow(steps).thenAccept(stepWorkflows ->
          processConnectionDatabaseTable().findProcessConnectionsByProcess(process.id())
            .thenAccept(connections -> futureResponse.complete(
              assemblyProcessInformation(process, creator, stepWorkflows, connections))))));
    return futureResponse;
  }

  private CompletableFuture<Map<ProcessStep, String>> findStepsWorkflow(
    List<ProcessStep> steps
  ) {
    var result = Maps.<ProcessStep, String>newHashMap();
    return AsyncIterator.execute(steps, step -> findWorkflowName(step)
        .thenAccept(workflowName -> result.put(step, workflowName)))
      .thenApply(value -> result);
  }

  private CompletableFuture<String> findWorkflowName(ProcessStep step) {
    if (step.workflow() == null) {
      return CompletableFuture.completedFuture(null);
    }
    return workflowDatabaseTable.workflowExists(step.workflow()).thenCompose(
      exists -> exists ? workflowDatabaseTable.findWorkflow(step.workflow())
        .thenApply(WorkflowEntry::name) : CompletableFuture.completedFuture(null));
  }

  private Map<String, Object> assemblyProcessInformation(
    Process process, User creator, Map<ProcessStep, String> steps,
    List<ProcessConnection> connections
  ) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", process.id());
    information.put("name", process.name());
    information.put("description", process.description());
    information.put("created", timeMillisecondsToDate(process.created()));
    information.put("creator", creator.name());
    information.putAll(assemblyProcessStepsInformation(steps));
    information.putAll(assemblyProcessConnectionsInformation(connections));
    return information;
  }

  private Map<String, Object> assemblyProcessStepsInformation(
    Map<ProcessStep, String> steps
  ) {
    var information = Maps.<String, Object>newHashMap();
    var stepsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var entry : steps.entrySet()) {
      var step = entry.getKey();
      var stepInformation = Maps.<String, Object>newHashMap();
      stepInformation.put("stepId", step.id());
      stepInformation.put("stepName", step.name());
      stepInformation.put("stepDescription", step.description());
      stepInformation.put("stepTodos", step.todos());
      if (entry.getValue() != null) {
        stepInformation.put("stepWorkflow", Map.of("id", step.workflow(),
          "name", entry.getValue()));
      }
      stepInformation.put("stepType", step.type());
      stepInformation.put("stepXCoordinate", step.xCoordinate());
      stepInformation.put("stepYCoordinate", step.yCoordinate());
      stepsInformation.add(stepInformation);
    }
    information.put("steps", stepsInformation);
    return information;
  }

  private Map<String, Object> assemblyProcessConnectionsInformation(
    List<ProcessConnection> connections
  ) {
    var information = Maps.<String, Object>newHashMap();
    var connectionsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var connection : connections) {
      var connectionInformation = Maps.<String, Object>newHashMap();
      connectionInformation.put("connectionId", connection.id());
      connectionInformation.put("connectionOriginStep", connection.originStepId());
      connectionInformation.put("connectionDestinationStep",
        connection.destinationStepId());
      connectionsInformation.add(connectionInformation);
    }
    information.put("connections", connectionsInformation);
    return information;
  }

  private String timeMillisecondsToDate(long milliseconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliseconds);
    return simpleDateFormat.format(calendar.getTime());
  }
}
