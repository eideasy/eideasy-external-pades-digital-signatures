package com.eideasy.pdf.models;

public class PrepareResponse extends ApiResponse {
    private long signatureTime;
    private String digest;
    private String hexDigest;

    public long getSignatureTime() {
        return signatureTime;
    }

    public void setSignatureTime(long signatureTime) {
        this.signatureTime = signatureTime;
    }

    public String getHexDigest() {
        return hexDigest;
    }

    public void setHexDigest(String hexDigest) {
        this.hexDigest = hexDigest;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }
}
