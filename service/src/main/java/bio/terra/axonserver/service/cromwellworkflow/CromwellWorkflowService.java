package bio.terra.axonserver.service.cromwellworkflow;

import bio.terra.axonserver.app.configuration.CromwellConfiguration;
import bio.terra.axonserver.model.ApiWorkflowMetadataResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResponse;
import bio.terra.axonserver.model.ApiWorkflowQueryResult;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.BearerToken;
import bio.terra.cromwell.api.WorkflowsApi;
import bio.terra.cromwell.client.ApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.client.model.CromwellApiLabelsResponse;
import io.swagger.client.model.CromwellApiWorkflowIdAndStatus;
import io.swagger.client.model.CromwellApiWorkflowMetadataResponse;
import io.swagger.client.model.CromwellApiWorkflowQueryResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper service for calling cromwell. When applicable, the precondition is:
 *
 * <p>- the user has access to the workspace
 *
 * <p>- the requested workflow has a label matching to the workspace id.
 *
 * <p>This service appends/overrides a label with a workspace id onto all submitted workflows.
 */
@Component
public class CromwellWorkflowService {
  private final CromwellConfiguration cromwellConfig;
  private final FileService fileService;
  private final WorkspaceManagerService wsmService;
  private final SamService samService;

  private final String WORKSPACE_ID_LABEL_KEY = "terra-workspace-id";
  private final String CROMWELL_CLIENT_API_VERSION = "v1";

  @Autowired
  public CromwellWorkflowService(
      CromwellConfiguration cromwellConfig,
      FileService fileService,
      WorkspaceManagerService wsmService,
      SamService samService) {
    this.cromwellConfig = cromwellConfig;
    this.fileService = fileService;
    this.wsmService = wsmService;
    this.samService = samService;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(cromwellConfig.basePath());
  }

