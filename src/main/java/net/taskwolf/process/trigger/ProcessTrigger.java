package net.taskwolf.process.trigger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.database.*;
import net.taskwolf.core.trigger.Trigger;
import net.taskwolf.core.trigger.TriggerContentDatabaseTable;
import net.taskwolf.core.trigger.TriggerInformation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public final class ProcessTrigger implements Trigger {
  public static ProcessTrigger create(
    DatabaseConnection databaseConnection, DatabaseKeyspace databaseKeyspace
  ) {
    return new ProcessTrigger(TriggerContentDatabaseTable.create(databaseConnection,
      databaseKeyspace, "trigger_process", Lists.newArrayList()));
  }

  private final TriggerContentDatabaseTable contentDatabaseTable;

  @Override
  public String type() {
    return "process-trigger";
  }

  @Override
  public TriggerInformation information() {
    return TriggerInformation.builder()
      .withName("process.trigger.name")
      .withDescription("process.trigger.description")
      .build();
  }

  @Override
  public void initialize() {
    contentDatabaseTable.createIfNotExists();
  }

  @Override
  public CompletableFuture<Void> insert(UUID triggerId, Map<String, Object> content) {
    return contentDatabaseTable.insertContent(triggerId, DatabaseRow.of());
  }

  @Override
  public CompletableFuture<Map<String, Object>> findContent(UUID triggerId) {
    return contentDatabaseTable.findContent(triggerId).thenApply(row ->
      Maps.newHashMap());
  }

  @Override
  public CompletableFuture<List<UUID>> findEntries(String condition) {
    return contentDatabaseTable.findContentByCondition(condition).thenApply(
      rows -> rows.stream().map(row -> row.findCell(0).uuidValue()).toList());
  }

  @Override
  public CompletableFuture<Void> delete(UUID triggerId) {
    return contentDatabaseTable.deleteContent(triggerId);
  }
}
