package com.eideasy.pdf.models;

public class CompleteRequest extends AbstractSignatureRequest {
    private String signatureValue;
    private String fileContent;
    private long signatureTime;
    private PadesDssData padesDssData;
    private VisualSignatureParameters visualSignature = null;

    public long getSignatureTime() {
        return signatureTime;
    }

    public void setSignatureTime(long signatureTime) {
        this.signatureTime = signatureTime;
    }

    public String getSignatureValue() {
        return signatureValue;
    }

    public void setSignatureValue(String signatureValue) {
        this.signatureValue = signatureValue;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public PadesDssData getPadesDssData() {
        return padesDssData;
    }

    public void setPadesDssData(PadesDssData padesDssData) {
        this.padesDssData = padesDssData;
    }

    public VisualSignatureParameters getVisualSignature() {
        return visualSignature;
    }

    public void setVisualSignature(VisualSignatureParameters visualSignature) {
        this.visualSignature = visualSignature;
    }
}
