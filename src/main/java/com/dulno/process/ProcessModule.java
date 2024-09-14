package com.dulno.process;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.dulno.core.account.AccountLink;
import com.dulno.core.action.ActionRepository;
import com.dulno.core.database.DatabaseConnection;
import com.dulno.core.database.DatabaseKeyspace;
import com.dulno.core.log.Log;
import com.dulno.core.module.Module;
import com.dulno.core.module.ModuleDescription;
import com.dulno.core.module.ModuleInformation;
import com.dulno.core.module.ModuleLoadPriority;
import com.dulno.core.trigger.TriggerRepository;
import com.dulno.process.trigger.ProcessTrigger;
import org.springframework.boot.SpringApplication;

@ModuleDescription(name = "process", version = "1.0.0-SNAPSHOT",
  priority = ModuleLoadPriority.NEUTRAL)
public final class ProcessModule extends Module {
  private Log log;
  private SpringApplication springApplication;
  private ProcessContextInitializer contextInitializer;
  private AccountLink accountLink;

  public ProcessModule(Injector injector) {
    super(injector.createChildInjector(ProcessInjectionModule.create()));
  }

  @Override
  public void enable() throws Exception {
    log = injector().getInstance(Log.class).subLog("Process");
    springApplication = injector().getInstance(SpringApplication.class);
    contextInitializer = injector().getInstance(ProcessContextInitializer.class);
    springApplication.addInitializers(contextInitializer);
    accountLink = ProcessAccountLink.create();
  }

  @Override
  public void disable() {
    var initializers = Lists.newArrayList(springApplication.getInitializers());
    initializers.remove(contextInitializer);
    springApplication.setInitializers(initializers);
  }

  @Override
  public AccountLink accountLink() {
    return accountLink;
  }

  @Override
  public ModuleInformation moduleInformation() {
    return ModuleInformation.create("Process", "", "process.png",
      ModuleInformation.Type.PUBLIC);
  }

  @Override
  public TriggerRepository triggerRepository() {
    var databaseConnection = injector().getInstance(DatabaseConnection.class);
    var databaseKeyspace = injector().getInstance(DatabaseKeyspace.class);
    var repository = TriggerRepository.create();
    repository.registerTrigger(ProcessTrigger.create(databaseConnection,
      databaseKeyspace));
    return repository;
  }


  @Override
  public ActionRepository actionRepository() {
    return ActionRepository.create();
  }
}