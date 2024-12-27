package com.dulno.process.access;

import com.dulno.process.structure.step.ProcessStepDatabaseTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.dulno.workflow.WorkflowModule;
import com.dulno.core.access.DulnoRequestBody;
import com.dulno.core.bundle.BundleDatabaseTable;
import com.dulno.core.iterator.AsyncIterator;
import com.dulno.core.organization.team.Team;
import com.dulno.core.organization.team.TeamDatabaseTable;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;
import com.dulno.workflow.structure.WorkflowDatabaseTable;
import com.dulno.workflow.structure.WorkflowEntry;
import com.dulno.process.structure.Process;
import com.dulno.process.structure.ProcessDatabaseTable;
import com.dulno.process.structure.connection.ProcessConnectionDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@RestController
public final class ProcessModificationController extends ProcessController {
  private final BundleDatabaseTable bundleDatabaseTable;
  private final TeamDatabaseTable teamDatabaseTable;
  private final WorkflowDatabaseTable workflowDatabaseTable;
  private final WorkflowModule workflowModule;

  private ProcessModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProcessDatabaseTable processDatabaseTable,
    ProcessStepDatabaseTable processStepDatabaseTable,
    ProcessConnectionDatabaseTable processConnectionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable,
    BundleDatabaseTable bundleDatabaseTable, TeamDatabaseTable teamDatabaseTable,
    WorkflowDatabaseTable workflowDatabaseTable, WorkflowModule workflowModule
  ) {
    super(secretKey, userDatabaseTable, processDatabaseTable,
      processStepDatabaseTable, processConnectionDatabaseTable,
      userTargetDatabaseTable, teamTargetDatabaseTable);
    this.bundleDatabaseTable = bundleDatabaseTable;
    this.teamDatabaseTable = teamDatabaseTable;
    this.workflowDatabaseTable = workflowDatabaseTable;
    this.workflowModule = workflowModule;
  }

  @RequestMapping(path = "/process/add/", method = RequestMethod.POST)
  public CompletableFuture<Void> addProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var created = System.currentTimeMillis();
    var name = body.getString("name", 64);
    var description = body.getString("description", 128);
    var steps = body.getObjectList("steps");
    var connections = body.getObjectList("connections");
    return findUser(request).thenCompose(user ->
      checkProcessIntegrity(user, name, description, steps, connections)
        .thenCompose(success -> success ?
          userTargetDatabaseTable().findTargetSecured(user.id()).thenCompose(target ->
            findProcessOwner(user, target).thenCompose(owner ->
              checkProcessNumberLimit(user, target).thenCompose(limitReached ->
                addProcess(user, owner, steps, connections, created,
                  name, description, limitReached, response)))) : null));
  }

  private CompletableFuture<UUID> findProcessOwner(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable().findTargetSecured(user.id())
        .thenApply(team -> team.orElse(target));
  }

  private CompletableFuture<Boolean> checkProcessNumberLimit(User user, UUID target) {
    return findOwnersOfTarget(user, target)
      .thenCompose(owners -> AsyncIterator.execute(owners, owner ->
          processDatabaseTable().findProcessCount(owner))
        .thenApply(sizes -> sizes.stream().mapToLong(Long::longValue).sum())
        .thenCompose(number -> bundleDatabaseTable.findBundle(target)
          .thenApply(bundle ->  bundle.webhookNumberLimit() > 0 &&
            number >= bundle.webhookNumberLimit())));
  }

  private CompletableFuture<List<UUID>> findOwnersOfTarget(User user, UUID target) {
    return user.id().equals(target) ?
      CompletableFuture.completedFuture(Lists.newArrayList(target)) :
      teamDatabaseTable.findTeamsByOrganization(target).thenApply(teams ->
        Stream.concat(teams.stream().map(Team::id).toList().stream(),
          Stream.of(target)).toList());
  }

  private CompletableFuture<Void> addProcess(
    User creator, UUID ownerId, List<DulnoRequestBody> stepData,
    List<DulnoRequestBody> connectionData, long created, String name,
    String description, boolean limitReached, HttpServletResponse response
  ) {
    if (limitReached) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return CompletableFuture.completedFuture(null);
    }
    return createProcess(creator, ownerId, stepData, connectionData, created,
      name, description);
  }

  @RequestMapping(path = "/process/update/", method = RequestMethod.POST)
  public CompletableFuture<Void> updateProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var processId = body.getUUID("process");
    var name = body.getString("name", 64);
    var description = body.getString("description", 128);
    var steps = body.getObjectList("steps");
    var connections = body.getObjectList("connections");
    return findUser(request).thenCompose(user ->
      checkProcessIntegrity(user, name, description, steps, connections)
        .thenCompose(success -> success ? processDatabaseTable().findProcess(processId)
          .thenCompose(process -> checkProcessAuthorization(user, process)
            .thenCompose(authorized -> updateProcess(process, authorized, steps,
              connections, name, description))) : null));
  }

  private CompletableFuture<Boolean> checkProcessIntegrity(
    User user, String name, String description, List<DulnoRequestBody> stepData,
    List<DulnoRequestBody> connectionData
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
    List<DulnoRequestBody> stepData, List<DulnoRequestBody> connectionData
  ) {
    var steps = findStartSteps(stepData);
    while (!steps.isEmpty()) {
      var newSteps = Lists.<DulnoRequestBody>newArrayList();
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

  private List<DulnoRequestBody> findStartSteps(
    List<DulnoRequestBody> stepData
  ) {
    var startSteps = Lists.<DulnoRequestBody>newArrayList();
    for (var step : stepData) {
      if (step.getString("type").equalsIgnoreCase("START")) {
        startSteps.add(step);
      }
    }
    return startSteps;
  }

  private List<DulnoRequestBody> findStepConnections(
    DulnoRequestBody step, List<DulnoRequestBody> stepData,
    List<DulnoRequestBody> connectionData
  ) {
    var stepIndex = stepData.indexOf(step);
    var connections = Lists.<DulnoRequestBody>newArrayList();
    for (var connection : connectionData) {
      if (connection.getInt("originStep") == stepIndex) {
        connections.add(connection);
      }
    }
    return connections;
  }

  private CompletableFuture<Boolean> checkStepWorkflow(
    User user, DulnoRequestBody step
  ) {
    if (!step.has("workflow")) {
      return CompletableFuture.completedFuture(true);
    }
    return workflowDatabaseTable.findWorkflow(step.getUUID("workflow"))
      .thenCompose(workflow -> checkWorkflowAuthorization(user, workflow.ownerId()));
  }

  private CompletableFuture<Void> updateProcess(
    Process process, boolean authorized, List<DulnoRequestBody> stepData,
    List<DulnoRequestBody> connectionData, String name, String description
  ) {
    if (!authorized) {
      return CompletableFuture.completedFuture(null);
    }
    return deleteProcess(process).thenCompose(value ->
      userDatabaseTable().findUser(process.creatorId()).thenCompose(creator ->
        processDatabaseTable().generateAvailableProcessId().thenCompose(processId ->
          createProcess(processId, creator, process.ownerId(), stepData,
            connectionData, process.created(), name, description))));
  }

  private CompletableFuture<Void> createProcess(
    User creator, UUID ownerId, List<DulnoRequestBody> stepData,
    List<DulnoRequestBody> connectionData, long created, String name,
    String description
  ) {
    return processDatabaseTable().generateAvailableProcessId()
      .thenCompose(processId -> createProcess(processId, creator, ownerId,
        stepData, connectionData, created, name, description));
  }

  private CompletableFuture<Void> createProcess(
    UUID processId, User creator, UUID ownerId,
    List<DulnoRequestBody> stepData, List<DulnoRequestBody> connectionData,
    long created, String name, String description
  ) {
    return generateStepIds(stepData.size()).thenCompose(stepIds ->
      generateConnectionIds(connectionData.size()).thenCompose(connectionIds ->
        createProcess(processId, creator.id(), ownerId, stepIds, stepData,
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

  private CompletableFuture<Void> createProcess(
    UUID processId, UUID creatorId, UUID ownerId, List<UUID> stepIds,
    List<DulnoRequestBody> stepData, List<UUID> connectionIds,
    List<DulnoRequestBody> connectionData, long created, String name,
    String description
  ) {
    for (int i = 0; i < stepData.size(); i++) {
      createStep(stepIds.get(i), processId, stepData.get(i));
    }
    for (int i = 0; i < connectionData.size(); i++) {
      createConnection(connectionIds.get(i), processId, connectionData.get(i),
        stepIds);
    }
    return processDatabaseTable().insertProcess(ownerId, processId, creatorId,
      stepIds, stepIds.size(), connectionIds, created, name, description);
  }

  private void createStep(
    UUID stepId, UUID processId, DulnoRequestBody stepData
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
    UUID connectionId, UUID processId, DulnoRequestBody connectionData,
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
    var body = DulnoRequestBody.of(payload, response);
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
      checkWorkflowAuthorization(user, workflow.ownerId()).thenAccept(authorized ->
        executeProcessStepWorkflow(workflow, authorized)));
  }

  private void executeProcessStepWorkflow(
    WorkflowEntry workflowEntry, boolean authorized
  ) {
    if (!authorized) {
      return;
    }
    executeProcessStepWorkflow(workflowEntry);
  }

  private void executeProcessStepWorkflow(WorkflowEntry workflowEntry) {
    workflowModule.createWorkflow(workflowEntry).thenAccept(workflow ->
      workflow.trigger(Maps.newHashMap()));
  }

  private CompletableFuture<Boolean> checkWorkflowAuthorization(
    User user, UUID workflowOwnerId
  ) {
    if (workflowOwnerId.equals(user.id()) ||
      user.organizations().contains(workflowOwnerId)
    ) {
      return CompletableFuture.completedFuture(true);
    }
    return teamTargetDatabaseTable().findTargetSecured(user.id())
      .thenApply(teamTarget -> teamTarget.map(uuid ->
        uuid.equals(workflowOwnerId)).orElse(false));
  }

  @RequestMapping(path = "/process/remove/", method = RequestMethod.POST)
  public CompletableFuture<Void> removeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = DulnoRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Void>();
    performProcessOperation(findUserId(request), body.getUUID("process"),
      process -> deleteProcess(process).thenAccept(futureResponse::complete),
      () -> futureResponse.complete(null));
    return futureResponse;
  }

  public CompletableFuture<Void> deleteProcess(Process process) {
    for (var step : process.stepIds()) {
      processStepDatabaseTable().deleteProcessStep(step);
    }
    for (var connection : process.connectionIds()) {
      processConnectionDatabaseTable().deleteProcessConnection(connection);
    }
    return processDatabaseTable().deleteProcess(process.id());
  }
}
