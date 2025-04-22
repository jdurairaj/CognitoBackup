package org.jay.cognito;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
public class CognitoFullRestoreLambda implements RequestHandler<Map<String, String>, String> {

    private final CognitoIdentityProviderClient cognito = CognitoIdentityProviderClient.create();
    private final S3Client s3 = S3Client.create();
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        String bucket = input.get("bucket");
        String key = input.get("key"); // S3 object key (e.g., cognito-backup/...)
        String tempPassword = input.getOrDefault("tempPassword", "Temp1234!");

        try (InputStream s3In = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
             GZIPInputStream gzip = new GZIPInputStream(s3In)) {

            Map<String, Object> data = mapper.readValue(gzip, Map.class);

            // 1. Recreate User Pool
            Map<String, Object> poolConfig = (Map<String, Object>) data.get("userPoolConfig");
            CreateUserPoolRequest createPoolRequest = CreateUserPoolRequest.builder()
                    .poolName(poolConfig.get("Name").toString() + "-restored")
                    .build(); // Add more detailed attributes here if needed
            CreateUserPoolResponse poolResponse = cognito.createUserPool(createPoolRequest);
            String newUserPoolId = poolResponse.userPool().id();
            context.getLogger().log("Created new pool: " + newUserPoolId);

            // 2. Recreate App Clients
            List<Map<String, Object>> appClients = (List<Map<String, Object>>) data.get("appClients");
            for (Map<String, Object> client : appClients) {
                cognito.createUserPoolClient(r -> r
                        .userPoolId(newUserPoolId)
                        .clientName(client.get("ClientName").toString())
                        .generateSecret(false)
                );
            }

            // 3. Recreate Groups
            List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("groups");
            for (Map<String, Object> group : groups) {
                cognito.createGroup(r -> r
                        .userPoolId(newUserPoolId)
                        .groupName(group.get("GroupName").toString())
                        .description((String) group.getOrDefault("Description", null))
                );
            }

            // 4. Recreate Resource Servers
            List<Map<String, Object>> resourceServers = (List<Map<String, Object>>) data.get("resourceServers");
            for (Map<String, Object> rs : resourceServers) {
                cognito.createResourceServer(r -> r
                        .userPoolId(newUserPoolId)
                        .identifier(rs.get("Identifier").toString())
                        .name(rs.get("Name").toString())
                );
            }

            // 5. Recreate Identity Providers (warning: secrets not backed up!)
            List<Map<String, Object>> idps = (List<Map<String, Object>>) data.get("identityProviders");
            for (Map<String, Object> idp : idps) {
                cognito.createIdentityProvider(r -> r
                        .userPoolId(newUserPoolId)
                        .providerName(idp.get("ProviderName").toString())
                        .providerType(idp.get("ProviderType").toString())  // Directly using string value
                        .providerDetails((Map<String, String>) idp.get("ProviderDetails"))
                        .attributeMapping((Map<String, String>) idp.getOrDefault("AttributeMapping", new HashMap<>()))
                );
            }

            // 6. Recreate Users (temp password)
            List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
            for (Map<String, Object> user : users) {
                String username = user.get("Username").toString();
                List<AttributeType> attrs = ((List<Map<String, String>>) user.get("Attributes")).stream()
                        .map(attr -> AttributeType.builder().name(attr.get("Name")).value(attr.get("Value")).build())
                        .collect(Collectors.toList());

                cognito.adminCreateUser(r -> r
                        .userPoolId(newUserPoolId)
                        .username(username)
                        .temporaryPassword(tempPassword)
                        .userAttributes(attrs)
                        .messageAction(MessageActionType.SUPPRESS)
                );
            }

            return "Restore completed. New Pool ID: " + newUserPoolId;

        } catch (Exception e) {
            context.getLogger().log("Restore failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}