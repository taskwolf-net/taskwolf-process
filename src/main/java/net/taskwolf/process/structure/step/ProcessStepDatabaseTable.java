package net.taskwolf.process.structure.step;

import com.google.common.collect.Lists;
import net.taskwolf.core.database.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ProcessStepDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "process_step";

  public static ProcessStepDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.PRIMARY_KEY));
    columns.add(DatabaseColumn.create("process", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("name", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("description", DatabaseDataType.TEXT));
    columns.add(DatabaseListColumn.create("workflows", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("type", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("xCoordinate", DatabaseDataType.INT));
    columns.add(DatabaseColumn.create("yCoordinate", DatabaseDataType.INT));
    return new ProcessStepDatabaseTable(connection, keyspace, TABLE_NAME, columns);
  }

  private ProcessStepDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  public void insertProcessStep(ProcessStep step) {
    insertProcessStep(step.id(), step.processId(), step.name(),
      step.description(), step.workflows(), step.type().toString(),
      step.xCoordinate(), step.yCoordinate());
  }

  public void insertProcessStep(
    UUID id, UUID processId, String name, String description,
    List<UUID> workflows, String type, int xCoordinate, int yCoordinate
  ) {
    insert(DatabaseRow.of(id, processId, name, description, workflows, type,
      xCoordinate, yCoordinate));
  }

  public void deleteProcessStep(UUID stepId) {
    delete(DatabaseCell.create(stepId));
  }

  public CompletableFuture<UUID> generateAvailableProcessStepId() {
    var futureResponse = new CompletableFuture<UUID>();
    var id = UUID.randomUUID();
    processStepExists(id).thenApply(exists -> exists ?
      generateAvailableProcessStepId().thenApply(futureResponse::complete) :
      CompletableFuture.completedFuture(futureResponse.complete(id)));
    return futureResponse;
  }

  public CompletableFuture<Boolean> processStepExists(UUID stepId) {
    return exists(DatabaseCell.create(stepId));
  }

  public CompletableFuture<ProcessStep> findProcessStep(UUID stepId) {
    return selectRow(DatabaseCell.create(stepId)).thenApply(ProcessStep::of);
  }

  public CompletableFuture<List<ProcessStep>> findProcessStepsByProcess(
    UUID processId
  ) {
    return selectRows("process=" + processId + " ALLOW FILTERING")
      .thenApply(rows -> rows.stream().map(ProcessStep::of)
        .collect(Collectors.toList()));
  }
}
