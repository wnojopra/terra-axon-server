package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.SignedUrlApi;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.signedurl.SignedUrlService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
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

  private final SignedUrlService signedUrlService;
  private final WorkspaceManagerService wsmService;

  @Autowired
  public SignedUrlController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      SignedUrlService signedUrlService,
      WorkspaceManagerService wsmService) {
    super(bearerTokenFactory, request);
    this.signedUrlService = signedUrlService;
    this.wsmService = wsmService;
  }

  @Override
  public ResponseEntity<ApiSignedUrlReport> getSignedUrl(
      UUID workspaceId, UUID resourceId, String objectName) {
    BearerToken token = getToken();
    String accessToken = token.getToken();
    if (accessToken == null) {
      throw new BadRequestException("Access token is null. Try refreshing your access.");
    }
    String projectId = wsmService.getGcpContext(workspaceId, accessToken).getProjectId();
    String bucketName =
        wsmService
            .getResource(accessToken, workspaceId, resourceId)
            .getResourceAttributes()
            .getGcpGcsBucket()
            .getBucketName();
    try {
      String result = signedUrlService.generateV4GetObjectSignedUrl(token, projectId, bucketName, objectName).toString();
      ApiSignedUrlReport actualResult = new ApiSignedUrlReport().signedUrl(result);
      return new ResponseEntity<>(actualResult, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }
}
