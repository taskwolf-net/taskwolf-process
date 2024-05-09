package net.taskwolf.process.access;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
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
public final class ProcessModificationController extends TaskwolfRestController {
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessStepDatabaseTable processStepDatabaseTable;
  private final ProcessConnectionDatabaseTable processConnectionDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;

  private ProcessModificationController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProcessDatabaseTable processDatabaseTable,
    ProcessStepDatabaseTable processStepDatabaseTable,
    ProcessConnectionDatabaseTable processConnectionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.processDatabaseTable = processDatabaseTable;
    this.processStepDatabaseTable = processStepDatabaseTable;
    this.processConnectionDatabaseTable = processConnectionDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
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
    if (!checkProcessIntegrity(name, description, steps, connections)) {
      return;
    }
    findUser(request).thenAccept(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        createProcess(user, target, steps, connections, created, name,
          description)));
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
    if (!checkProcessIntegrity(name, description, steps, connections)) {
      return;
    }
    findUser(request).thenAccept(user -> processDatabaseTable.findProcess(processId)
      .thenAccept(process -> updateProcess(user, process, steps, connections,
        name, description)));
  }

  private boolean checkProcessIntegrity(
    String name, String description, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData
  ) {
    if (name.equals("") || description.equals("")) {
      return false;
    }
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

  private void updateProcess(
    User user, Process process, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData, String name,
    String description
  ) {
    if (!checkProcessAuthorization(user, process)) {
      return;
    }
    deleteProcess(user, process);
    userDatabaseTable().findUser(process.creatorId()).thenAccept(creator ->
      processDatabaseTable.generateAvailableProcessId().thenAccept(processId ->
        createProcess(processId, creator, process.ownerId(), stepData,
          connectionData, process.created(), name, description)));
  }

  private void createProcess(
    User creator, UUID ownerId, List<TaskwolfRequestBody> stepData,
    List<TaskwolfRequestBody> connectionData, long created, String name,
    String description
  ) {
    if (!checkProcessAuthorization(creator, ownerId)) {
      return;
    }
    processDatabaseTable.generateAvailableProcessId().thenAccept(processId ->
      createProcess(processId, creator, ownerId, stepData, connectionData,
        created, name, description));
  }

  private void createProcess(
    UUID processId, User creator, UUID ownerId,
    List<TaskwolfRequestBody> stepData, List<TaskwolfRequestBody> connectionData,
    long created, String name, String description
  ) {
    generateStepIds(stepData.size()).thenAccept(stepIds ->
      generateConnectionIds(connectionData.size()).thenAccept(connectionIds ->
        createProcess(processId, creator.id(), ownerId, stepIds, stepData,
          connectionIds, connectionData, created, name, description)));
  }

  private CompletableFuture<List<UUID>> generateStepIds(int number) {
    return generateMultipleIds(number,
      processStepDatabaseTable::generateAvailableProcessStepId);
  }

  private CompletableFuture<List<UUID>> generateConnectionIds(int number) {
    return generateMultipleIds(number,
      processConnectionDatabaseTable::generateAvailableProcessConnectionId);
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
    UUID processId, UUID creatorId, UUID ownerId, List<UUID> stepIds,
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
    processDatabaseTable.insertProcess(processId, creatorId, ownerId,
      stepIds, connectionIds, created, name, description);
  }

  private void createStep(
    UUID stepId, UUID processId, TaskwolfRequestBody stepData
  ) {
    processStepDatabaseTable.insertProcessStep(stepId, processId,
      stepData.getString("name"), stepData.getString("description"),
      stepData.getString("type"), stepData.getInt("xCoordinate"),
      stepData.getInt("yCoordinate"));
  }

  private void createConnection(
    UUID connectionId, UUID processId, TaskwolfRequestBody connectionData,
    List<UUID> stepIds
  ) {
    processConnectionDatabaseTable.insertProcessConnection(connectionId,
      processId, stepIds.get(connectionData.getInt("originStep")),
      stepIds.get(connectionData.getInt("destinationStep")));
  }

  @RequestMapping(path = "/process/remove/", method = RequestMethod.POST)
  public void removeProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var processId = body.getUUID("process");
    findUser(request).thenAccept(user ->
      processDatabaseTable.processExists(processId).thenAccept(exists ->
        deleteProcess(user, processId, exists)));
  }

  private void deleteProcess(User user, UUID processId, boolean processExists) {
    if (!processExists) {
      return;
    }
    processDatabaseTable.findProcess(processId).thenAccept(process ->
      deleteProcess(user, process));
  }

  private void deleteProcess(User user, Process process) {
    if (!checkProcessAuthorization(user, process)) {
      return;
    }
    deleteProcess(process);
  }

  public void deleteProcess(Process process) {
    processDatabaseTable.deleteProcess(process.id());
    for (var step : process.stepIds()) {
      processStepDatabaseTable.deleteProcessStep(step);
    }
    for (var connection : process.connectionIds()) {
      processConnectionDatabaseTable.deleteProcessConnection(connection);
    }
  }

  private boolean checkProcessAuthorization(User user, Process process) {
    return checkProcessAuthorization(user, process.ownerId());
  }

  private boolean checkProcessAuthorization(User user, UUID processOwnerId) {
    return processOwnerId.equals(user.id()) ||
      user.organizations().contains(processOwnerId);
  }
}
