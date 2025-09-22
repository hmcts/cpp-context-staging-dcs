package uk.gov.moj.cpp.staging.dcs.event.jobstore.jobdata;

import uk.gov.justice.services.messaging.JsonMetadata;
import uk.gov.justice.services.messaging.Metadata;

import javax.json.JsonObject;

public record DcsUpdateJobData(String caseId, String defendantId, JsonObject metadata, String transactionRef) {
}
