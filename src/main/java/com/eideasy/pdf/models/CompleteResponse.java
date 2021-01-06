package com.eideasy.pdf.models;

public class CompleteResponse extends ApiResponse {
    String signedFile;

    public String getSignedFile() {
        return signedFile;
    }

    public void setSignedFile(String signedFile) {
        this.signedFile = signedFile;
    }
}
