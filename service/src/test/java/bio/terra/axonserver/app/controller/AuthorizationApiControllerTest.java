//package bio.terra.axonserver.app.controller;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//
//import bio.terra.axonserver.testutils.BaseUnitTest;
//import java.util.Arrays;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.test.web.servlet.MockMvc;
//
//class AuthorizationApiControllerTest extends BaseUnitTest {
//
//  @Autowired private MockMvc mockMvc;
//
//  @Test
//  void clientID_and_clientSecret_environmentVariablesNotNull() throws Exception {
//    var result =
//        this.mockMvc
//            .perform(get("/auth/exchangeAuthorizationCode?authCode=asdasd"))
//            .andReturn()
//            .getResponse();
//    //    System.out.println();
//    // Error message is empty if client id or secret is null.
//    assertNotNull(result.getErrorMessage());
//  }
//}
