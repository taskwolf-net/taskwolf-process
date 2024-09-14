package com.dulno.process;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import com.dulno.core.account.AccountLink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public final class ProcessAccountLink implements AccountLink {
  @Override
  public CompletableFuture<Boolean> accountExists(UUID userId) {
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public CompletableFuture<List<String>> findAccounts(UUID userId) {
    return CompletableFuture.completedFuture(Lists.newArrayList());
  }

  @Override
  public void removeAccount(UUID userId, String identifier) {

  }

  @Override
  public String registrationUrl(UUID id, String apiKey) {
    return "";
  }

  @Override
  public String description() {
    return "process.link.description";
  }
}