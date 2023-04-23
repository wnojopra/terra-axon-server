package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AwsResourceApi;
import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.cloud.aws.AwsService;
import bio.terra.axonserver.service.exception.FeatureNotEnabledException;
import bio.terra.axonserver.service.features.FeatureService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.ResourceDescription;
import java.net.URL;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AwsResourceController extends ControllerBase implements AwsResourceApi {

  private final FeatureService featureService;
  private final WorkspaceManagerService wsmService;
  private final AwsService awsService;

  @Autowired
  public AwsResourceController(
      BearerTokenFactory bearerTokenFactory,
      HttpServletRequest request,
      FeatureService featureService,
      WorkspaceManagerService wsmService,
      AwsService awsService) {
    super(bearerTokenFactory, request);
    this.featureService = featureService;
    this.wsmService = wsmService;
    this.awsService = awsService;
  }

  @Override
  public ResponseEntity<ApiSignedUrlReport> getSignedConsoleUrl(UUID workspaceId, UUID resourceId) {

    if (!featureService.awsEnabled()) {
      throw new FeatureNotEnabledException("AWS Feature not enabled.");
    }

    String accessToken = getAccessToken();
    ResourceDescription resourceDescription =
        wsmService.getResource(accessToken, workspaceId, resourceId);

    AwsCredential awsCredential =
        wsmService.getAwsResourceCredential(
            accessToken,
            resourceDescription,
            wsmService.getHighestRole(accessToken, workspaceId),
            WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN);

    URL signedConsoleUrl =
        awsService.createSignedConsoleUrl(
            resourceDescription, awsCredential, AwsService.MAX_CONSOLE_SESSION_DURATION);

    ApiSignedUrlReport actualResult =
        new ApiSignedUrlReport().signedUrl(signedConsoleUrl.toString());
    return new ResponseEntity<>(actualResult, HttpStatus.OK);
  }
}
