package controllers.minhpvth08530;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.servlet.ServletContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu ảnh biến thể.
 * <ul>
 *   <li>Có biến môi trường CLOUDINARY_URL (cloudinary://API_KEY:API_SECRET@CLOUD_NAME):
 *       upload lên Cloudinary, lưu secure_url + public_id.</li>
 *   <li>Chưa cấu hình: lưu vào thư mục webapp/uploads/variants để chạy được trên máy cá nhân,
 *       public_id có dạng "local/variants/&lt;uuid&gt;.jpg".</li>
 * </ul>
 */
final class ImageStorage {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String FOLDER = "coffee-shop/variants";
    private static final String LOCAL_PREFIX = "local/variants/";
    private static final Pattern LOCAL_FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");

    record StoredImage(String url, String publicId) {}

    private final ServletContext context;

    ImageStorage(ServletContext context) {
        this.context = context;
    }

    static String extension(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase().split(";")[0].strip()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> null;
        };
    }

    StoredImage save(byte[] data, String extension, String contextPath) throws IOException {
        Cloudinary cloudinary = cloudinary();
        if (cloudinary != null) {
            Map<?, ?> result = cloudinary.uploader().upload(data,
                    ObjectUtils.asMap("folder", FOLDER, "resource_type", "image"));
            return new StoredImage(String.valueOf(result.get("secure_url")),
                    String.valueOf(result.get("public_id")));
        }
        String fileName = UUID.randomUUID() + "." + extension;
        Path folder = localFolder();
        Files.createDirectories(folder);
        Files.write(folder.resolve(fileName), data);
        return new StoredImage(contextPath + "/uploads/variants/" + fileName, LOCAL_PREFIX + fileName);
    }

    /** Xóa ảnh trên Cloudinary hoặc trong thư mục local; lỗi chỉ ghi log, không chặn thao tác. */
    void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            if (publicId.startsWith(LOCAL_PREFIX)) {
                String fileName = publicId.substring(LOCAL_PREFIX.length());
                if (LOCAL_FILE.matcher(fileName).matches()) {
                    Files.deleteIfExists(localFolder().resolve(fileName));
                }
                return;
            }
            Cloudinary cloudinary = cloudinary();
            if (cloudinary != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (IOException | RuntimeException exception) {
            context.log("Không xóa được ảnh " + publicId, exception);
        }
    }

    private Path localFolder() throws IOException {
        String realPath = context.getRealPath("/uploads/variants");
        if (realPath == null) {
            throw new IOException("Chưa cấu hình CLOUDINARY_URL và server không cho lưu file local");
        }
        return Path.of(realPath);
    }

    private static Cloudinary cloudinary() {
        String url = System.getProperty("CLOUDINARY_URL");
        if (url == null || url.isBlank()) {
            url = System.getenv("CLOUDINARY_URL");
        }
        return url == null || url.isBlank() ? null : new Cloudinary(url.strip());
    }
}
