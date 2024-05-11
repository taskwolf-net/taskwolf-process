package net.taskwolf.process;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import net.taskwolf.core.account.AccountLink;
import net.taskwolf.core.action.ActionRepository;
import net.taskwolf.core.database.DatabaseConnection;
import net.taskwolf.core.database.DatabaseKeyspace;
import net.taskwolf.core.log.Log;
import net.taskwolf.core.module.Module;
import net.taskwolf.core.module.ModuleDescription;
import net.taskwolf.core.module.ModuleInformation;
import net.taskwolf.core.module.ModuleLoadPriority;
import net.taskwolf.core.trigger.TriggerRepository;
import net.taskwolf.process.trigger.ProcessTrigger;
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