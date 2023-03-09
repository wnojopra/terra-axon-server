package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GetFileApi;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

/**
 * Controller for the GetFileApi. This controller is responsible for handling all incoming requests
 * for files.
 */
@Controller
public class GetFileController extends ControllerBase implements GetFileApi {

  private final FileService fileService;

  @Autowired
  public GetFileController(
      BearerTokenFactory bearerTokenFactory, HttpServletRequest request, FileService fileService) {
    super(bearerTokenFactory, request);
    this.fileService = fileService;
  }

  /**
   * Get a file from a workspace. This method is responsible for handling all requests to the
   * /api/workspaces/{workspaceId}/files/{resourceId} endpoint.
   *
   * @param workspaceId - UUID of the workspace to retrieve the file from
   * @param resourceId - UUID of the file to retrieve
   * @param convertTo - Optional parameter to convert the file to a different format
   * @return - A ResponseEntity containing the file
   */
  @Override
  public ResponseEntity<byte[]> getFile(
      UUID workspaceId, UUID resourceId, @Nullable String convertTo) {

    BearerToken token = getToken();

    HttpRange byteRange = getByteRange();

    byte[] resourceObj =
        fileService.getFile(token, workspaceId, resourceId, null, convertTo, byteRange);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }

  /**
   * Get a file from a bucket. This method is responsible for handling all requests to the
   * /api/workspaces/{workspaceId}/files/{resourceId}/{objectPath} endpoint.
   *
   * @param workspaceId - UUID of the workspace to retrieve the file from
   * @param resourceId - UUID of the bucket to retrieve the file from
   * @param objectPath - Path to the file in the bucket
   * @param convertTo - Optional parameter to convert the file to a different format
   * @return - A ResponseEntity containing the file
   */
  @Override
  public ResponseEntity<byte[]> getFileInBucket(
      UUID workspaceId, UUID resourceId, String objectPath, @Nullable String convertTo) {

    BearerToken token = getToken();

    HttpRange byteRange = getByteRange();

    byte[] resourceObj =
        fileService.getFile(token, workspaceId, resourceId, objectPath, convertTo, byteRange);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }

  private HttpRange getByteRange() {
    String rangeHeader = getServletRequest().getHeader("Range");
    if (rangeHeader == null) {
      return null;
    }

    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
    if (ranges.size() != 1) {
      throw new BadRequestException("Only one range is supported");
    }
    return ranges.get(0);
  }
}
