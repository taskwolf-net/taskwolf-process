package net.taskwolf.process.structure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseColumn;
import net.taskwolf.core.database.DatabaseRow;
import net.taskwolf.core.database.DatabaseTable;

import java.util.List;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class Process {
  public static Process of(DatabaseRow row, DatabaseTable table) {
    return of(row, table.columns().stream().map(DatabaseColumn::name).toList());
  }

  public static Process of(DatabaseRow row, List<String> columns) {
    return create(row.findCell(columns.indexOf("owner")).uuidValue(),
      row.findCell(columns.indexOf("id")).uuidValue(),
      row.findCell(columns.indexOf("creator")).uuidValue(),
      row.findCell(columns.indexOf("steps")).listValue(),
      row.findCell(columns.indexOf("stepCount")).integerValue(),
      row.findCell(columns.indexOf("connections")).listValue(),
      row.findCell(columns.indexOf("created")).longValue(),
      row.findCell(columns.indexOf("name")).stringValue(),
      row.findCell(columns.indexOf("description")).stringValue());
  }

  private final UUID ownerId;
  private final UUID id;
  private final UUID creatorId;
  private final List<UUID> stepIds;
  private final int stepCount;
  private final List<UUID> connectionIds;
  private final long created;
  private final String name;
  private final String description;
}
