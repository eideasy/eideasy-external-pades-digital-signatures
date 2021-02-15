package com.eideasy.pdf.models;

import java.util.List;

public class PadesDssData {
    private List<String> certificates;
    private List<String> ocsps;
    private List<String> crls;

    public List<String> getCertificates() {
        return certificates;
    }

    public void setCertificates(List<String> certificates) {
        this.certificates = certificates;
    }

    public List<String> getOcsps() {
        return ocsps;
    }

    public void setOcsps(List<String> ocsps) {
        this.ocsps = ocsps;
    }

    public List<String> getCrls() {
        return crls;
    }

    public void setCrls(List<String> crls) {
        this.crls = crls;
    }
}
