package com.gdghost.upload;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/** R2 S3 호환 API. 시간 초과 3초, 재시도는 SDK 기본값 */
public class R2ObjectStorage implements ObjectStorage {

    private static final String REGION = "auto";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final URI endpoint;
    private final String bucket;
    private final AwsCredentialsIdentity identity;
    private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();
    private final S3Client s3;

    public R2ObjectStorage(URI endpoint, String bucket, String accessKeyId, String secretAccessKey) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.identity = AwsCredentialsIdentity.create(accessKeyId, secretAccessKey);
        this.s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(TIMEOUT)
                        .socketTimeout(TIMEOUT)
                        .build())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .overrideConfiguration(c -> c.apiCallTimeout(CALL_TIMEOUT))
                .build();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public URI presignPut(String objectKey, String contentType, long size, Instant signedAt, Duration ttl) {
        SdkHttpRequest request = request(SdkHttpMethod.PUT, objectKey).toBuilder()
                .putHeader("Content-Type", contentType)
                .putHeader("Content-Length", Long.toString(size))
                .build();
        return sign(request, signedAt, ttl);
    }

    @Override
    public URI presignGet(String objectKey, Instant signedAt, Duration ttl) {
        return sign(request(SdkHttpMethod.GET, objectKey), signedAt, ttl);
    }

    @Override
    public OptionalLong sizeOf(String objectKey) {
        try {
            return OptionalLong.of(s3.headObject(r -> r.bucket(bucket).key(objectKey)).contentLength());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(r -> r.bucket(bucket).key(objectKey));
    }

    @Override
    public List<String> keysModifiedBefore(Instant cutoff) {
        return s3.listObjectsV2Paginator(r -> r.bucket(bucket)).contents().stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(S3Object::key)
                .toList();
    }

    private SdkHttpRequest request(SdkHttpMethod method, String objectKey) {
        return SdkHttpRequest.builder()
                .method(method)
                .uri(endpoint.resolve("/" + bucket + "/" + objectKey))
                .build();
    }

    // SDK의 S3Presigner는 서명 시각을 정할 수 없다. 정각 맞춤을 위해 서명기를 직접 쓴다
    private URI sign(SdkHttpRequest request, Instant signedAt, Duration ttl) {
        return signer.sign(r -> r.identity(identity)
                .request(request)
                .putProperty(AwsV4HttpSigner.REGION_NAME, REGION)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "s3")
                .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
                .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, ttl)
                .putProperty(AwsV4HttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false)
                .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false)
                .putProperty(HttpSigner.SIGNING_CLOCK, Clock.fixed(signedAt, ZoneOffset.UTC)))
                .request().getUri();
    }

}
