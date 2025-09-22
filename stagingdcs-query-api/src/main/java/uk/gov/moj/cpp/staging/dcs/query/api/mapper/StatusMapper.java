package uk.gov.moj.cpp.staging.dcs.query.api.mapper;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import uk.gov.moj.cpp.staging.dcs.domain.common.DcsDefendantStatus;
import uk.gov.moj.cpp.staging.dcs.domain.common.TransactionStatus;
import uk.gov.moj.cpp.staging.dcs.persistance.entity.DcsCaseDetailEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.collections.CollectionUtils;

public class StatusMapper {

    private static final String LINKED_TO_DCS = "LINKED TO DCS";
    private static final String NOT_LINKED_TO_DCS = "NOT LINKED TO DCS";
    private static final String DCS_UPDATED = "DCS UPDATED";
    private static final String DCS_NOT_UPDATED = "DCS NOT UPDATED";
    private static final String DCS_LINK_SEVERED = "DCS LINK SEVERED";

    private StatusMapper() {
    }

    private static final Predicate<DcsCaseDetailEntity> isLinkedPredicate =
            dcsCaseDetail -> DcsDefendantStatus.LINKED.name().equals(dcsCaseDetail.getDcsDefendantStatus());

    public static String mapCaseStatus(List<DcsCaseDetailEntity> dcsCaseDetailEntities) {
        return Optional.ofNullable(dcsCaseDetailEntities)
                .filter(CollectionUtils::isNotEmpty)
                .filter(entities -> entities.stream().anyMatch(isLinkedPredicate))
                .map(entities -> LINKED_TO_DCS)
                .orElse(NOT_LINKED_TO_DCS);
    }

    public static String mapDefendantStatus(DcsCaseDetailEntity dcsCaseDetail) {
        Optional<String> status = Arrays.stream(DefendantStatusMapper.values())
                .filter(enumValue -> enumValue.getDcsDefendantStatus().name().equals(dcsCaseDetail.getDcsDefendantStatus()))
                .map(DefendantStatusMapper::getStatus)
                .findFirst();
        return status.orElse(NOT_LINKED_TO_DCS);
    }

    public static String mapTransactionStatus(String transactionStatus) {
        return Optional.ofNullable(transactionStatus)
                .filter(status -> !status.isEmpty())
                .map(status -> {
                    if (TransactionStatus.SUCCESS.name().equals(status)) {
                        return DCS_UPDATED;
                    } else if (TransactionStatus.FAILED.name().equals(status)) {
                        return DCS_NOT_UPDATED;
                    } else {
                        return EMPTY;
                    }
                })
                .orElse(EMPTY);
    }

    public enum DefendantStatusMapper {
        DCS_DEFENDANT_STATUS_LINKED(DcsDefendantStatus.LINKED, StatusMapper.LINKED_TO_DCS),
        DCS_DEFENDANT_STATUS_FAILED(DcsDefendantStatus.FAILED, StatusMapper.NOT_LINKED_TO_DCS),
        DCS_DEFENDANT_STATUS_UNLINKED(DcsDefendantStatus.UNLINKED, StatusMapper.DCS_LINK_SEVERED);

        private DcsDefendantStatus defendantStatus;
        private String status;

        DefendantStatusMapper(final DcsDefendantStatus defendantStatus, final String status) {
            this.defendantStatus = defendantStatus;
            this.status = status;
        }

        public DcsDefendantStatus getDcsDefendantStatus() {
            return this.defendantStatus;
        }

        public String getStatus() {
            return this.status;
        }
    }
}
