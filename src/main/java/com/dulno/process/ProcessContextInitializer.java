package com.dulno.process;

import com.dulno.process.structure.ProcessDatabaseTable;
import com.dulno.process.structure.connection.ProcessConnectionDatabaseTable;
import com.dulno.process.structure.step.ProcessStepDatabaseTable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@Singleton
@RequiredArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({@Inject}))
public final class ProcessContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final ProcessDatabaseTable processDatabaseTable;
  private final ProcessStepDatabaseTable processStepDatabaseTable;
  private final ProcessConnectionDatabaseTable processConnectionDatabaseTable;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    var beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingleton("processDatabaseTable", processDatabaseTable);
    beanFactory.registerSingleton("processStepDatabaseTable", processStepDatabaseTable);
    beanFactory.registerSingleton("processConnectionDatabaseTable", processConnectionDatabaseTable);
  }
}
