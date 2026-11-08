package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.config.CloudinaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudinaryServiceTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.cloudinary.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CloudinaryService service = new CloudinaryService(builder.build(), new CloudinaryProperties("test", "test", "test"));

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "image.webp", "image/webp", new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'});
    }

    @Test void uploadReturnsSecureUrlAndSignsRequest() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test/image/upload"))
                .andRespond(request -> {
                    String body = request.getBody().toString();
                    var id = Pattern.compile("sekkinlk/products/[0-9a-f-]{36}").matcher(body);
                    assertTrue(id.find());
                    assertTrue(body.contains("name=\"signature\""));
                    assertFalse(body.contains("api_secret"));
                    String timestamp = field(body, "timestamp");
                    String canonical = "allowed_formats=jpg,jpeg,png,webp,gif&overwrite=false&public_id="
                            + id.group() + "&timestamp=" + timestamp + "test";
                    try {
                        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
                        assertEquals(expected, field(body, "signature"));
                    } catch (java.security.NoSuchAlgorithmException ex) {
                        throw new AssertionError(ex);
                    }
                    return withSuccess("{\"secure_url\":\"https://res.cloudinary.com/test/image/upload/v1/"
                            + id.group() + ".webp\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });
        Map<String, String> result = service.upload(image());
        assertTrue(result.get("url").startsWith("https://res.cloudinary.com/test/"));
        assertTrue(result.get("publicId").startsWith("sekkinlk/products/"));
        server.verify();
    }

    private String field(String multipart, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\"[^\\r\\n]*\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r\\n]+)")
                .matcher(multipart);
        assertTrue(matcher.find(), "Missing multipart field: " + name);
        return matcher.group(1);
    }

    @Test void rejectsNonImageContent() {
        var file = new MockMultipartFile("file", "fake.webp", "image/webp", "not an image at all".getBytes());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.upload(file)).getStatusCode().value());
        server.verify();
    }

    @Test void missingCredentialsReturnsHelpfulError() {
        CloudinaryService unconfigured = new CloudinaryService(builder.build(), new CloudinaryProperties("", "", ""));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> unconfigured.upload(image())).getStatusCode().value());
    }

    @Test void providerErrorDoesNotExposeCredentials() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test/image/upload"))
                .andRespond(withBadRequest().body("private provider details"));
        var error = assertThrows(ResponseStatusException.class, () -> service.upload(image()));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getReason().contains("private"));
    }

    @Test void deletionNeverTouchesLegacyOrOtherAccountAssets() {
        service.deleteUnused("http://localhost:8080/uploads/old.webp");
        service.deleteUnused("https://res.cloudinary.com/other/image/upload/v1/sekkinlk/products/00000000-0000-0000-0000-000000000000.webp");
        service.deleteUnused("https://res.cloudinary.com/test/image/upload/v1/unrelated.webp");
        server.verify();
    }

    @Test void deletionInvalidatesManagedAsset() {
        String publicId = "sekkinlk/products/00000000-0000-0000-0000-000000000000";
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test/image/destroy"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invalidate=true")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("signature=")))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));
        service.deleteUnused("https://res.cloudinary.com/test/image/upload/v1/" + publicId + ".webp");
        server.verify();
    }
}