  public CromwellApiWorkflowIdAndStatus getStatus(UUID workflowId)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .status(CROMWELL_CLIENT_API_VERSION, workflowId.toString());
  }

  /**
   * Get the metadata for a given workflow.
   *
   * @param workflowId requested workflow.
   * @param includeKey filters metadata to only return fields with names which begins with this
   *     value.
   * @param excludeKey filters metadata to not return any field with a name which begins with this
   *     value
   * @param expandSubWorkflows metadata for sub workflows will be fetched and inserted automatically
   *     in the metadata response.
   * @return metadata response with applied queries.
   * @throws bio.terra.cromwell.client.ApiException Exception thrown by Cromwell client.
   */
  public CromwellApiWorkflowMetadataResponse getMetadata(
      UUID workflowId,
      @Nullable List<String> includeKey,
      @Nullable List<String> excludeKey,
      @Nullable Boolean expandSubWorkflows)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .metadata(
            CROMWELL_CLIENT_API_VERSION,
            workflowId.toString(),
            includeKey,
            excludeKey,
            expandSubWorkflows);
  }

  /**
   * Queries workflows based on user-supplied criteria, and additionally requires the corresponding
   * workspace id label (e.g., "{WORKSPACE_ID_LABEL_KEY}:{workspaceId}").
   */
  public CromwellApiWorkflowQueryResponse getQuery(
      UUID workspaceId,
      @Nullable Date submission,
      @Nullable Date start,
      @Nullable Date end,
      @Nullable List<String> status,
      @Nullable List<String> name,
      @Nullable List<String> id,
      @Nullable List<String> label,
      @Nullable List<String> labelor,
      @Nullable List<String> excludeLabelAnd,
      @Nullable List<String> excludeLabelOr,
      @Nullable List<String> additionalQueryResultFields,
      @Nullable Boolean includeSubworkflows)
      throws bio.terra.cromwell.client.ApiException {
    if (label == null) {
      label = new ArrayList<>();
    }
    // Restrict the subset to only workflows with the corresponding workspace id label.
    label.add("%s:%s".formatted(WORKSPACE_ID_LABEL_KEY, workspaceId));
    return new WorkflowsApi(getApiClient())
        .queryGet(
            CROMWELL_CLIENT_API_VERSION,
            submission,
            start,
            end,
            status,
            name,
            id,
            label,
            labelor,
            excludeLabelAnd,
            excludeLabelOr,
            additionalQueryResultFields,
            includeSubworkflows);
  }

  /**
   * Submits a single workflow to Cromwell. This appends (or overrides) the workspace id label, and
   * parts of the options configuration. Files are retrieved from GCS, and stored on the disk
   * temporarily before calling Cromwell.
   *
   * @param workspaceId workspace where the workflow will reside
   * @param workflowGcsUri URI pointing to the workflow source: a GCS object that is a WDL file.
   * @param workflowUrl URL which points to the workflow source.
   * @param workflowOnHold Put workflow on hold upon submission. By default, it is taken as false.
   * @param workflowInputs JSON string of inputs.
   * @param workflowOptions Object containing the options.
   * @param workflowType Workflow language for the submitted file (i.e., WDL)
   * @param workflowTypeVersion Version for the workflow language (draft-2 or 1.0).
   * @param labels JSON string of labels.
   * @param workflowDependenciesGcsUri URI pointing to the workflow dependencies: a GCS object that
   *     is a ZIP file.
   * @param requestedWorkflowId An ID to ascribe to this workflow. If not supplied, then a random ID
   *     will be generated.
   * @param token Bearer token.
   * @return The workflow ID, and status of submission.
   * @throws bio.terra.cromwell.client.ApiException Exception thrown by Cromwell client.
   * @throws IOException Exception thrown during file input/output.
   */
  public CromwellApiWorkflowIdAndStatus submitWorkflow(
      UUID workspaceId,
      String workflowGcsUri,
      String workflowUrl,
      Boolean workflowOnHold,
      String workflowInputs,
      Map<String, Object> workflowOptions,
      String workflowType,
      String workflowTypeVersion,
      String labels,
      String workflowDependenciesGcsUri,
      UUID requestedWorkflowId,
      BearerToken token)
      throws bio.terra.cromwell.client.ApiException, IOException {

    if (workflowGcsUri == null && workflowUrl == null) {
      throw new ApiException("workflowGcsUri or workflowUrl needs to be provided.");
    }

    // Create temp files from JSON of: workflowInputs, workflowOptions, labels, source,
    // and dependencies.
    // - labels and options will be modified before being sent to Cromwell.

    File tempInputsFile = null;
    if (workflowInputs != null) {
      tempInputsFile = File.createTempFile("workflow-label-", "-terra");
      try (OutputStream out = new FileOutputStream(tempInputsFile)) {
        out.write(workflowInputs.getBytes(StandardCharsets.UTF_8));
      }
    }

    File tempOptionsFile = File.createTempFile("workflow-options-", "-terra");
    // Adjoin preset options for the options file.
    // Place the project ID + compute SA into the options.
    String projectId = wsmService.getGcpContext(workspaceId, token.getToken()).getProjectId();
    workflowOptions.put("google_project", projectId);
    workflowOptions.put(
        "google_compute_service_account", samService.getPetServiceAccount(projectId, token));
    workflowOptions.put(
        "default_runtime_attributes", new AbstractMap.SimpleEntry("docker", "debian:stable-slim"));

    ObjectMapper mapper = new ObjectMapper();
    try (OutputStream out = new FileOutputStream(tempOptionsFile)) {
      out.write(mapper.writeValueAsString(workflowOptions).getBytes(StandardCharsets.UTF_8));
    }

    File tempLabelsFile = File.createTempFile("workflow-labels-", "-terra");
    // Adjoin the workspace-id label to the workflow.
    JSONObject jsonLabels = labels == null ? new JSONObject() : new JSONObject(labels);
    jsonLabels.put(WORKSPACE_ID_LABEL_KEY, workspaceId);
    labels = jsonLabels.toString();
    try (OutputStream out = new FileOutputStream(tempLabelsFile)) {
      out.write(labels.getBytes(StandardCharsets.UTF_8));
    }

    File tempWorkflowSourceFile = null;
    if (workflowGcsUri != null) {
      InputStream inputStream =
          fileService.getFile(token, workspaceId, workflowGcsUri, /*convertTo=*/ null);
      tempWorkflowSourceFile = createTempFileFromInputStream(inputStream, "workflow-source-");
    }

    File tempWorkflowDependenciesFile = null;
    if (workflowDependenciesGcsUri != null) {
      InputStream inputStream =
          fileService.getFile(token, workspaceId, workflowDependenciesGcsUri, /*convertTo=*/ null);
      tempWorkflowDependenciesFile =
          createTempFileFromInputStream(inputStream, "workflow-dependencies-");
    }

    // TODO (PF-2650): Write inputs.json + options.json to jes_gcs_root (to leave artifacts in the
    // bucket). This is not required, but it's useful for logging.

    return new WorkflowsApi(getApiClient())
        .submit(
            CROMWELL_CLIENT_API_VERSION,
            tempWorkflowSourceFile,
            workflowUrl,
            workflowOnHold,
            tempInputsFile,
            /*workflowInputs_2=*/ null,
            /*workflowInputs_3=*/ null,
            /*workflowInputs_4=*/ null,
            /*workflowInputs_5=*/ null,
            tempOptionsFile,
            workflowType,
            /*workflowRoot=*/ null,
            workflowTypeVersion,
            tempLabelsFile,
            tempWorkflowDependenciesFile,
            requestedWorkflowId != null ? requestedWorkflowId.toString() : null);
  }

  private File createTempFileFromInputStream(InputStream inputStream, String tempFilePrefix)
      throws IOException {
    File tempFile = File.createTempFile(tempFilePrefix, "-terra");
    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return tempFile;
  }

  /** Retrieve the labels of a workflow. */
  public CromwellApiLabelsResponse getLabels(UUID workflowId)
      throws bio.terra.cromwell.client.ApiException {
    return new WorkflowsApi(getApiClient())
        .labels(CROMWELL_CLIENT_API_VERSION, workflowId.toString());
  }

  /**
   * Checks if the workflow has the required workspace id label (e.g.,
   * "terra-workspace-id:workspaceId").
   */
  public void validateWorkflowLabelMatchesWorkspaceId(UUID workflowId, UUID workspaceId)
      throws bio.terra.cromwell.client.ApiException {
    Map<String, String> labels = getLabels(workflowId).getLabels();
    if (labels.get(WORKSPACE_ID_LABEL_KEY) == null
        || !labels.get(WORKSPACE_ID_LABEL_KEY).equals(workspaceId.toString())) {
      throw new ApiException(
          "Able to access workspace %s, but cannot access workflow %s"
              .formatted(workspaceId, workflowId));
    }
  }

  public ApiWorkflowQueryResponse toApiQueryResponse(
      CromwellApiWorkflowQueryResponse workflowQuery) {
    List<ApiWorkflowQueryResult> results =
        workflowQuery.getResults().stream()
            .map(
                r ->
                    new ApiWorkflowQueryResult()
                        .id(r.getId())
                        .name(r.getName())
                        .status(r.getStatus())
                        .submission(r.getSubmission())
                        .start(r.getStart())
                        .end(r.getEnd()))
            .toList();

    return new ApiWorkflowQueryResponse()
        .results(results)
        .totalResultsCount(workflowQuery.getTotalResultsCount());
  }

  public ApiWorkflowMetadataResponse toApiMetadataResponse(
      CromwellApiWorkflowMetadataResponse metadataResponse) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(metadataResponse, ApiWorkflowMetadataResponse.class);
  }
}
