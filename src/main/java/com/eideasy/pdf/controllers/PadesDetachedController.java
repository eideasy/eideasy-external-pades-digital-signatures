package com.eideasy.pdf.controllers;

import com.eideasy.pdf.models.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.tomcat.util.buf.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
public class PadesDetachedController {
    private static final Logger logger = LoggerFactory.getLogger(PadesDetachedController.class);

    @PostMapping("/api/detached-pades/complete")
    public CompleteResponse completePdfSigning(@RequestBody CompleteRequest request) throws IOException, NoSuchAlgorithmException {
        SignatureParameters parameters = new SignatureParameters();
        parameters.setSignatureTime(request.getSignatureTime());
        parameters.setContactInfo(request.getContactInfo());
        parameters.setLocation(request.getLocation());
        parameters.setReason(request.getReason());
        parameters.setSignerName(request.getSignerName());

        logger.info("Completing PDF signature with value: " + request.getSignatureValue() + ", params=" + parameters);
        CompleteResponse response = new CompleteResponse();
        PDDocument document = null;
        try {
            document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));

            byte[] signatureBytes = Base64.getDecoder().decode(request.getSignatureValue());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            signDetached(parameters, document, signatureBytes, baos);

            baos = addPadesDss(request.getPadesDssData(), baos);

            response.setSignedFile(Base64.getEncoder().encodeToString(baos.toByteArray()));

            logger.info("Completed PDF signature");
        } catch (Throwable e) {
            logger.error("PDF parsing failed", e);
            response.setStatus("error");
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @PostMapping("/api/detached-pades/prepare")
    public PrepareResponse preparePdf(@RequestBody PrepareRequest request) throws IOException, NoSuchAlgorithmException {
        SignatureParameters parameters = new SignatureParameters();
        parameters.setSignatureTime(System.currentTimeMillis());
        parameters.setContactInfo(request.getContactInfo());
        parameters.setLocation(request.getLocation());
        parameters.setReason(request.getReason());
        parameters.setSignerName(request.getSignerName());

        logger.info("Preparing PDF, params=" + parameters);
        PrepareResponse response = new PrepareResponse();
        PDDocument document = null;
        try {
            document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] digest = signDetached(parameters, document, null, baos);
            String digestString = HexUtils.toHexString(digest);

            response.setHexDigest(digestString);
            response.setDigest(Base64.getEncoder().encodeToString(digest));
            response.setSignatureTime(parameters.getSignatureTime());

            logger.info("Prepared PDF with digest: " + digestString + ", signatureTime=" + response.getSignatureTime());
        } catch (Throwable e) {
            logger.error("PDF parsing failed", e);
            response.setStatus("error");
            response.setMessage(e.getMessage());
        }
        return response;
    }

    protected ByteArrayOutputStream addPadesDss(PadesDssData padesDssData, ByteArrayOutputStream baos) throws IOException {
        if (padesDssData == null || (padesDssData.getCrls().size() == 0 && padesDssData.getCertificates().size() == 0 && padesDssData.getOcsps().size() == 0)) {
            return baos;
        }

        InputStream is = new ByteArrayInputStream(baos.toByteArray());

        PDDocument pdDocument = PDDocument.load(is);

        final COSDictionary cosDictionary = pdDocument.getDocumentCatalog().getCOSObject();
        cosDictionary.setItem("DSS", buildDSSDictionary(pdDocument, padesDssData));
        cosDictionary.setNeedToBeUpdated(true);

        baos = new ByteArrayOutputStream();

        pdDocument.saveIncremental(baos);
        return baos;
    }

    private COSDictionary buildDSSDictionary(PDDocument pdDocument, PadesDssData padesDssData)
            throws IOException {
        COSDictionary dss = new COSDictionary();
        COSDictionary vri = new COSDictionary();

        COSArray certArray = new COSArray();
        if (padesDssData.getCertificates().size() > 0) {
            for (String cert : padesDssData.getCertificates()) {
                COSStream stream = pdDocument.getDocument().createCOSStream();
                try (OutputStream unfilteredStream = stream.createOutputStream()) {
                    unfilteredStream.write(Base64.getDecoder().decode(cert));
                    unfilteredStream.flush();
                }
                certArray.add(stream);
            }
            vri.setItem("Cert", certArray);
        }

        COSArray ocspArray = new COSArray();
        if (padesDssData.getOcsps().size() > 0) {
            for (String cert : padesDssData.getOcsps()) {
                COSStream stream = pdDocument.getDocument().createCOSStream();
                try (OutputStream unfilteredStream = stream.createOutputStream()) {
                    unfilteredStream.write(Base64.getDecoder().decode(cert));
                    unfilteredStream.flush();
                }
                ocspArray.add(stream);
            }
            vri.setItem("OCSP", ocspArray);
        }

        COSArray crlArray = new COSArray();
        if (padesDssData.getCrls().size() > 0) {
            for (String cert : padesDssData.getCrls()) {
                COSStream stream = pdDocument.getDocument().createCOSStream();
                try (OutputStream unfilteredStream = stream.createOutputStream()) {
                    unfilteredStream.write(Base64.getDecoder().decode(cert));
                    unfilteredStream.flush();
                }
                crlArray.add(stream);
            }
            vri.setItem("CRL", crlArray);
        }

        dss.setItem("VRI", vri);
        dss.setItem("Certs", certArray);
        if (padesDssData.getOcsps().size() > 0) {
            dss.setItem("OCSPs", ocspArray);
        }
        if (padesDssData.getCrls().size() > 0) {
            dss.setItem("CRLs", crlArray);
        }

        return dss;
    }

    protected byte[] signDetached(SignatureParameters parameters, PDDocument document, byte[] signatureBytes, OutputStream out)
            throws IOException, NoSuchAlgorithmException {

        if (document.getDocumentId() == null) {
            document.setDocumentId(parameters.getSignatureTime());
        }

        PDSignature signature = createSignatureDictionary(parameters);
        SignatureOptions options = new SignatureOptions();

        // Enough room for signature, timestamp and OCSP for baseline-LT profile.
        options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE);
        document.addSignature(signature, options);
        ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(out);

        byte[] dataToSign = IOUtils.toByteArray(externalSigning.getContent());
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] digestBytes = digest.digest(dataToSign);

        if (signatureBytes != null) {
            externalSigning.setSignature(signatureBytes);
        }

        return digestBytes;
    }

    protected PDSignature createSignatureDictionary(final SignatureParameters parameters) {
        PDSignature signature = new PDSignature();

        signature.setType(COSName.getPDFName("Sig"));
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);

        if (notEmpty(parameters.getSignerName())) {
            signature.setName(parameters.getSignerName());
        }

        if (notEmpty(parameters.getContactInfo())) {
            signature.setContactInfo(parameters.getContactInfo());
        }

        if (notEmpty(parameters.getLocation())) {
            signature.setLocation(parameters.getLocation());
        }

        if (notEmpty(parameters.getReason())) {
            signature.setReason(parameters.getReason());
        }

        // the signing date, needed for valid signature
        final Calendar cal = Calendar.getInstance();
        final Date signingDate = new Date(parameters.getSignatureTime());
        cal.setTime(signingDate);
        signature.setSignDate(cal);

        return signature;
    }

    public static boolean notEmpty(CharSequence cs) {
        return cs != null && cs.length() != 0;
    }
}
