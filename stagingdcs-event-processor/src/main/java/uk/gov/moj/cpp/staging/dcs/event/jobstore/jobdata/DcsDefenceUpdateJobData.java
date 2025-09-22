package uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata;

import javax.json.JsonObject;

public record DcsDefenceUpdateJobData(String caseId, String defendantId, String organisationId, JsonObject metadata, String action, String transactionRef) {
}
