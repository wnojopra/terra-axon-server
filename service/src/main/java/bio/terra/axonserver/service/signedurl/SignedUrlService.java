package bio.terra.axonserver.service.signedurl;

import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.common.iam.BearerToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for generating signed URLs for Terra cloud resources. */
@Component
public class SignedUrlService {
  private final SamService samService;

  private static final int DEFAULT_SIGNED_URL_EXPIRATION_TIME_IN_MINUTES = 60;

  @Autowired
  public SignedUrlService(SamService samService) {
    this.samService = samService;
  }

  /**
   * Generate a V4 signed URL using the Google application default credentials and pet service
   * account email.
   *
   * <p>See <a href="https://cloud.google.com/storage/docs/access-control/signed-urls">Signed
   * URLs</a> and <a
   * href="https://cloud.google.com/storage/docs/access-control/signing-urls-with-helpers#client-libraries">
   * V4 signing process with Cloud Storage tools</a>
   *
   * @param token Bearer token for the requester.
   * @param projectId The GCP project ID.
   * @param bucketName Name of the GCS bucket.
   * @param objectName Path to object in the bucket.
   * @return A signed URL giving download access for one hour.
   */
  public URL generateV4GetObjectSignedUrl(
      BearerToken token, String projectId, String bucketName, String objectName)
      throws StorageException, IOException {
    String petSaEmail = samService.getPetServiceAccount(projectId, token);
    ImpersonatedCredentials targetCredentials =
        ImpersonatedCredentials.create(
            GoogleCredentials.getApplicationDefault(),
            petSaEmail,
            null,
            CloudStorageUtils.getPetScopes(),
            300);

    Storage storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(targetCredentials)
            .build()
            .getService();

    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();
    return storage.signUrl(
        blobInfo,
        DEFAULT_SIGNED_URL_EXPIRATION_TIME_IN_MINUTES,
        TimeUnit.MINUTES,
        Storage.SignUrlOption.withV4Signature());
  }
}
