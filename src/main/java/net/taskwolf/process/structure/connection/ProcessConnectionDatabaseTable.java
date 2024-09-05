package net.taskwolf.process.structure.connection;

import com.google.common.collect.Lists;
import net.taskwolf.core.database.*;
import net.taskwolf.core.database.condition.DatabaseCondition;
import net.taskwolf.process.structure.step.ProcessStep;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ProcessConnectionDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "process_connection";

  public static ProcessConnectionDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.PRIMARY_KEY));
    columns.add(DatabaseColumn.create("process", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("origin", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("destination", DatabaseDataType.UUID));
    return new ProcessConnectionDatabaseTable(connection, keyspace, TABLE_NAME, columns);
  }

  private ProcessConnectionDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  public void insertProcessConnection(ProcessConnection connection) {
    insertProcessConnection(connection.id(), connection.processId(),
      connection.originStepId(), connection.destinationStepId());
  }

  public void insertProcessConnection(
    UUID id, UUID processId, UUID originStepId, UUID destinationStepId
  ) {
    insert(DatabaseRow.of(id, processId, originStepId, destinationStepId));
  }

  public void deleteProcessConnection(UUID connectionId) {
    delete(connectionId);
  }

  public CompletableFuture<UUID> generateAvailableProcessConnectionId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    processConnectionExists(id).thenApply(exists -> exists ?
      generateAvailableProcessConnectionId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  public CompletableFuture<Boolean> processConnectionExists(UUID connectionId) {
    return exists(connectionId);
  }

  public CompletableFuture<ProcessStep> findProcessConnection(UUID connectionId) {
    return selectRow(connectionId).thenApply(ProcessStep::of);
  }

  public CompletableFuture<List<ProcessConnection>> findProcessConnectionsByProcess(
    UUID processId
  ) {
    return selectRows(DatabaseCondition.of("process", processId))
      .thenApply(rows -> rows.stream().map(ProcessConnection::of)
        .collect(Collectors.toList()));
  }
}
