package com.flipkart.drift.api.service.utils;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Singleton
public class Utility {
    private static final String WORKFLOW_ID_PREFIX = "WF-";
    private static final DateTimeFormatter ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");
    private static final int RANDOM_ID_BOUND = 100000;
    private static final String RANDOM_ID_FORMAT = "%05d";

    /**
     * Generates a workflow ID based on incident creation allowance
     * @param externalIncidentId incident id
     * @param isIncidentCreationAllowed whether incident creation is allowed
     * @return generated workflow ID
     */
    public String generateWorkflowId(String externalIncidentId, boolean isIncidentCreationAllowed) {

        if (isIncidentCreationAllowed) {
            return WORKFLOW_ID_PREFIX + externalIncidentId;
        }
        return generateRandomWorkflowId();
    }

    /**
     * Generates a random workflow ID using current timestamp and random number
     * @return random workflow ID
     */
    private String generateRandomWorkflowId() {
        String timestamp = LocalDateTime.now().format(ID_DATE_FORMAT);
        int randomNumber = ThreadLocalRandom.current().nextInt(RANDOM_ID_BOUND);
        return WORKFLOW_ID_PREFIX + timestamp + String.format(RANDOM_ID_FORMAT, randomNumber);
    }
}
