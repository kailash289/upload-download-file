package com.example;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
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

import java.io.*;


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
    public Uni<String> uploadFile(@FormParam("fileName") InputStream file1)
    {
        return Uni.createFrom().item(() -> {
            String passphrase = "";
            System.setProperty("OCI_JAVASDK_JERSEY_CLIENT_DEFAULT_CONNECTOR_ENABLED","true");

            AuthenticationDetailsProvider authBuilder =
                    SimpleAuthenticationDetailsProvider.builder()
                            .userId(userId)
                            .fingerprint(fingerprint)
                            .tenantId(tenantId)
                            .privateKeySupplier(() -> {
                                File file = new  File(privateKeyPath);
                                try {
                                    return new FileInputStream(file);
                                } catch (FileNotFoundException e) {
                                    return getClass().getResourceAsStream(privateKeyPath);
                                }
                            })
                            .passPhrase(passphrase)
                            .region(Region.fromRegionId(region))
                            .build();

            ClientConfiguration clientConfig
                    = ClientConfiguration.builder()
                    .connectionTimeoutMillis(3000)
                    .readTimeoutMillis(60000)
                    .build();
            ObjectStorageClient client = new ObjectStorageClient(authBuilder, clientConfig);

            InputStream f = file1;

            String objectName = "examples2.txt";

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .namespaceName(namespace)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .putObjectBody(f)
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
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> DownloadFile(@PathParam("fileName") String fileName)
    {

            return Uni.createFrom().item(() -> {
                String passphrase = "";
                System.setProperty("OCI_JAVASDK_JERSEY_CLIENT_DEFAULT_CONNECTOR_ENABLED","true");

                AuthenticationDetailsProvider authBuilder =
                        SimpleAuthenticationDetailsProvider.builder()
                                .userId(userId)
                                .fingerprint(fingerprint)
                                .tenantId(tenantId)
                                .privateKeySupplier(() -> {
                                    File file = new  File(privateKeyPath);
                                    try {
                                        return new FileInputStream(file);
                                    } catch (FileNotFoundException e) {
                                        return getClass().getResourceAsStream(privateKeyPath);
                                    }
                                })
                                .passPhrase(passphrase)
                                .region(Region.fromRegionId(region))
                                .build();

                ClientConfiguration clientConfig
                        = ClientConfiguration.builder()
                        .connectionTimeoutMillis(3000)
                        .readTimeoutMillis(60000)
                        .build();
                ObjectStorageClient client = new ObjectStorageClient(authBuilder, clientConfig);
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucketName(bucketName)
                        .namespaceName(namespace)
                        .objectName(fileName)
                        .build();


                GetObjectResponse response = client.getObject(request);
                InputStream fileInputStream = response.getInputStream();
                Response.ResponseBuilder responseBuilder = Response.ok(fileInputStream);
                responseBuilder.header("Content-Disposition", "attachment; fileName=examples.txt");

                return responseBuilder.build();
            }).onFailure().recoverWithItem(e -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("File download failed: " + e.getMessage()).build());
    }
}


