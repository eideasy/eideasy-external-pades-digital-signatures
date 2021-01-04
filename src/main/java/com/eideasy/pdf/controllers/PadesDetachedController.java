package com.eideasy.pdf.controllers;

import com.eideasy.pdf.models.*;
import org.apache.pdfbox.cos.COSName;
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
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

@RestController
public class PadesDetachedController {
    private static final Logger logger = LoggerFactory.getLogger(PadesDetachedController.class);

    @PostMapping("/api/detached-pades/complete")
    public CompleteResponse completePdfSigning(@RequestBody CompleteRequest request) throws IOException, NoSuchAlgorithmException {
        logger.info("Completing PDF signature with value: " + request.getSignatureValue());

        PDDocument document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));
        byte[] signatureBytes = Base64.getDecoder().decode(request.getSignatureValue());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        SignatureParameters parameters = new SignatureParameters();
        parameters.setSignatureTime(request.getSignatureTime());
        parameters.setContactInfo(request.getContactInfo());
        parameters.setLocation(request.getLocation());
        parameters.setReason(request.getReason());
        parameters.setSignerName(request.getSignerName());

        signDetached(parameters, document, signatureBytes, baos);

        CompleteResponse response = new CompleteResponse();
        response.setSignedFile(Base64.getEncoder().encodeToString(baos.toByteArray()));

        logger.info("Completed PDF signature");

        return response;
    }

    @PostMapping("/api/detached-pades/prepare")
    public PrepareResponse preparePdf(@RequestBody PrepareRequest request) throws IOException, NoSuchAlgorithmException {
        logger.info("Preparing PDF");

        PDDocument document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        SignatureParameters parameters = new SignatureParameters();
        parameters.setSignatureTime(System.currentTimeMillis());
        parameters.setContactInfo(request.getContactInfo());
        parameters.setLocation(request.getLocation());
        parameters.setReason(request.getReason());
        parameters.setSignerName(request.getSignerName());

        byte[] digest = signDetached(parameters, document, null, baos);
        String digestString = HexUtils.toHexString(digest);

        PrepareResponse response = new PrepareResponse();
        response.setDigest(digestString);
        response.setSignatureTime(parameters.getSignatureTime());

        logger.info("Prepared PDF with digest: " + digestString);

        return response;
    }

    public byte[] signDetached(SignatureParameters parameters, PDDocument document, byte[] signatureBytes, OutputStream out)
            throws IOException, NoSuchAlgorithmException {

        if (document.getDocumentId() == null) {
            document.setDocumentId(parameters.getSignatureTime());
        }

        PDSignature signature = createSignatureDictionary(parameters);
        SignatureOptions options = new SignatureOptions();

        // Enough room for signature, timestamp and OCSP for baseline-LT profile.
        options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
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

    private PDSignature createSignatureDictionary(final SignatureParameters parameters) {
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
