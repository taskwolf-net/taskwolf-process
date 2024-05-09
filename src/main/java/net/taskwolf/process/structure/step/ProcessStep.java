package net.taskwolf.process.structure.step;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.taskwolf.core.database.DatabaseRow;

import java.util.List;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public final class ProcessStep {
  public static ProcessStep of(DatabaseRow row) {
    return create(row.findCell(0).uuidValue(), row.findCell(1).uuidValue(),
      row.findCell(2).stringValue(), row.findCell(3).stringValue(),
      row.findCell(3).listValue(),
      ProcessStepType.valueOf(row.findCell(4).stringValue().toUpperCase()),
      row.findCell(5).integerValue(), row.findCell(6).integerValue());
  }

  private final UUID id;
  private final UUID processId;
  private final String name;
  private final String description;
  private final List<UUID> workflows;
  private final ProcessStepType type;
  private final int xCoordinate;
  private final int yCoordinate;
}
