package net.taskwolf.process.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.iterator.AsyncIterator;
import net.taskwolf.core.iterator.AsyncListIterator;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
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
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class ProcessInformationController extends TaskwolfRestController {
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessStepDatabaseTable processStepDatabaseTable;
  private final ProcessConnectionDatabaseTable processConnectionDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");

  private ProcessInformationController(
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

  @RequestMapping(path = "/process/find/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> findProcess(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenAccept(user ->
      processDatabaseTable.findProcess(body.getUUID("process"))
        .thenAccept(process -> findProcess(user, process)
          .thenAccept(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findProcess(
    User user, Process process
  ) {
    if (!checkProcessAuthorization(user, process)) {
      var futureResponse = new CompletableFuture<Map<String, Object>>();
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    return gatherProcessInformation(process);
  }

  @RequestMapping(path = "/processes/selected/", method = RequestMethod.GET)
  public CompletableFuture<Map<String, Object>> selectedProcesses(
    HttpServletRequest request
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    findUser(request).thenApply(user ->
      userTargetDatabaseTable.findTargetSecured(user.id()).thenAccept(target ->
        findSelectedProcesses(user, target).thenApply(futureResponse::complete)));
    return futureResponse;
  }

  private CompletableFuture<Map<String, Object>> findSelectedProcesses(
    User user, UUID ownerId
  ) {
    var futureResponse = new CompletableFuture<Map<String, Object>>();
    if (!checkProcessAuthorization(user, ownerId)) {
      futureResponse.complete(Maps.newHashMap());
      return futureResponse;
    }
    collectProcesses(Lists.newArrayList(ownerId)).thenAccept(processes ->
      collectProcessInformation(processes).thenAccept(futureResponse::complete));
    return futureResponse;
  }

  private CompletableFuture<List<Process>> collectProcesses(
    List<UUID> ownerIds
  ) {
    var futureResponse = new CompletableFuture<List<Process>>();
    AsyncListIterator.execute(ownerIds, processDatabaseTable::findProcessesOfOwner)
      .thenAccept(futureResponse::complete);
    return futureResponse;
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
    userDatabaseTable().findUserIfExists(process.creatorId())
      .thenAccept(creator -> processStepDatabaseTable.findProcessStepsByProcess(process.id())
        .thenAccept(steps -> processConnectionDatabaseTable.findProcessConnectionsByProcess(process.id())
          .thenAccept(connections -> futureResponse.complete(
            assemblyProcessInformation(process, creator, steps, connections)))));
    return futureResponse;
  }

  private Map<String, Object> assemblyProcessInformation(
    Process process, User creator, List<ProcessStep> steps,
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
    List<ProcessStep> steps
  ) {
    var information = Maps.<String, Object>newHashMap();
    var stepsInformation = Lists.<Map<String, Object>>newArrayList();
    for (var step : steps) {
      var stepInformation = Maps.<String, Object>newHashMap();
      stepInformation.put("stepId", step.id());
      stepInformation.put("stepName", step.name());
      stepInformation.put("stepDescription", step.description());
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

  private boolean checkProcessAuthorization(User user, Process process) {
    return checkProcessAuthorization(user, process.ownerId());
  }

  private boolean checkProcessAuthorization(User user, UUID processOwnerId) {
    return processOwnerId.equals(user.id()) ||
      user.organizations().contains(processOwnerId);
  }
}
