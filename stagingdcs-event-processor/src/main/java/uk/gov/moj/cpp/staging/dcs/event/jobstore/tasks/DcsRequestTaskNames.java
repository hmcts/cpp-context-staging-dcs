package uk.gov.moj.cpp.staging.dcs.event.jobstore.tasks;

public class DcsRequestTaskNames {
    public static final String DCS_NOTIFICATION_TASK = "submit-case-and-defendant-details";
    public static final String SET_NOTIFICATION_STATUS_FAILED_TASK = "set-notification-status-failed";
    public static final String SET_UPDATE_STATUS_FAILED_TASK = "set-update-status-failed";
    public static final String DEFENDANT_UPDATE_TASK = "send-case-defendant-update";
    public static final String DEFENCE_REPRESENTATION_TASK = "send-defence-representation-details";
    public static final String SEND_MATERIAL_TO_DCS_TASK = "send-material-to-dcs";

    public static final String INITIATE_MATERIAL_TASK_FOR_CASE = "initiate-material-task-for-case";
    public static final String PROCESS_ADD_COURT_DOCUMENT_TASK = "process-add-court-document";
    public static final String UPLOAD_MATERIAL_TO_STORAGE_TASK = "upload-material-to-storage";
    public static final String CHECK_MATERIAL_STATUS_TASK = "check-material-status";
    public static final String INSERT_MATERIAL_DOCUMENT_TASK = "insert-material-document";

    private DcsRequestTaskNames() {}
}
