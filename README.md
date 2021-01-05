# PDF detached PAdES remote signatures with digest and CAdES

This application helps you create eIDAS PAdES digital signatures according to specification ETSI EN 319 142-1 in externally in detached mode.
This means that there is no need to transfer the whole PDF file to the place/device/application that will generate PKCS7 or CAdES (ETSI.CAdES.detached) signature.

There are 2 API calls.

- **POST /api/detached-pades/prepare** - This call will get json with 1 field fileContent which holds the PDF content in base64 encoding.
  Return value is signatureTime and SHA256 digest in hex and base64. Base64 digest is needed for creating CAdES signature using eID Easy /api/signatures/prepare-files-for-signing API call and signatureTime is needed later when completing the digital signature.

- **POST /api/detached-pades/complete** - This call will get 3 json fields. signatureTime that was received when preparing, signatureValue that is CAdES signature created separately and fileContent that is the original file

fileContent has to be sent in both calls because this is stateless service and does not save any data.

## Docker

This application can be found from Docker as well.
Following will remove any existing instance and install new one that will listen to localhost port 8082. 
```
sudo docker stop eideasy_detached_pades -t 0
sudo docker rm eideasy_detached_pades
sudo docker run -d -p 127.0.0.1:8082:8084 --name=eideasy_detached_pades --restart always --log-driver syslog --log-opt tag="{{.Name}}/{{.ID}}" eideasy/pades-external-digital-signatures
```
## More info

Postman documentation for these API calls can be found from https://documenter.getpostman.com/view/3869493/Szf6WoG1#bf3a86b8-3937-4b56-9579-a0e4751d2f18

There is also free service for testing purposes running at https://detached-pdf.eideasy.com

If you do not have any way to create ETSI.CAdES.detached digital signatures then you can use eID Easy service. Start with API call "/api/signatures/prepare-files-for-signing" and send the digest as the PDF file content. More information at https://documenter.getpostman.com/view/3869493/Szf6WoG1#74939bae-2c9b-459c-9f0b-8070d2bd32f7

