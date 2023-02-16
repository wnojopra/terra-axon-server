package bio.terra.axonserver.utils;

import bio.terra.common.exception.BadRequestException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class PathValidationUtils {
  public static String decodePath(String path) throws BadRequestException {
    try {
      return URLDecoder.decode(path, StandardCharsets.UTF_8.toString());
    } catch (Exception e) {
      System.out.println("Exception: " + e.getMessage());
      throw new BadRequestException("Bad object path: " + path);
    }
  }
}
