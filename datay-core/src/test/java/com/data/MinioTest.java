package com.data;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.data.job.ETLFlowTask;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MinioTest {

    @Test
    @Timeout(6000000)
    public void testMySQLBinlogInput() throws Exception {
        MinioClient minioClient =
                MinioClient.builder()
                        .endpoint("http://127.0.0.1:9001")
                        .credentials("minio", "Tv9uxm#OALGo")
                        .build();

        List<Bucket> buckets = minioClient.listBuckets();
        for (Bucket bucket : buckets) {
            System.out.println(bucket.name());
        }
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket("apps").prefix("test/").build());
        for (Result<Item> result : results) {
            System.out.println(result.get().objectName());
        }
    }

    @Test
    @Timeout(6000000)
    public void testS3() throws Exception {
        AmazonS3 build = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://127.0.0.1:9001", Regions.fromName("cn-north-1").getName()))
                .withCredentials(
                        new AWSStaticCredentialsProvider(new BasicAWSCredentials("minio", "Tv9uxm#OALGo")))
                .build();

        try {
            ObjectListing listObjects = build.listObjects("apps", "test/");

            for (S3ObjectSummary objectSummary : listObjects.getObjectSummaries()) {
                System.out.println(objectSummary.toString());
            }
            // 检查是否有对象
            if (listObjects.getObjectSummaries().isEmpty()) {
                System.out.println("Directory exists but is empty");
            }

        } catch (Exception e) {
            System.err.println("Error listing objects: " + e.getMessage());
        }
    }
}
