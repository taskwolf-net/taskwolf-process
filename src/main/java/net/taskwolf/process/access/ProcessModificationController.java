package net.taskwolf.process.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.CoreModule;
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
import net.taskwolf.process.structure.connection.ProcessConnectionDatabaseTable;
import net.taskwolf.process.structure.step.ProcessStepDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProcessModificationController extends ProcessController {
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final CoreModule coreModule;

  private ProcessModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProcessDatabaseTable processDatabaseTable,
    ProcessStepDatabaseTable processStepDatabaseTable,
    ProcessConnectionDatabaseTable processConnectionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, CoreModule coreModule
  ) {
    super(secretKey, userDatabaseTable, processDatabaseTable,
      processStepDatabaseTable, processConnectionDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.coreModule = coreModule;
  }

  @RequestMapping(path = "/process/add/", method = RequestMethod.POST)
  public void addProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var created = System.currentTimeMillis();
    var name = body.getString("name");
    var description = body.getString("description");
    var steps = body.getObjectList("steps");
    var connections = body.getObjectList("connections");
    findUser(request).thenAccept(user ->
      checkProcessIntegrity(user, name, description, steps, connections)
        .thenApply(success -> success ?
          userTargetDatabaseTable().findTargetSecured(user.id()).thenAccept(target ->
            findTeam(user, target).thenAccept(team ->
              createProcess(user, target, team, steps, connections, created,
                name, description))) : null));
  }

  private CompletableFuture<UUID> findTeam(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(null) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(null));
  }

  @RequestMapping(path = "/process/update/", method = RequestMethod.POST)
  public void updateProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var processId = body.getUUID("process");
    var name = body.getString("name");
    var description = body.getString("description");
    var steps = body.getObjectList("steps");
    var connections = body.getObjectList("connections");
    findUser(request).thenAccept(user ->
      checkProcessIntegrity(user, name, description, steps, connections)
        .thenApply(success -> success ? processDatabaseTable().findProcess(processId)
          .thenAccept(process -> updateProcess(user, process, steps, connections,
            name, description)) : null));
  }

  private CompletableFuture<Boolean> checkProcessIntegrity(
    User user, String name, String description, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData
  ) {
    if (name.equals("") || description.equals("")) {
      return CompletableFuture.completedFuture(false);
    }
    if (!checkProcessCompleteness(stepData, connectionData)) {
      return CompletableFuture.completedFuture(false);
    }
    return AsyncIterator.execute(stepData, entry -> checkStepWorkflow(user, entry))
      .thenApply(results -> results.stream().allMatch(result -> result));
  }

  private boolean checkProcessCompleteness(
    List<TaskwolfRequestBody> stepData, List<TaskwolfRequestBody> connectionData
  ) {
    var steps = findStartSteps(stepData);
    while (!steps.isEmpty()) {
      var newSteps = Lists.<TaskwolfRequestBody>newArrayList();
      for (var step : steps) {
        var connections = findStepConnections(step, stepData, connectionData);
        for (var connection : connections) {
          var destination = stepData.get(connection.getInt("destinationStep"));
          if (destination.getString("type").equalsIgnoreCase("END")) {
            return true;
          }
          newSteps.add(destination);
        }
      }
      steps = newSteps;
    }
    return false;
  }

  private List<TaskwolfRequestBody> findStartSteps(
    List<TaskwolfRequestBody> stepData
  ) {
    var startSteps = Lists.<TaskwolfRequestBody>newArrayList();
    for (var step : stepData) {
      if (step.getString("type").equalsIgnoreCase("START")) {
        startSteps.add(step);
      }
    }
    return startSteps;
  }

  private List<TaskwolfRequestBody> findStepConnections(
    TaskwolfRequestBody step, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData
  ) {
    var stepIndex = stepData.indexOf(step);
    var connections = Lists.<TaskwolfRequestBody>newArrayList();
    for (var connection : connectionData) {
      if (connection.getInt("originStep") == stepIndex) {
        connections.add(connection);
      }
    }
    return connections;
  }

  private CompletableFuture<Boolean> checkStepWorkflow(
    User user, TaskwolfRequestBody step
  ) {
    if (!step.has("workflow")) {
      return CompletableFuture.completedFuture(true);
    }
    return workflowDatabaseTable.findWorkflow(step.getUUID("workflow"))
      .thenApply(workflow -> checkWorkflowAuthorization(user, workflow.ownerId()));
  }

  private void updateProcess(
    User user, Process process, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData, String name,
    String description
  ) {
    if (!checkProcessAuthorization(user, process)) {
      return;
    }
    deleteProcess(process);
    userDatabaseTable().findUser(process.creatorId()).thenAccept(creator ->
      processDatabaseTable().generateAvailableProcessId().thenAccept(processId ->
        createProcess(processId, creator, process.ownerId(), process.teamId(),
          stepData, connectionData, process.created(), name, description)));
  }

  private void createProcess(
    User creator, UUID ownerId, UUID teamId, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData, long created, String name,
    String description
  ) {
    if (!checkProcessAuthorization(creator, ownerId)) {
      return;
    }
    processDatabaseTable().generateAvailableProcessId().thenAccept(processId ->
      createProcess(processId, creator, ownerId, teamId, stepData, connectionData,
        created, name, description));
  }

  private void createProcess(
    UUID processId, User creator, UUID ownerId, UUID teamId,
    List<TaskwolfRequestBody> stepData, List<TaskwolfRequestBody> connectionData,
    long created, String name, String description
  ) {
    generateStepIds(stepData.size()).thenAccept(stepIds ->
      generateConnectionIds(connectionData.size()).thenAccept(connectionIds ->
        createProcess(processId, creator.id(), ownerId, teamId, stepIds, stepData,
          connectionIds, connectionData, created, name, description)));
  }

  private CompletableFuture<List<UUID>> generateStepIds(int number) {
    return generateMultipleIds(number,
      processStepDatabaseTable()::generateAvailableProcessStepId);
  }

  private CompletableFuture<List<UUID>> generateConnectionIds(int number) {
    return generateMultipleIds(number,
      processConnectionDatabaseTable()::generateAvailableProcessConnectionId);
  }

  private CompletableFuture<List<UUID>> generateMultipleIds(
    int number, Callable<CompletableFuture<UUID>> generator
  ) {
    var futureResponse = new CompletableFuture<List<UUID>>();
    var ids = Lists.<UUID>newArrayList();
    if (number == 0) {
      futureResponse.complete(ids);
      return futureResponse;
    }
    for (int i = 0; i < number; i++) {
      try {
        generator.call().thenAccept(ids::add)
          .thenApply(value -> ids.size() == number &&
            futureResponse.complete(ids));
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    }
    return futureResponse;
  }

  private void createProcess(
    UUID processId, UUID creatorId, UUID ownerId, UUID teamId, List<UUID> stepIds,
    List<TaskwolfRequestBody> stepData, List<UUID> connectionIds,
    List<TaskwolfRequestBody> connectionData, long created, String name,
    String description
  ) {
    for (int i = 0; i < stepData.size(); i++) {
      createStep(stepIds.get(i), processId, stepData.get(i));
    }
    for (int i = 0; i < connectionData.size(); i++) {
      createConnection(connectionIds.get(i), processId, connectionData.get(i),
        stepIds);
    }
    processDatabaseTable().insertProcess(processId, creatorId, ownerId, teamId,
      stepIds, connectionIds, created, name, description);
  }

  private void createStep(
    UUID stepId, UUID processId, TaskwolfRequestBody stepData
  ) {
    var todos = stepData.getObjectList("todos").stream()
      .map(workflow -> workflow.getString("todo")).toList();
    var workflow = stepData.has("workflow") ? stepData.getUUID("workflow") : null;
    processStepDatabaseTable().insertProcessStep(stepId, processId,
      stepData.getString("name"), stepData.getString("description"),
      todos, workflow, stepData.getString("type"),
      stepData.getInt("xCoordinate"), stepData.getInt("yCoordinate"));
  }

  private void createConnection(
    UUID connectionId, UUID processId, TaskwolfRequestBody connectionData,
    List<UUID> stepIds
  ) {
    processConnectionDatabaseTable().insertProcessConnection(connectionId,
      processId, stepIds.get(connectionData.getInt("originStep")),
      stepIds.get(connectionData.getInt("destinationStep")));
  }

  @RequestMapping(path = "/process/workflow/execute/", method = RequestMethod.POST)
  private void executeProcessStepWorkflow(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var workflowId = body.getUUID("workflow");
    findUser(request).thenAccept(user ->
      workflowDatabaseTable.workflowExists(workflowId).thenAccept(exists ->
        executeProcessStepWorkflow(user, workflowId, exists)));
  }

  private void executeProcessStepWorkflow(
    User user, UUID workflowId, boolean exists
  ) {
    if (!exists) {
      return;
    }
    workflowDatabaseTable.findWorkflow(workflowId).thenAccept(workflow ->
      executeProcessStepWorkflow(user, workflow));
  }

  private void executeProcessStepWorkflow(User user, WorkflowEntry workflowEntry) {
    if (!checkWorkflowAuthorization(user, workflowEntry.ownerId())) {
      return;
    }
    executeProcessStepWorkflow(workflowEntry);
  }

  private void executeProcessStepWorkflow(WorkflowEntry workflowEntry) {
    coreModule.createWorkflow(workflowEntry).thenAccept(workflow ->
      workflow.trigger(Maps.newHashMap()));
  }

  private boolean checkWorkflowAuthorization(User user, UUID workflowOwnerId) {
    return workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId);
  }

  @RequestMapping(path = "/process/remove/", method = RequestMethod.POST)
  public void removeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    performProcessOperation(findUserId(request), body.getUUID("process"),
      this::deleteProcess, () -> {});
  }

  public void deleteProcess(Process process) {
    processDatabaseTable().deleteProcess(process.id());
    for (var step : process.stepIds()) {
      processStepDatabaseTable().deleteProcessStep(step);
    }
    for (var connection : process.connectionIds()) {
      processConnectionDatabaseTable().deleteProcessConnection(connection);
    }
  }
}
