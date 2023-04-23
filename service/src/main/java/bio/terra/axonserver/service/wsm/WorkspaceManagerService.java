package bio.terra.axonserver.service.wsm;

import bio.terra.axonserver.app.configuration.WsmConfiguration;
import bio.terra.workspace.api.ControlledAwsResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsCredentialAccessScope;
import bio.terra.workspace.model.GcpContext;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.WorkspaceDescription;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for interacting with the Terra Workspace Manager client. */
@Component
public class WorkspaceManagerService {

  public static final int AWS_RESOURCE_CREDENTIAL_DURATION_MIN = 900;
  public static final int AWS_RESOURCE_CREDENTIAL_DURATION_MAX = 3600;

  private final WsmConfiguration wsmConfig;

  @Autowired
  public WorkspaceManagerService(WsmConfiguration wsmConfig) {
    this.wsmConfig = wsmConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(wsmConfig.basePath());
  }

  private WorkspaceDescription getWorkspace(
      String accessToken, UUID workspaceId, @Nullable IamRole highestRole) {
    try {
      return new WorkspaceApi(getApiClient(accessToken)).getWorkspace(workspaceId, null);
    } catch (ApiException e) {
      throw new NotFoundException("Unable to access workspace " + workspaceId + ".");
    }
  }

  /**
   * Get a resource from a workspace.
   *
   * @param accessToken user access token
   * @param workspaceId terra workspace id
   * @param resourceId terra resource id
   * @return WSM resource description
   * @throws NotFoundException if workspace or resource does not exist
   */
  public ResourceDescription getResource(String accessToken, UUID workspaceId, UUID resourceId) {
    try {
      return new ResourceApi(getApiClient(accessToken)).getResource(workspaceId, resourceId);
    } catch (ApiException apiException) {
      throw new NotFoundException("Unable to access workspace or resource.");
    }
  }

  /**
   * Get the GCP context for a workspace.
   *
   * @param workspaceId terra workspace id
   * @param accessToken user access token
   * @return WSM GCP context
   * @throws NotFoundException if workspace does not exist or user does not have access to workspace
   */
  public GcpContext getGcpContext(UUID workspaceId, String accessToken) {
    return getWorkspace(accessToken, workspaceId, null).getGcpContext();
  }

  public IamRole getHighestRole(String accessToken, UUID workspaceId) {
    return getWorkspace(accessToken, workspaceId, null).getHighestRole();
  }

  private AwsCredential getAwsStorageFolderCredential(
      String accessToken,
      UUID workspaceId,
      UUID resourceId,
      AwsCredentialAccessScope accessScope,
      Integer duration) {
    try {
      return new ControlledAwsResourceApi(getApiClient(accessToken))
          .getAwsS3StorageFolderCredential(workspaceId, resourceId, accessScope, duration);
    } catch (ApiException e) {
      throw new NotFoundException("Unable to access workspace or resource.");
    }
  }

  private AwsCredentialAccessScope getAccessScope(IamRole highestRole) {
    return highestRole.equals(IamRole.READER)
        ? AwsCredentialAccessScope.READ_ONLY
        : AwsCredentialAccessScope.WRITE_READ;
  }

  public AwsCredential getAwsResourceCredential(
      String accessToken,
      ResourceDescription resourceDescription,
      IamRole highestRole,
      Integer duration) {
    if (highestRole == null || highestRole.equals(IamRole.DISCOVERER)) {
      throw new RuntimeException("Not authorized");
    }

    ResourceMetadata resourceMetadata = resourceDescription.getMetadata();
    return switch (resourceMetadata.getResourceType()) {
      case AWS_S3_STORAGE_FOLDER -> getAwsStorageFolderCredential(
          accessToken,
          resourceMetadata.getWorkspaceId(),
          resourceMetadata.getResourceId(),
          getAccessScope(highestRole),
          duration);
      default -> throw new RuntimeException("Wrong type");
    };
  }
}
