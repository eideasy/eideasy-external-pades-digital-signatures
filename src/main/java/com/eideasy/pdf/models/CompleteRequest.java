package com.eideasy.pdf.models;

public class CompleteRequest extends AbstractSignatureRequest {
    String signatureValue;
    String fileContent;
    long signatureTime;

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
}
