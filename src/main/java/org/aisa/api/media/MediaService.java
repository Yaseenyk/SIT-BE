package org.aisa.api.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.util.HashMap;
import java.util.Map;
import org.aisa.api.common.ServiceUnavailableException;
import org.aisa.api.config.AisaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Image hosting, via Cloudinary.
 *
 * <p>The browser uploads the file <em>directly</em> to Cloudinary and this API only mints
 * a short-lived signature authorising it. Two reasons that is the right shape here rather
 * than proxying the bytes: a free-tier container has neither the memory to buffer several
 * 8&nbsp;MB uploads nor a disk that survives a redeploy, and the API secret stays on the
 * server either way.
 *
 * <p>The Firebase Storage version this replaces let any authenticated client upload
 * anything of any size to any path. Here the folder and the allowed formats are baked into
 * the signature, so a tampered request fails Cloudinary's own check.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final AisaProperties.Cloudinary config;
    private final Cloudinary cloudinary;

    public MediaService(AisaProperties properties) {
        this.config = properties.cloudinary();
        this.cloudinary = config.isConfigured()
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", config.cloudName(),
                        "api_key", config.apiKey(),
                        "api_secret", config.apiSecret(),
                        "secure", true))
                /*
                 * Null, not a stub. The site's text content works perfectly without image
                 * hosting configured, so the app must start; but a stub that pretended to
                 * upload would leave admins with broken image URLs and no error.
                 */
                : null;
    }

    /**
     * Parameters the browser must post to Cloudinary, including the signature.
     *
     * <p>{@code folder} and {@code transformation} are inside the signed set, so the
     * client cannot widen them: an upload aimed at another folder, or at full resolution,
     * fails signature validation at Cloudinary rather than being trusted here.
     */
    public UploadSignature signUpload(String folder) {
        requireConfigured();

        long timestamp = System.currentTimeMillis() / 1000L;
        String targetFolder = config.uploadFolder() + "/" + sanitiseFolder(folder);

        Map<String, Object> signed = new HashMap<>();
        signed.put("timestamp", timestamp);
        signed.put("folder", targetFolder);
        /*
         * Server-side resize, replacing the canvas-based compressImage() the old page ran
         * in the browser. Same intent — do not store a 12 MP phone photo to render it at
         * 800px — but it cannot be skipped by a client that posts directly.
         */
        signed.put("transformation", "c_limit,w_1600,h_1600,q_auto:good");

        String signature = cloudinary.apiSignRequest(signed, config.apiSecret());

        return new UploadSignature(
                config.cloudName(),
                config.apiKey(),
                timestamp,
                targetFolder,
                (String) signed.get("transformation"),
                signature,
                "https://api.cloudinary.com/v1_1/" + config.cloudName() + "/image/upload");
    }

    /**
     * Deletes an asset, logging rather than throwing on failure.
     *
     * <p>Used on the delete/replace paths, where the database change is what the admin
     * asked for. Failing their delete because a remote cleanup call timed out would leave
     * them staring at an error over a row that is already gone; an orphaned image is the
     * cheaper outcome, and it is logged so it can be swept later.
     */
    public void deleteQuietly(String publicId) {
        if (publicId == null || publicId.isBlank() || cloudinary == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
        } catch (Exception ex) {
            log.warn("Could not delete Cloudinary asset '{}': {}", publicId, ex.getMessage());
        }
    }

    private void requireConfigured() {
        if (cloudinary == null) {
            throw new ServiceUnavailableException(
                    "Image uploads are not configured. Set CLOUDINARY_* in the environment.");
        }
    }

    /** Keeps a caller-supplied folder name to one path segment of safe characters. */
    private static String sanitiseFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "misc";
        }
        String cleaned = folder.toLowerCase().replaceAll("[^a-z0-9-]", "");
        return cleaned.isBlank() ? "misc" : cleaned;
    }

    /** Everything the browser needs to POST straight to Cloudinary. */
    public record UploadSignature(
            String cloudName,
            String apiKey,
            long timestamp,
            String folder,
            String transformation,
            String signature,
            String uploadUrl) {}
}
