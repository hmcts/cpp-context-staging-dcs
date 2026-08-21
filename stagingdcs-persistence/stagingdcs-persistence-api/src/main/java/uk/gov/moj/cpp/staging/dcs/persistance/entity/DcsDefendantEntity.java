package uk.gov.moj.cpp.staging.dcs.persistance.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dcs_defendant")
public class DcsDefendantEntity implements Serializable {
    @Id
    private UUID defendantId;

    @Column(name = "forename")
    private String forename;
    @Column(name = "middlename")
    private String middlename;
    @Column(name = "surname")
    private String surname;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "interpreter_language")
    private String interpreterLanguage;
    @Column(name = "interpreter_information")
    private String interpreterInformation;
    @Column(name = "bail_status")
    private String bailStatus;
    @Column(name = "organisation_name")
    private String organisationName;
    @Column(name = "defence_org_name")
    private String defenceOrgName;
    @Column(name = "defence_org_email")
    private String defenceOrgEmail;
    @Column(name = "created_at")
    private ZonedDateTime createdAt;
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
    @Column(name = "master_defendant_id")
    private UUID masterDefendantId;

    public UUID getDefendantId() {
        return defendantId;
    }

    public void setDefendantId(final UUID defendantId) {
        this.defendantId = defendantId;
    }

    public String getForename() {
        return forename;
    }

    public void setForename(final String forename) {
        this.forename = forename;
    }

    public String getMiddlename() {
        return middlename;
    }

    public void setMiddlename(final String middlename) {
        this.middlename = middlename;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(final String surname) {
        this.surname = surname;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(final LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getInterpreterLanguage() {
        return interpreterLanguage;
    }

    public void setInterpreterLanguage(final String interpreterLanguage) {
        this.interpreterLanguage = interpreterLanguage;
    }

    public String getInterpreterInformation() {
        return interpreterInformation;
    }

    public void setInterpreterInformation(final String interpreterInformation) {
        this.interpreterInformation = interpreterInformation;
    }

    public String getBailStatus() {
        return bailStatus;
    }

    public void setBailStatus(final String bailStatus) {
        this.bailStatus = bailStatus;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(final String organisationName) {
        this.organisationName = organisationName;
    }

    public String getDefenceOrgName() {
        return defenceOrgName;
    }

    public void setDefenceOrgName(final String defenceOrgName) {
        this.defenceOrgName = defenceOrgName;
    }

    public String getDefenceOrgEmail() {
        return defenceOrgEmail;
    }

    public void setDefenceOrgEmail(final String defenceOrgEmail) {
        this.defenceOrgEmail = defenceOrgEmail;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getMasterDefendantId() {
        return masterDefendantId;
    }

    public void setMasterDefendantId(final UUID masterDefendantId) {
        this.masterDefendantId = masterDefendantId;
    }
}
