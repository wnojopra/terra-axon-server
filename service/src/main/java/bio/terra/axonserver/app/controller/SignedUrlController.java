package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.SignedUrlApi;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class SignedUrlController extends ControllerBase implements SignedUrlApi {

  private final SamService samService;
  private final WorkspaceManagerService wsmService;
  private static final int DEFAULT_SIGNED_URL_EXPIRATION_TIME_IN_MINUTES = 60;

  @Autowired
  public SignedUrlController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      SamService samService,
      WorkspaceManagerService wsmService) {
    super(bearerTokenFactory, request);
    this.samService = samService;
    this.wsmService = wsmService;
  }

  @Override
  public ResponseEntity<ApiSignedUrlReport> getSignedUrl(
      UUID workspaceId, UUID resourceId, String objectName) {
    BearerToken token = getToken();
    String accessToken = token.getToken();
    if (accessToken == null) {
      throw new ApiException("Access token is null. Try refreshing your access.");
    }
    String projectId = wsmService.getGcpContext(workspaceId, accessToken).getProjectId();
    String bucketName =
        wsmService
            .getResource(accessToken, workspaceId, resourceId)
            .getResourceAttributes()
            .getGcpGcsBucket()
            .getBucketName();
    try {
      String result = generateV4GetObjectSignedUrl(projectId, bucketName, objectName).toString();
      ApiSignedUrlReport actualResult = new ApiSignedUrlReport().signedUrl(result);
      return new ResponseEntity<>(actualResult, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }

  /**
   * Generate a V4 signed URL using the Google application default credentials and pet service
   * account email.
   *
   * <p>See <a href="https://cloud.google.com/storage/docs/access-control/signed-urls">Signed
   * URLs</a> and <a
   * href="https://cloud.google.com/storage/docs/access-control/signing-urls-with-helpers#client-libraries">V4
   * signing process with Cloud Storage tools</a>
   */
  public URL generateV4GetObjectSignedUrl(String projectId, String bucketName, String objectName)
      throws StorageException, IOException {
    String petSaEmail = samService.getPetServiceAccount(projectId, getToken());
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
