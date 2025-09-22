package uk.gov.moj.cpp.staging.dcs.query.api.accesscontrol;

import static java.util.Collections.singletonList;

import java.util.List;

public final class RuleConstants {

    private static final String SYSTEM_USERS = "System Users";

    private RuleConstants() {
    }

    public static List<String> createCaseRequestGroups() {
        return singletonList(SYSTEM_USERS);
    }

}

