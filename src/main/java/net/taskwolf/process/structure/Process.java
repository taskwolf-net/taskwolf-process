package net.taskwolf.process.structure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseRow;

import java.util.List;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class Process {
  public static Process of(DatabaseRow row) {
    return create(row.findCell(0).uuidValue(), row.findCell(1).uuidValue(),
      row.findCell(2).uuidValue(),  row.findCell(3).uuidValue(),
      row.findCell(4).listValue(), row.findCell(5).listValue(),
      row.findCell(6).longValue(), row.findCell(7).stringValue(),
      row.findCell(8).stringValue());
  }

  private final UUID id;
  private final UUID creatorId;
  private final UUID ownerId;
  private final UUID teamId;
  private final List<UUID> stepIds;
  private final List<UUID> connectionIds;
  private final long created;
  private final String name;
  private final String description;
}
