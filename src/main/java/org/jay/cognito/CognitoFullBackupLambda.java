package org.jay.cognito;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.fasterxml.jackson.databind.SerializationFeature;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class CognitoFullBackupLambda implements RequestHandler<Map<String, String>, String> {

    private final CognitoIdentityProviderClient cognito = CognitoIdentityProviderClient.create();
    private final S3Client s3 = S3Client.create();
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        String userPoolId = input.get("userPoolId");
        String bucket = input.get("bucket");
        String backupKey = "cognito-backup/" + userPoolId + "-" + Instant.now() + ".json.gz";

        try {
            Map<String, Object> backup = new LinkedHashMap<>();

            // 1. Pool Config
            backup.put("userPoolConfig", cognito.describeUserPool(r -> r.userPoolId(userPoolId)).userPool());

            // 2. App Clients
            List<UserPoolClientDescription> clients = cognito.listUserPoolClients(r -> r.userPoolId(userPoolId)).userPoolClients();
            List<Object> clientConfigs = new ArrayList<>();
            for (UserPoolClientDescription client : clients) {
                clientConfigs.add(cognito.describeUserPoolClient(r -> r.userPoolId(userPoolId).clientId(client.clientId())).userPoolClient());
            }
            backup.put("appClients", clientConfigs);

            // 3. Groups
            backup.put("groups", cognito.listGroups(r -> r.userPoolId(userPoolId)).groups());

            // 4. Resource Servers
            backup.put("resourceServers", cognito.listResourceServers(r -> r.userPoolId(userPoolId)).resourceServers());

            // 5. Identity Providers
            List<ProviderDescription> idps = cognito.listIdentityProviders(r -> r.userPoolId(userPoolId)).providers();
            List<IdentityProviderType> idpDetails = new ArrayList<>();
            for (ProviderDescription idp : idps) {
                IdentityProviderType fullIdp = cognito.describeIdentityProvider(r ->
                        r.userPoolId(userPoolId).providerName(idp.providerName())
                ).identityProvider();

                idpDetails.add(fullIdp);
            }
            backup.put("identityProviders", idpDetails);

            // 6. Users and Attributes
            List<UserType> users = new ArrayList<>();
            String token = null;
            do {
                final String currentToken = token; // ✅ effectively final
                ListUsersResponse res = cognito.listUsers(r ->
                        r.userPoolId(userPoolId)
                                .paginationToken(currentToken)
                                .limit(60)
                );
                users.addAll(res.users());
                token = res.paginationToken();
            } while (token != null);

            backup.put("users", users);

            // 7. Serialize + Compress
            byte[] jsonBytes = mapper.writeValueAsBytes(backup);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(jsonBytes);
            }

            // 8. Upload to S3
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(backupKey)
                            .contentType("application/gzip")
                            .build(),
                    RequestBody.fromBytes(out.toByteArray()));

            return "Backup successful: " + backupKey;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}