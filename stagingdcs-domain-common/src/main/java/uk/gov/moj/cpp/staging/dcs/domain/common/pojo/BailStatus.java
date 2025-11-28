package uk.gov.moj.cpp.staging.dcs.domain.common.pojo;

import java.util.UUID;

public record BailStatus(String code, String description, UUID id) {}
