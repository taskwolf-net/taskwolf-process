package net.taskwolf.process.access;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.taskwolf.core.access.TaskwolfRestController;
import net.taskwolf.core.organization.team.TeamTargetDatabaseTable;
import net.taskwolf.core.user.User;
import net.taskwolf.core.user.UserDatabaseTable;
import net.taskwolf.core.user.UserTargetDatabaseTable;
import net.taskwolf.process.structure.Process;
import net.taskwolf.process.structure.ProcessDatabaseTable;
import net.taskwolf.process.structure.connection.ProcessConnectionDatabaseTable;
import net.taskwolf.process.structure.step.ProcessStepDatabaseTable;

import java.security.Key;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class ProcessController extends TaskwolfRestController {
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessStepDatabaseTable processStepDatabaseTable;
  private final ProcessConnectionDatabaseTable processConnectionDatabaseTable;
  private final UserTargetDatabaseTable userTargetDatabaseTable;
  private final TeamTargetDatabaseTable teamTargetDatabaseTable;

  protected ProcessController(
    Key secretKey, UserDatabaseTable userDatabaseTable,
    ProcessDatabaseTable processDatabaseTable,
    ProcessStepDatabaseTable processStepDatabaseTable,
    ProcessConnectionDatabaseTable processConnectionDatabaseTable,
    UserTargetDatabaseTable userTargetDatabaseTable,
    TeamTargetDatabaseTable teamTargetDatabaseTable
  ) {
    super(secretKey, userDatabaseTable);
    this.processDatabaseTable = processDatabaseTable;
    this.processStepDatabaseTable = processStepDatabaseTable;
    this.processConnectionDatabaseTable = processConnectionDatabaseTable;
    this.userTargetDatabaseTable = userTargetDatabaseTable;
    this.teamTargetDatabaseTable = teamTargetDatabaseTable;
  }

  protected void performProcessOperation(
    UUID userId, UUID processId, Consumer<Process> operation,
    Runnable failResponse
  ) {
    userDatabaseTable().findUser(userId).thenAccept(user ->
      performProcessOperation(user, processId, operation, failResponse));
  }

  protected void performProcessOperation(
    User user, UUID processId, Consumer<Process> operation,
    Runnable failResponse
  ) {
    processDatabaseTable.processExists(processId).thenAccept(exists ->
      performProcessOperation(user, processId, exists, operation,
        failResponse));
  }

  private void performProcessOperation(
    User user, UUID processId, boolean workflowExists,
    Consumer<Process> operation, Runnable failResponse
  ) {
    if (!workflowExists) {
      failResponse.run();
      return;
    }
    processDatabaseTable.findProcess(processId).thenAccept(workflow ->
      performProcessOperation(user, workflow, operation, failResponse));
  }

  private void performProcessOperation(
    User user, Process process, Consumer<Process> operation,
    Runnable failResponse
  ) {
    if (!checkProcessAuthorization(user, process)) {
      failResponse.run();
      return;
    }
    operation.accept(process);
  }

  protected boolean checkProcessAuthorization(User user, Process process) {
    return checkProcessAuthorization(user, process.ownerId());
  }

  protected boolean checkProcessAuthorization(User user, UUID processOwnerId) {
    return processOwnerId.equals(user.id()) ||
      user.organizations().contains(processOwnerId);
  }

  protected CompletableFuture<List<Process>> findViewableProcesses(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findViewableProcesses(userId, target));
  }

  protected CompletableFuture<List<Process>> findViewableProcesses(
    UUID userId, UUID target
  ) {
    return userId.equals(target) ?
      processDatabaseTable.findProcessesOfOwner(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenCompose(team -> team.isEmpty() ?
          processDatabaseTable.findGlobalOrganizationProcesses(target) :
          processDatabaseTable.findOrganizationTeamProcesses(target, team.get()));
  }
}
