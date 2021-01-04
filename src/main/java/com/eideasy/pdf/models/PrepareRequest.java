package com.eideasy.pdf.models;

public class PrepareRequest extends AbstractSignatureRequest{
    private String fileContent;

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }
}
