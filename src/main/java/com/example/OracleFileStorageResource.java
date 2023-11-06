package com.example;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.*;
import java.nio.file.Files;


@Path("/files")
@Produces({MediaType.APPLICATION_JSON})
@Consumes(MediaType.MULTIPART_FORM_DATA)
@ApplicationScoped
public class OracleFileStorageResource{

    @ConfigProperty(name="file.namespace")
    String namespace;
    @ConfigProperty(name = "file.bucketName")
    String bucketName;
    @ConfigProperty(name = "file.userId")
    String userId;
    @ConfigProperty(name = "file.fingerprint")
    String fingerprint;
    @ConfigProperty(name = "file.tenantId")
    String tenantId;
    @ConfigProperty(name = "file.region")
    String region;
    @ConfigProperty(name = "file.privateKeyPath")
    String privateKeyPath;


    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<String> uploadFile(@RestForm("file") FileUpload file)
    {



        String passphrase = "";
        return Uni.createFrom().item(() -> {

            System.setProperty("OCI_JAVASDK_JERSEY_CLIENT_DEFAULT_CONNECTOR_ENABLED","true");
            //ObjectStorageClient  client = new ObjectStorageClient(getAuthenticationDetailsProvider(passphrase), getClientConfiguration());
            ObjectStorage client = ObjectStorageClient.builder().configuration(getClientConfiguration()).build(getAuthenticationDetailsProvider(passphrase));

            byte[] fileBytes;
            try {
                fileBytes = Files.readAllBytes(file.filePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            InputStream inputStream = new ByteArrayInputStream(fileBytes);

            String objectName = file.fileName();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .namespaceName(namespace)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .putObjectBody(inputStream)
                    .build();

            PutObjectResponse response = client.putObject(request);

            return "File uploaded successfully. ETag: " + response.getETag();
        }).onFailure().recoverWithItem(e -> {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        });
    }



    @GET
    @Path("/download/{fileName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> DownloadFile(@PathParam("fileName") String fileName)
    {
            String passphrase = "";
            return Uni.createFrom().item(() -> {
                System.setProperty("OCI_JAVASDK_JERSEY_CLIENT_DEFAULT_CONNECTOR_ENABLED","true");
                ObjectStorage client = ObjectStorageClient.builder().configuration(getClientConfiguration()).build(getAuthenticationDetailsProvider(passphrase));
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucketName(bucketName)
                        .namespaceName(namespace)
                        .objectName(fileName)
                        .build();

                GetObjectResponse response = client.getObject(request);
                InputStream fileInputStream = response.getInputStream();
                Response.ResponseBuilder responseBuilder = Response.ok(fileInputStream);

                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                return responseBuilder.build();
            }).onFailure().recoverWithItem(e -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("File Download failed").build());
    }

    private AuthenticationDetailsProvider getAuthenticationDetailsProvider(String passphrase) {
        AuthenticationDetailsProvider authBuilder =
                SimpleAuthenticationDetailsProvider.builder()
                        .userId(userId)
                        .fingerprint(fingerprint)
                        .tenantId(tenantId)
                        .privateKeySupplier(() -> {
                            File f = new  File(privateKeyPath);
                            try {
                                return new FileInputStream(f);
                            } catch (FileNotFoundException e) {
                                return getClass().getResourceAsStream(privateKeyPath);
                            }
                        })
                        .passPhrase(passphrase)
                        .region(Region.fromRegionId(region))
                        .build();
        return authBuilder;
    }

    private static ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfig
                = ClientConfiguration.builder()
                .connectionTimeoutMillis(3000)
                .readTimeoutMillis(60000)
                .build();
        return clientConfig;
    }


}


