package com.eideasy.pdf.models;

public class PrepareResponse {
    private long signatureTime;
    private String digest;

    public long getSignatureTime() {
        return signatureTime;
    }

    public void setSignatureTime(long signatureTime) {
        this.signatureTime = signatureTime;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }
}
