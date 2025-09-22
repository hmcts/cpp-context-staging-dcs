package uk.gov.moj.cpp.staging.dcs.event.service;

import uk.gov.justice.services.common.configuration.Value;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

public class DcsRestNotificationService {
    @Inject
    private Logger logger;

    @Inject
    @Value(key = "dcsBaseUrl", defaultValue = "http://localhost:8080/dcs-outbound/v1/mock/case/")
    private String dcsBaseUrl;

    @Inject
    @Value(key = "dcsSubmitCase.subscription.key", defaultValue = "")//TO DO: To check and update the subscription key
    private String subscriptionKey;

    @Inject
    private RestEasyClientService restEasyClientService;

    public Response submitCaseAndDefendantDetails(final String caseUrn, final String payload) {
        final String url = dcsBaseUrl + caseUrn + "/defendants";

        final Response response = restEasyClientService.post(url, payload, subscriptionKey);
        logger.info("API-M {} called with DCS notification and received status response: {}", url, response.getStatus());

        return response;
    }

    public Response submitMaterial(final String caseUrn, final String payload) {
        final String url = dcsBaseUrl + caseUrn + "/material";

        final Response response = restEasyClientService.post(url, payload, subscriptionKey);
        logger.info("API-M {} called with DCS notification and received status response: {}", url, response.getStatus());

        return response;
    }
    public Response sendUpdatedDefendantDetails(final String caseUrn, String defendantReferral, final String payload) {
        final String url = dcsBaseUrl + caseUrn + "/defendant/" + defendantReferral;
        final Response response = restEasyClientService.post(url, payload, subscriptionKey);
        logger.info("API-M {} called with Defendant update Request: {} and received status response: {}", url, payload, response.getStatus());

        return response;
    }
    public Response sendUpdatedDefenceRepresentationDetails(final String caseUrn, String defendantReferral, final String payload) {
        final String url = dcsBaseUrl + caseUrn + "/defendant/" + defendantReferral + "/defenceRepresentation";
        final Response response = restEasyClientService.post(url, payload, subscriptionKey);
        logger.info("API-M {} called with Defence Representation and received status response: {}", url, response.getStatus());

        return response;
    }
}