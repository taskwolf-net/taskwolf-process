package com.dulno.process;

import com.dulno.process.structure.ProcessDatabaseTable;
import com.dulno.process.structure.connection.ProcessConnectionDatabaseTable;
import com.dulno.process.structure.step.ProcessStepDatabaseTable;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;

@RequiredArgsConstructor(staticName = "create")
public class ProcessInjectionModule extends AbstractModule {
  @Provides
  @Singleton
  ProcessDatabaseTable provideProcessDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    return ProcessDatabaseTable.create(connection, keyspace);
  }

  @Provides
  @Singleton
  ProcessStepDatabaseTable provideProcessStepDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var processStepDatabaseTable = ProcessStepDatabaseTable.create(connection, keyspace);
    processStepDatabaseTable.createIfNotExists();
    processStepDatabaseTable.createIndexIfNotExists("process");
    return processStepDatabaseTable;
  }

  @Provides
  @Singleton
  ProcessConnectionDatabaseTable provideProcessConnectionDatabaseTable(
    DatabaseConnection connection, DatabaseKeyspace keyspace
  ) {
    var processConnectionDatabaseTable = ProcessConnectionDatabaseTable.create(
      connection, keyspace);
    processConnectionDatabaseTable.createIfNotExists();
    processConnectionDatabaseTable.createIndexIfNotExists("process");
    return processConnectionDatabaseTable;
  }
}
