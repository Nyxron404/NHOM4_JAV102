package models.catalog;

/**
 * Phương pháp sơ chế
 * <p>Bảng: processing_methods</p>
 */
public class ProcessingMethod {

    public static final String TABLE = "processing_methods";
    public static final String[] PRIMARY_KEY = {"process_id"};

    /** process_id | INT | PK | tự tăng */
    private Integer processId;
    /** process_name | d_name | NOT NULL */
    private String processName;
    /** description | d_text */
    private String description;

    public ProcessingMethod() {}

    public Integer getProcessId() { return processId; }
    public void setProcessId(Integer processId) { this.processId = processId; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "ProcessingMethod{" + "processId=" + processId + ", processName=" + processName + ", description=" + description + "}";
    }
}
