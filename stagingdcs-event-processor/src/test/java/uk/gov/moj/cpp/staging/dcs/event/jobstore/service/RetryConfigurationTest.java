package uk.gov.moj.cpp.staging.dcs.event.jobstore.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil;

import java.util.List;

import org.junit.jupiter.api.Test;
 class RetryConfigurationTest {

    @Test
    void shouldReturnRetryDurations() {
        final RetryConfiguration retryConfiguration = new RetryConfiguration();
        ReflectionUtil.setField(retryConfiguration, "taskRetryDurationsSeconds", "30 ,  60 ");

        final List<Long> retryDurationsSeconds = retryConfiguration.getTaskRetryDurationsSeconds();

        assertThat(retryDurationsSeconds.size(), is(2));
        assertThat(retryDurationsSeconds.get(0), is(30L));
        assertThat(retryDurationsSeconds.get(1), is(60L));
    }

    @Test
    void shouldThrowException_whenConfigurationValueIsNotParsable() {
        final RetryConfiguration retryConfiguration = new RetryConfiguration();
        ReflectionUtil.setField(retryConfiguration, "taskRetryDurationsSeconds", "20, abc");

        final InvalidJobStoreJndiValueException e = assertThrows(InvalidJobStoreJndiValueException.class,
                retryConfiguration::getTaskRetryDurationsSeconds);

        assertThat(e.getMessage(), is("Failed to parse '20, abc' value configured through JNDI parameter, name: stagingdcs.task.retry.threshold.durations.seconds"));
    }
}