package com.booki.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

/**
 * Storage on any S3-compatible service ({@code booki.storage.driver=s3}). The
 * same client talks to MinIO, Cloudflare R2, Google Cloud Storage (its S3 XML
 * API + HMAC keys) and AWS S3 — only {@code booki.storage.s3.endpoint} changes.
 *
 * <p>Objects are fetched fully into memory and handed back as a
 * {@link ByteArrayResource}. BooKI's files are whole PDFs read start to finish,
 * capped at 50 MB by the multipart limit, so this is fine; a presigned-URL
 * redirect would be the move if that stops being true.
 */
@Component
@ConditionalOnProperty(name = "booki.storage.driver", havingValue = "s3")
public class S3StorageAdapter implements StorageAdapter {

    private final S3Client s3;
    private final String bucket;

    public S3StorageAdapter(
            @Value("${booki.storage.s3.endpoint:}") String endpoint,
            @Value("${booki.storage.s3.region:us-east-1}") String region,
            @Value("${booki.storage.s3.bucket}") String bucket,
            @Value("${booki.storage.s3.access-key}") String accessKey,
            @Value("${booki.storage.s3.secret-key}") String secretKey,
            @Value("${booki.storage.s3.path-style:true}") boolean pathStyle) {

        this.bucket = bucket;

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .forcePathStyle(pathStyle)
                // AWS SDK v2 sends CRC32 request checksums by default; GCS's
                // S3-compatible API (and some other non-AWS stores) reject them.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        this.s3 = builder.build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new StorageException("Failed to write " + key, e);
        }
    }

    @Override
    public Resource get(String key) {
        try {
            byte[] bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            return new ByteArrayResource(bytes);
        } catch (NoSuchKeyException e) {
            throw new StorageException("No stored file for key " + key, e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    @Override
    public void ping() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            throw new StorageException("Bucket unreachable: " + bucket, e);
        }
    }
}
