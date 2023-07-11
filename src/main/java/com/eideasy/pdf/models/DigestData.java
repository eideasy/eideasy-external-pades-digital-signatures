package com.eideasy.pdf.models;

import org.apache.pdfbox.pdmodel.PDDocument;

public class DigestData {
    private PDDocument document;
    private byte[] digest;
    public PDDocument getDocument() {
        return document;
    }

    public void setDocument(PDDocument document) {
        this.document = document;
    }

    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
    }
}
