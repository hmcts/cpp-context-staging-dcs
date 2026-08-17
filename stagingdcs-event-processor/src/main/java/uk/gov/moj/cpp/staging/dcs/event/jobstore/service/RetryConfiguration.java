package uk.gov.moj.cpp.staging.dcs.event.jobstore.service;

import uk.gov.justice.services.common.configuration.GlobalValue;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

public class RetryConfiguration {

    private static final int TEN_SECONDS = 10;

    private static final String RETRY_DURATIONS_SECONDS =
            TEN_SECONDS + ", " + TEN_SECONDS;

    // Default value is set here to allow for fast exhaust of retries for IT tests
    // On higher environments, value is set to "30, 60, 180, 300, 900, 1800, 1800, 1800"


    @Inject
    @GlobalValue(key = "stagingdcs.task.retry.threshold.durations.seconds", defaultValue = RETRY_DURATIONS_SECONDS)
    public String taskRetryDurationsSeconds;

    @Inject
    @GlobalValue(key = "stagingdcs.downloadable.task.retry.threshold.durations.seconds", defaultValue = RETRY_DURATIONS_SECONDS)
    public String checkMaterialStatusTaskRetryDurationsSeconds;

    public List<Long> getTaskRetryDurationsSeconds() {
        try{
            return Arrays.stream(taskRetryDurationsSeconds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException nfe) {
            throw new InvalidJobStoreJndiValueException(String.format("""
                    Failed to parse '%s' value configured through JNDI parameter, \
                    name: stagingdcs.task.retry.threshold.durations.seconds""", taskRetryDurationsSeconds), nfe);
        }
    }

    public List<Long> getRecheckMaterialStatusTaskRetryDurationsSeconds() {
        try{
            return Arrays.stream(checkMaterialStatusTaskRetryDurationsSeconds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException nfe) {
            throw new InvalidJobStoreJndiValueException(String.format("""
                    Failed to parse '%s' value configured through JNDI parameter, \
                    name: stagingdcs.downloadable.task.retry.threshold.durations.seconds""", checkMaterialStatusTaskRetryDurationsSeconds), nfe);
        }
    }
}
