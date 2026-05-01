-- Seed a test consumer client with ROLE_ASSESSMENT_VIEW_DELIUS for local dev testing

INSERT INTO oauth2_registered_client (id, client_id, client_id_issued_at, client_secret, client_secret_expires_at, client_name, client_authentication_methods, authorization_grant_types, redirect_uris, scopes, client_settings, token_settings, post_logout_redirect_uris)
VALUES (
  '1f745be7-ef50-4032-9f31-1779fdd55f74',
  'test-api-consumer',
  '2025-09-02 14:16:35.828906',
  '{bcrypt}$2a$10$lBwbziQlLfiCnn8Kj1PfMujEcLdsJYlYSNJvBRO638gCYTS9yN0xm',
  null,
  'test-api-consumer',
  'client_secret_basic',
  'client_credentials',
  '',
  'read',
  '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-authorization-consent":false,"settings.client.additional-data.jwtFields":"","settings.client.require-proof-key":false,"settings.client.additional-data.database-user-name":"","settings.client.additional-data.jira-number":""}',
  '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
  null
) ON CONFLICT (id) DO NOTHING;

INSERT INTO oauth2_authorization_consent (registered_client_id, principal_name, authorities)
VALUES (
  '1f745be7-ef50-4032-9f31-1779fdd55f74',
  'test-api-consumer',
  'ROLE_ASSESSMENT_VIEW_DELIUS'
) ON CONFLICT (registered_client_id, principal_name) DO NOTHING;
