package com.dulno.process.structure.connection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import com.dulno.core.database.DatabaseRow;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "create")
public class ProcessConnection {
  public static ProcessConnection of(DatabaseRow row) {
    return create(row.findCell(0).uuidValue(), row.findCell(1).uuidValue(),
      row.findCell(2).uuidValue(), row.findCell(3).uuidValue());
  }

  private final UUID id;
  private final UUID processId;
  private final UUID originStepId;
  private final UUID destinationStepId;
}
