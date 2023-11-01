package sdk.db.vcs.model;

public class DBVcsSchema {
    private String versionNo;
    private String executedAt;
    private String fileHash;
    private String sqlFile;

    public DBVcsSchema(String versionNo, String executedAt, String fileHash, String sqlFile) {
        this.versionNo = versionNo;
        this.executedAt = executedAt;
        this.fileHash = fileHash;
        this.sqlFile = sqlFile;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getSqlFile() {
        return sqlFile;
    }

    public void setSqlFile(String sqlFile) {
        this.sqlFile = sqlFile;
    }
}
