package com.dulno.process.access;

import com.dulno.process.structure.Process;
import com.dulno.process.structure.ProcessDatabaseTable;
import com.dulno.process.structure.connection.ProcessConnectionDatabaseTable;
import com.dulno.process.structure.step.ProcessStepDatabaseTable;
import com.dulno.workflow.trigger.TriggerEntry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import com.dulno.core.access.DulnoRestController;
import com.dulno.core.organization.team.TeamTargetDatabaseTable;
import com.dulno.core.user.User;
import com.dulno.core.user.UserDatabaseTable;
import com.dulno.core.user.UserTargetDatabaseTable;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
public class ProcessController extends DulnoRestController {
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
    User user, UUID processId, boolean processExists,
    Consumer<Process> operation, Runnable failResponse
  ) {
    if (!processExists) {
      failResponse.run();
      return;
    }
    processDatabaseTable.findProcess(processId).thenAccept(process ->
      checkProcessAuthorization(user, process).thenAccept(authorized ->
        performProcessOperation(process, authorized, operation, failResponse)));
  }

  private void performProcessOperation(
    Process process, boolean authorized, Consumer<Process> operation,
    Runnable failResponse
  ) {
    if (!authorized) {
      failResponse.run();
      return;
    }
    operation.accept(process);
  }

  protected CompletableFuture<Boolean> checkProcessAuthorization(
    User user, Process process
  ) {
    return checkProcessAuthorization(user, process.ownerId());
  }

  protected CompletableFuture<Boolean> checkProcessAuthorization(
    User user, UUID processOwnerId
  ) {
    if (processOwnerId.equals(user.id()) ||
      user.organizations().contains(processOwnerId)
    ) {
      return CompletableFuture.completedFuture(true);
    }
    return teamTargetDatabaseTable.findTargetSecured(user.id())
      .thenApply(teamTarget -> teamTarget.map(uuid ->
        uuid.equals(processOwnerId)).orElse(false));
  }

  protected CompletableFuture<Boolean> checkTriggerAuthorization(
    User user, TriggerEntry trigger
  ) {
    if (!trigger.type().equals("sub-workflow-trigger")) {
      return CompletableFuture.completedFuture(false);
    }
    return checkWorkflowAuthorization(user, trigger.ownerId());
  }

  protected CompletableFuture<Boolean> checkWorkflowAuthorization(
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

  protected CompletableFuture<UUID> findProcessTarget(UUID userId) {
    return userTargetDatabaseTable.findTargetSecured(userId)
      .thenCompose(target -> findProcessTarget(userId, target));
  }

  private CompletableFuture<UUID> findProcessTarget(
    UUID userId, UUID target
  ) {
    return userId.equals(target) ? CompletableFuture.completedFuture(target) :
      teamTargetDatabaseTable.findTargetSecured(userId)
        .thenApply(team -> team.orElse(target));
  }
}
