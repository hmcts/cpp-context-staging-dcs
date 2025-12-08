package uk.gov.moj.cpp.staging.dcs.helper;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.collections.MapUtils.isNotEmpty;
import static org.hamcrest.CoreMatchers.allOf;
import static uk.gov.justice.services.common.http.HeaderConstants.USER_ID;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matcher;
import uk.gov.justice.services.test.utils.core.http.FibonacciPollWithStartAndMax;

public class QueryHelper {

    private static final String QUERY = "/stagingdcs-query-api/query/api/rest/stagingdcs/dcscase/cases/";
    private static final String TRANSACTION_METADATA_QUERY = "/stagingdcs-query-api/query/api/rest/stagingdcs/transaction-metadata/case/";
    private static final String TRANSACTION_DETAILS_QUERY = "/stagingdcs-query-api/query/api/rest/stagingdcs/transaction-detail";
    private static final int QUERY_TIMEOUT_IN_SECONDS = 15;
    public static final FibonacciPollWithStartAndMax POLL_INTERVAL = new FibonacciPollWithStartAndMax(Duration.ofMillis(30), Duration.ofMillis(300));

    public void queryCaseDetailByCaseIdAndAssertMatch(final UUID caseId, final Matcher... matchers) {
        String url = getBaseUri() + QUERY + caseId.toString();
        poll(requestParams(url, "application/vnd.stagingdcs.query.dcscase-status-by-case-id+json")
                        .withHeader(USER_ID, randomUUID()).build(),
                POLL_INTERVAL, Duration.ofSeconds(QUERY_TIMEOUT_IN_SECONDS))
                .until(status().is(OK), payload().isJson(allOf(matchers)));
    }

    public String queryTransactionMetadataAndAssertMatch(final UUID caseId, final Map<String, String> params, final Matcher... matchers) {
        String url = getBaseUri() + TRANSACTION_METADATA_QUERY + caseId.toString();
        if (isNotEmpty(params)) {
            url = url.concat("?");
            for (String key : params.keySet()) {
                url = url.concat(key).concat("=").concat(params.get(key)).concat("&");
            }
            url = StringUtils.chop(url);
        }
        return poll(requestParams(url, "application/vnd.stagingdcs.query.transaction-metadata-for-case+json")
                        .withHeader(USER_ID, randomUUID()).build(),
                POLL_INTERVAL, Duration.ofSeconds(QUERY_TIMEOUT_IN_SECONDS))
                .until(status().is(OK), payload().isJson(allOf(matchers)))
                .getPayload();
    }

    public String queryTransactionDetailsAndAssertMatch(final List<UUID> tranIds, final Matcher... matchers) {
        String url = getBaseUri() + TRANSACTION_DETAILS_QUERY.concat("?transactionIds=").concat(StringUtils.join(tranIds, ","));
        return poll(requestParams(url, "application/vnd.stagingdcs.query.transaction-detail+json")
                        .withHeader(USER_ID, randomUUID()).build(),
                POLL_INTERVAL, Duration.ofSeconds(QUERY_TIMEOUT_IN_SECONDS))
                .until(status().is(OK), payload().isJson(allOf(matchers)))
                .getPayload();
    }
}
