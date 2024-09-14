package com.dulno.process.structure;

import com.google.common.collect.Lists;
import com.dulno.core.database.*;
import com.dulno.core.database.condition.DatabaseComparison;
import com.dulno.core.database.condition.DatabaseCondition;
import com.dulno.core.database.paging.DatabaseDirection;
import com.dulno.core.database.paging.DatabaseOrder;
import com.dulno.core.database.paging.DatabasePage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProcessDatabaseTable extends DatabaseTable {
  private static final String TABLE_NAME = "process";

  public static ProcessDatabaseTable create(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var columns = Lists.<DatabaseColumn>newArrayList();
    columns.add(DatabaseColumn.create("owner", DatabaseDataType.UUID,
      DatabaseColumn.Type.PARTITION_KEY));
    columns.add(DatabaseColumn.create("id", DatabaseDataType.UUID,
      DatabaseColumn.Type.CLUSTERING_KEY));
    columns.add(DatabaseColumn.create("creator", DatabaseDataType.UUID));
    columns.add(DatabaseListColumn.create("steps", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("stepCount", DatabaseDataType.INT));
    columns.add(DatabaseListColumn.create("connections", DatabaseDataType.UUID));
    columns.add(DatabaseColumn.create("created", DatabaseDataType.BIGINT));
    columns.add(DatabaseColumn.create("name", DatabaseDataType.TEXT));
    columns.add(DatabaseColumn.create("description", DatabaseDataType.TEXT));
    var table = new ProcessDatabaseTable(connection, keyspace, TABLE_NAME, columns);
    table.createIfNotExists();
    table.createIndexIfNotExists("id");
    table.createIndexIfNotExists("name",
      "'org.apache.cassandra.index.sasi.SASIIndex' WITH OPTIONS = " +
        "{'mode': 'CONTAINS', 'analyzer_class': " +
        "'org.apache.cassandra.index.sasi.analyzer.NonTokenizingAnalyzer', " +
        "'case_sensitive': 'false'}");
    table.initializeViews();
    return table;
  }

  private DatabaseTable nameView;
  private DatabaseTable creatorView;
  private DatabaseTable createdView;
  private DatabaseTable stepView;

  private ProcessDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace, String name,
    List<DatabaseColumn> columns
  ) {
    super(connection, keyspace, name, columns);
  }

  private void initializeViews() {
    nameView = createMaterializedViewIfNotExists("name_view", "name");
    creatorView = createMaterializedViewIfNotExists("creator_view", "creator");
    createdView = createMaterializedViewIfNotExists("created_view", "created");
    stepView = createMaterializedViewIfNotExists("step_view", "stepCount");
  }

  public void insertProcess(Process process) {
    insertProcess(process.ownerId(), process.id(), process.creatorId(),
      process.stepIds(), process.stepCount(), process.connectionIds(),
      process.created(), process.name(), process.description());
  }

  public void insertProcess(
    UUID ownerId, UUID id, UUID creatorId, List<UUID> stepIds, int stepCount,
    List<UUID> connectionIds, long created, String name, String description
  ) {
    insert(DatabaseRow.of(ownerId, id, creatorId, stepIds, stepCount,
      connectionIds, created, name, description));
  }

  public void deleteProcess(UUID processId) {
    findProcess(processId).thenAccept(process ->
      delete(DatabaseCondition.of("owner", process.ownerId(), "id", process.id())));
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
    return exists(DatabaseCondition.of("id", processId));
  }

  public CompletableFuture<Process> findProcess(UUID processId) {
    return selectRow(DatabaseCondition.of("id", processId))
      .thenApply(row -> Process.of(row, this));
  }

  private static final int PAGE_SIZE = 5;

  public CompletableFuture<DatabasePage<Process>> findProcessesOfOwner(
    UUID ownerId, int targetPage, String sortingColumn, DatabaseOrder sortingOrder,
    String search, UUID creatorId, long startTime, long endTime, long minimumSteps,
    long maximumSteps
  ) {
    if (!search.isEmpty()) {
      var condition = DatabaseCondition.of(DatabaseComparison.create("owner", ownerId),
        DatabaseComparison.create("name", "%" + search + "%", DatabaseComparison.Type.LIKE));
      return selectRows(condition, PAGE_SIZE)
        .thenApply(rows -> createProcessPage(DatabasePage.create(rows, "", 1), this));
    }
    var view = findTargetView(sortingColumn);
    return view.selectPage(ownerId, createProcessConditions(creatorId, startTime,
          endTime, minimumSteps, maximumSteps),
        sortingOrder, PAGE_SIZE, targetPage)
      .thenApply(page -> createProcessPage(page, view));
  }

  public CompletableFuture<DatabasePage<Process>> findProcessesOfOwner(
    UUID ownerId, String pageState, DatabaseDirection startingPoint,
    DatabaseDirection direction, String sortingColumn, DatabaseOrder sortingOrder,
    UUID creatorId, long startTime, long endTime, long minimumSteps,
    long maximumSteps
  ) {
    var view = findTargetView(sortingColumn);
    return view.shiftPage(ownerId, createProcessConditions(creatorId, startTime,
          endTime, minimumSteps, maximumSteps),
        sortingOrder, PAGE_SIZE, pageState, startingPoint, direction)
      .thenApply(page -> createProcessPage(page, view));
  }

  private DatabaseTable findTargetView(String sortingColumn) {
    if (sortingColumn.equals("name")) {
      return nameView;
    } else if (sortingColumn.equals("creator")) {
      return creatorView;
    } else if (sortingColumn.equals("created")) {
      return createdView;
    } else if (sortingColumn.equals("steps")) {
      return stepView;
    }
    return null;
  }

  private DatabaseCondition createProcessConditions(
    UUID creatorId, long startTime, long endTime, long minimumSteps,
    long maximumSteps
  ) {
    var comparisons = Lists.<DatabaseComparison>newArrayList();
    if (creatorId != null) {
      comparisons.add(DatabaseComparison.create("creator", creatorId));
    }
    if (startTime > 0) {
      comparisons.add(DatabaseComparison.create("created", startTime,
        DatabaseComparison.Type.GREATER_EQUALS));
    }
    if (endTime > 0) {
      comparisons.add(DatabaseComparison.create("created", endTime,
        DatabaseComparison.Type.SMALLER_EQUALS));
    }
    if (minimumSteps > 0) {
      comparisons.add(DatabaseComparison.create("stepCount", minimumSteps,
        DatabaseComparison.Type.GREATER_EQUALS));
    }
    if (maximumSteps > 0) {
      comparisons.add(DatabaseComparison.create("stepCount", maximumSteps,
        DatabaseComparison.Type.SMALLER_EQUALS));
    }
    return DatabaseCondition.create(comparisons);
  }

  private DatabasePage<Process> createProcessPage(
    DatabasePage<DatabaseRow> page, DatabaseTable table
  ) {
    return DatabasePage.create(
      page.content().stream().map(row -> Process.of(row, table)).toList(),
      page.pageState(), page.pageNumber());
  }

  public CompletableFuture<Long> findProcessCount(UUID ownerId) {
    return count(DatabaseCondition.of("owner", ownerId));
  }

  public CompletableFuture<List<Process>> findAllProcessesOfOwner(
    UUID ownerId
  ) {
    return selectRows(DatabaseCondition.of("owner", ownerId)).thenApply(rows ->
      rows.stream().map(row -> Process.of(row, this)).toList());
  }
}
