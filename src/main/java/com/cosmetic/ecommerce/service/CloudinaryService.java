package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.config.CloudinaryProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class CloudinaryService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final String PREFIX = "sekkinlk/products/";
    private final RestClient client;
    private final CloudinaryProperties properties;

    public CloudinaryService(@Qualifier("cloudinaryClient") RestClient client, CloudinaryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Map<String, String> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select an image.");
        }
        if (file.getSize() > 15L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 15 MB or smaller.");
        }
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Image storage is not configured. Set the backend Cloudinary environment variables.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!isImage(bytes)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a JPEG, PNG, WebP, or GIF image.");
            }
            String publicId = PREFIX + UUID.randomUUID();
            var form = signedForm(Map.of("public_id", publicId, "overwrite", "false",
                    "allowed_formats", "jpg,jpeg,png,webp,gif"));
            form.add("file", new ByteArrayResource(bytes) {
                @Override public String getFilename() { return "product"; }
            });
            Map<?, ?> result = client.post().uri("/v1_1/{cloud}/image/upload", properties.cloudName())
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
            if (result == null) throw new IOException("Empty upload response");
            String url = (String) result.get("secure_url");
            if (managedPublicId(url).filter(publicId::equals).isEmpty()) {
                throw new IOException("Unexpected image storage response");
            }
            return Map.of("url", url, "publicId", publicId);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ResponseStatusException statusException) throw statusException;
            // Provider errors may contain credentials. Do not expose or log them.
            log.warn("Cloudinary upload failed ({})", ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image upload failed. Please try again.");
        }
    }

    public Optional<String> managedPublicId(String url) {
        if (url == null || properties.cloudName() == null || properties.cloudName().isBlank()) {
            return Optional.empty();
        }
        String base = "https://res.cloudinary.com/" + properties.cloudName() + "/image/upload/";
        var matcher = Pattern.compile(Pattern.quote(base) + "v[0-9]+/(" + PREFIX
                + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(?:webp|jpg|jpeg|png|gif)")
                .matcher(url);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public void deleteUnused(String url) {
        managedPublicId(url).ifPresent(publicId -> {
            try {
                if (!properties.configured()) throw new IOException("Not configured");
                Map<?, ?> result = client.post().uri("/v1_1/{cloud}/image/destroy", properties.cloudName())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(signedForm(Map.of("public_id", publicId, "invalidate", "true")))
                        .retrieve().body(Map.class);
                if (result == null || !List.of("ok", "not found").contains(result.get("result"))) {
                    throw new IOException("Deletion failed");
                }
            } catch (IOException | RuntimeException ex) {
                // Product changes have already succeeded. Keep unused assets on failure.
                log.warn("Cloudinary cleanup needs retry for public ID {}", publicId);
            }
        });
    }

    private LinkedMultiValueMap<String, Object> signedForm(Map<String, String> options) {
        var parameters = new TreeMap<>(options);
        parameters.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        String canonical = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        String signature;
        try {
            signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((canonical + properties.apiSecret()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
        var form = new LinkedMultiValueMap<String, Object>();
        parameters.forEach(form::add);
        form.add("api_key", properties.apiKey());
        form.add("signature", signature);
        return form;
    }

    private boolean isImage(byte[] bytes) {
        if (bytes.length < 12) return false;
        boolean jpeg = (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean gif = bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
        boolean webp = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        // Cloudinary performs decoding and enforces allowed_formats as well.
        return jpeg || png || gif || webp;
    }
}
