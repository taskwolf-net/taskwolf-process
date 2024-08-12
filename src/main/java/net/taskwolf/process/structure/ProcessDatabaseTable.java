package net.taskwolf.process.structure;

import com.google.common.collect.Lists;
import net.taskwolf.core.database.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ProcessDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "process";

  public static ProcessDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.PRIMARY_KEY));
    columns.add(DatabaseColumn.create("creator", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID));
    columns.add(DatabaseListColumn.create("steps", DatabaseDataType.UUID));
    columns.add(DatabaseListColumn.create("connections", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("created", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("name", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("description", DatabaseDataType.TEXT));
    return new ProcessDatabaseTable(connection, keyspace, TABLE_NAME, columns);
  }

  private ProcessDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  public void insertProcess(Process process) {
    insertProcess(process.id(), process.creatorId(), process.ownerId(),
      process.stepIds(), process.connectionIds(), process.created(),
      process.name(), process.description());
  }

  public void insertProcess(
    UUID id, UUID creatorId, UUID ownerId, List<UUID> stepIds,
    List<UUID> connectionIds, long created, String name, String description
  ) {
    insert(DatabaseRow.of(id, creatorId, ownerId, stepIds, connectionIds,
      created, name, description));
  }

  public void deleteProcess(UUID processId) {
    delete(DatabaseCell.create(processId));
  }

  public CompletableFuture<UUID> generateAvailableProcessId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    processExists(id).thenApply(exists -> exists ?
      generateAvailableProcessId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  public CompletableFuture<Boolean> processExists(UUID processId) {
    return exists(DatabaseCell.create(processId));
  }

  public CompletableFuture<Process> findProcess(UUID processId) {
    return selectRow(DatabaseCell.create(processId)).thenApply(Process::of);
  }

  public CompletableFuture<List<Process>> findProcessesOfOwner(UUID ownerId) {
    return selectRows("owner=" + ownerId).thenApply(rows ->
      rows.stream().map(Process::of).collect(Collectors.toList()));
  }
}
