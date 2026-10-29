package com.aeolens.api.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HistoryRetentionJob {

    private static final Logger logger = LoggerFactory.getLogger(HistoryRetentionJob.class);

    private final DatabaseClient databaseClient;

    public HistoryRetentionJob(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Scheduled(cron = "${aeolens.history.cleanup-cron:0 15 3 * * *}")
    public void removeExpiredHistory() {
        databaseClient.sql("DELETE FROM audits WHERE expires_at <= CURRENT_TIMESTAMP")
                .fetch()
                .rowsUpdated()
                .then(databaseClient.sql("DELETE FROM guest_sessions WHERE expires_at <= CURRENT_TIMESTAMP")
                        .fetch()
                        .rowsUpdated())
                .then(databaseClient.sql("""
                                DELETE FROM guest_users
                                WHERE expires_at <= CURRENT_TIMESTAMP
                                  AND NOT EXISTS (
                                      SELECT 1 FROM guest_sessions
                                      WHERE guest_sessions.guest_id = guest_users.id
                                  )
                                  AND NOT EXISTS (
                                      SELECT 1 FROM audits
                                      WHERE audits.guest_id = guest_users.id
                                  )
                                """)
                        .fetch()
                        .rowsUpdated())
                .subscribe(
                        ignored -> { },
                        error -> logger.warn("Could not remove expired anonymous history", error));
    }
}
