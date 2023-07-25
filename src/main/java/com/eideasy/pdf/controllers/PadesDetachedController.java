package com.eideasy.pdf.controllers;

import com.eideasy.pdf.models.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Matrix;
import org.apache.tomcat.util.buf.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

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
        VisualSignatureParameters visualSignatureParameters = request.getVisualSignature();

        logger.info("Completing PDF signature with value: " + request.getSignatureValue() + ", params=" + parameters);
        CompleteResponse response = new CompleteResponse();
        PDDocument document = null;
        try {
            document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));

            byte[] signatureBytes = Base64.getDecoder().decode(request.getSignatureValue());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            signDetached(parameters, document, signatureBytes, baos, visualSignatureParameters);

            //baos = addPadesDss(request.getPadesDssData(), baos);

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
        VisualSignatureParameters visualSignatureParameters = request.getVisualSignature();

        logger.info("Preparing PDF, params=" + parameters);
        PrepareResponse response = new PrepareResponse();
        PDDocument document = null;
        try {
            document = PDDocument.load(Base64.getDecoder().decode(request.getFileContent()));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            DigestData digestData = signDetached(parameters, document, null, baos, visualSignatureParameters);
            String digestString = HexUtils.toHexString(digestData.getDigest());


            response.setHexDigest(digestString);
            response.setDigest(Base64.getEncoder().encodeToString(digestData.getDigest()));
            response.setSignatureTime(parameters.getSignatureTime());

            PDDocument modifiedDocument = digestData.getDocument();
            ByteArrayOutputStream modifiedDocumentBaos = new ByteArrayOutputStream();
            modifiedDocument.save(modifiedDocumentBaos);

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

    protected DigestData signDetached(
        SignatureParameters parameters,
        PDDocument document,
        byte[] signatureBytes,
        OutputStream out,
        VisualSignatureParameters visualSignatureParameters
    ) throws IOException, NoSuchAlgorithmException {

        if (document.getDocumentId() == null) {
            document.setDocumentId(parameters.getSignatureTime());
        }

        PDSignature signature = createSignatureDictionary(parameters);
        SignatureOptions options = new SignatureOptions();


        // Enough room for signature, timestamp and OCSP for baseline-LT profile.
        options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE);

        if (visualSignatureParameters != null) {
            int pageNum = visualSignatureParameters.getPageNum() - 1;
            options.setPage(pageNum);
            Rectangle2D humanRect = new Rectangle2D.Float(
                visualSignatureParameters.getX(),
                visualSignatureParameters.getY(),
                visualSignatureParameters.getWidth(),
                visualSignatureParameters.getHeight()
            );
            PDRectangle rect = null;
            rect = createSignatureRectangle(document, humanRect);
            options.setVisualSignature(createVisualSignatureTemplate(
                document,
                pageNum,
                rect,
                visualSignatureParameters.getImage()
            ));
        }

        document.addSignature(signature, options);
        ExternalSigningSupport externalSigning = document.saveIncrementalForExternalSigning(out);

        byte[] dataToSign = IOUtils.toByteArray(externalSigning.getContent());
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] digestBytes = digest.digest(dataToSign);

        if (signatureBytes != null) {
            externalSigning.setSignature(signatureBytes);
        }

        DigestData digestData = new DigestData();
        digestData.setDigest(digestBytes);
        digestData.setDocument(document);

        return digestData;
    }

    protected PDSignature createSignatureDictionary(final SignatureParameters parameters) {
        PDSignature signature = new PDSignature();

        signature.setType(COSName.getPDFName("Sig"));
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);

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

    private PDRectangle createSignatureRectangle(PDDocument doc, Rectangle2D humanRect)
    {
        float x = (float) humanRect.getX();
        float y = (float) humanRect.getY();
        float width = (float) humanRect.getWidth();
        float height = (float) humanRect.getHeight();
        PDPage page = doc.getPage(0);
        PDRectangle pageRect = page.getCropBox();
        PDRectangle rect = new PDRectangle();

        // signing should be at the same position regardless of page rotation.
        switch (page.getRotation())
        {
            case 90:
                rect.setLowerLeftX(pageRect.getWidth() - y - height);
                rect.setUpperRightX(pageRect.getWidth() - y);
                rect.setLowerLeftY(x);
                rect.setUpperRightY(x + width);
                break;
            case 180:
                rect.setLowerLeftX(pageRect.getWidth() - x - width);
                rect.setUpperRightX(pageRect.getWidth() - x);
                rect.setLowerLeftY(pageRect.getHeight() - y - height);
                rect.setUpperRightY(pageRect.getHeight() - y);
                break;
            case 270:
                rect.setLowerLeftX(y);
                rect.setUpperRightX(y + height);
                rect.setLowerLeftY(pageRect.getHeight() - x - width);
                rect.setUpperRightY(pageRect.getHeight() - x);
                break;
            case 0:
            default:
                rect.setLowerLeftX(x);
                rect.setUpperRightX(x + width);
                rect.setLowerLeftY(y);
                rect.setUpperRightY(y + height);
                break;
        }

        return rect;
    }

    // create a template PDF document with empty signature and return it as a stream.
    private InputStream createVisualSignatureTemplate(
            PDDocument srcDoc,
            int pageNum,
            PDRectangle rect,
            String imageInBase64
    ) throws IOException {
        try (PDDocument doc = new PDDocument())
        {
            PDPage page = new PDPage(srcDoc.getPage(pageNum).getMediaBox());
            doc.addPage(page);
            PDAcroForm acroForm = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acroForm);
            PDSignatureField signatureField = new PDSignatureField(acroForm);
            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            List<PDField> acroFormFields = acroForm.getFields();
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);
            acroFormFields.add(signatureField);

            widget.setRectangle(rect);

            // from PDVisualSigBuilder.createHolderForm()
            PDStream stream = new PDStream(doc);
            PDFormXObject form = new PDFormXObject(stream);
            PDResources res = new PDResources();
            form.setResources(res);
            form.setFormType(1);
            PDRectangle bbox = new PDRectangle(rect.getWidth(), rect.getHeight());
            Matrix initialScale = null;
            int pageRotation = srcDoc.getPage(0).getRotation();
            switch (pageRotation)
            {
                case 90:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(1));
                    initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(), bbox.getHeight() / bbox.getWidth());
                    break;
                case 180:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(2));
                    break;
                case 270:
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(3));
                    initialScale = Matrix.getScaleInstance(bbox.getWidth() / bbox.getHeight(), bbox.getHeight() / bbox.getWidth());
                    break;
                default:
                    break;
            }
            form.setBBox(bbox);

            // from PDVisualSigBuilder.createAppearanceDictionary()
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);

            try (PDPageContentStream cs = new PDPageContentStream(doc, appearanceStream))
            {
                if (initialScale != null)
                {
                    cs.transform(initialScale);
                }

                if (imageInBase64 != null)
                {
                    byte[] image = Base64.getDecoder().decode(imageInBase64);
                    cs.saveGraphicsState();
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, image, "signature.png");

                    float imageWidth = bbox.getWidth();
                    float imageHeight = bbox.getHeight();
                    if (pageRotation == 90 || pageRotation == 270) {
                        imageWidth = bbox.getHeight();
                        imageHeight = bbox.getWidth();
                    }
                    cs.drawImage(img, 0, 0, imageWidth, imageHeight);
                    cs.restoreGraphicsState();
                }

            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

    public static boolean notEmpty(CharSequence cs) {
        return cs != null && cs.length() != 0;
    }
}
