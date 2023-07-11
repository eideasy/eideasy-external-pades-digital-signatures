package com.eideasy.pdf.models;

public class PrepareRequest extends AbstractSignatureRequest{
    private String fileContent;

    public String getFileContent() {
        return fileContent;
    }

    private VisualSignatureParameters visualSignature = null;

    public VisualSignatureParameters getVisualSignature() {
        return visualSignature;
    }

    public void setVisualSignature(VisualSignatureParameters visualSignature) {
        this.visualSignature = visualSignature;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }
}
