package com.github.onozaty.sample.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupTask {

  private static final Logger logger = LoggerFactory.getLogger(SessionCleanupTask.class);

  private final AuthService authService;

  public SessionCleanupTask(AuthService authService) {
    this.authService = authService;
  }

  // 毎日 03:00 に有効な refresh_token を持たないセッションを削除する。
  @Scheduled(cron = "0 0 3 * * *")
  public void cleanup() {
    int deleted = authService.cleanupExpiredSessions();
    if (deleted > 0) {
      logger.info("期限切れセッションを {} 件削除しました。", deleted);
    }
  }
}
