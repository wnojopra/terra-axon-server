package bio.terra.axonserver.service.cloud.aws;

import bio.terra.cloudres.aws.console.ConsoleCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsS3StorageFolderAttributes;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sts.model.Credentials;

@Component
public class AwsService {

  public static final Integer MAX_CONSOLE_SESSION_DURATION = 43200;

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-cli").build();

  private URL getDestinationForAwsS3StorageFolder(
      String region, AwsS3StorageFolderAttributes awsS3StorageFolder) {
    try {
      String prefix = String.format("%s/", awsS3StorageFolder.getPrefix());
      return new URIBuilder()
          .setScheme("https")
          .setHost("s3.console.aws.amazon.com")
          .setPath(String.format("s3/buckets/%s", awsS3StorageFolder.getBucketName()))
          .addParameter("prefix", prefix)
          .addParameter("region", region)
          .build()
          .toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private URL getDestinationFromResourceDescription(ResourceDescription resourceDescription) {
    ResourceMetadata resourceMetadata = resourceDescription.getMetadata();
    return switch (resourceMetadata.getResourceType()) {
      case AWS_S3_STORAGE_FOLDER -> getDestinationForAwsS3StorageFolder(
          resourceMetadata.getControlledResourceMetadata().getRegion(),
          resourceDescription.getResourceAttributes().getAwsS3StorageFolder());
      default -> throw new RuntimeException("Wrong type");
    };
  }

  @VisibleForTesting
  public URL createSignedConsoleUrl(
      ConsoleCow consoleCow,
      ResourceDescription resourceDescription,
      AwsCredential awsCredential,
      Integer duration) {
    try {
      Credentials credentials =
          Credentials.builder()
              .accessKeyId(awsCredential.getAccessKeyId())
              .secretAccessKey(awsCredential.getSecretAccessKey())
              .sessionToken(awsCredential.getSessionToken())
              .build();
      return consoleCow.createSignedUrl(
          credentials, duration, getDestinationFromResourceDescription(resourceDescription));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public URL createSignedConsoleUrl(
      ResourceDescription resourceDescription, AwsCredential awsCredential, Integer duration) {
    ConsoleCow consoleCow = ConsoleCow.create(clientConfig);
    return createSignedConsoleUrl(consoleCow, resourceDescription, awsCredential, duration);
  }
}
