package uk.gov.moj.cpp.staging.dcs.event.processor;

import static uk.gov.justice.services.core.annotation.Component.EVENT_PROCESSOR;

import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.inject.Inject;

import org.slf4j.Logger;

@ServiceComponent(EVENT_PROCESSOR)
public class ProgressionEventProcessor {
    @Inject
    private Logger logger;

    @Handles("public.progression.case-defendant-changed")
    public void processCaseDefendantChanged(final JsonEnvelope caseDefendantChangedEnvelope) {
        logger.info("Processing public.progression.case-defendant-changed event.");

    }
}
