package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.AuthorizationApi;
import bio.terra.axonserver.app.configuration.AuthConfiguration;
import bio.terra.axonserver.model.ApiTokenReport;
import bio.terra.common.exception.ApiException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AuthorizationApiController implements AuthorizationApi {
  private final AuthConfiguration authConfiguration;
  private final HttpServletRequest servletRequest;

  @Autowired
  public AuthorizationApiController(
      HttpServletRequest servletRequest, AuthConfiguration authConfiguration) {
    this.servletRequest = servletRequest;
    this.authConfiguration = authConfiguration;
  }

  @Override
  public ResponseEntity<ApiTokenReport> getAuthTokens(String authCode) {
    var request =
        new GoogleAuthorizationCodeTokenRequest(
            new NetHttpTransport(),
            new GsonFactory(),
            authConfiguration.clientId(),
            authConfiguration.clientSecret(),
            authCode,
            "postmessage");
    try {
      var result = buildApiTokenResult(request.execute());
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }

  @Override
  public ResponseEntity<ApiTokenReport> getRefreshedAccessToken(String refreshToken) {
    var request =
        new GoogleRefreshTokenRequest(
            new NetHttpTransport(),
            new GsonFactory(),
            refreshToken,
            authConfiguration.clientId(),
            authConfiguration.clientSecret());
    try {
      var result = buildApiTokenResult(request.execute());
      return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (IOException e) {
      throw new ApiException(e.getMessage(), e);
    }
  }

  private ApiTokenReport buildApiTokenResult(GoogleTokenResponse response) {
    return new ApiTokenReport()
        .accessToken(response.getAccessToken())
        .expiresIn(response.getExpiresInSeconds())
        .tokenType(response.getTokenType())
        .scope(response.getScope())
        .refreshToken(response.getRefreshToken())
        .idToken(response.getIdToken());
  }
}
