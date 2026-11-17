package controllers.minhpvth08530;

import dao.minhpvth08530.VariantImageDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/**
 * API ảnh biến thể — UC72, UC73.
 * <pre>
 * GET  /api/variant-images?variantId=                          danh sách ảnh
 * POST /api/variant-images?variantId=   (body = file ảnh, Content-Type: image/jpeg|png|webp|gif)  tải ảnh (UC72)
 * POST /api/variant-images action=delete&amp;id=                     xóa ảnh (UC73)
 * </pre>
 * Upload gửi thẳng file trong body (fetch(url, {body: file})) nên không cần multipart.
 */
@WebServlet("/api/variant-images")
public class VariantImageController extends BaseController {

    private final VariantImageDAO imageDAO = new VariantImageDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        return imageDAO.findByVariant(requiredInt(request, "variantId", "biến thể"));
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        ImageStorage storage = new ImageStorage(getServletContext());
        if ("delete".equals(request.getParameter("action"))) {
            String publicId = found(imageDAO.delete(requiredInt(request, "id", "mã ảnh")), "ảnh");
            storage.delete(publicId);
            return ok("Đã xóa ảnh");
        }

        int variantId = requiredInt(request, "variantId", "biến thể");
        String extension = ImageStorage.extension(request.getContentType());
        if (extension == null) {
            throw new IllegalArgumentException("Chỉ nhận ảnh JPG, PNG, WEBP hoặc GIF");
        }
        byte[] data = readBody(request);
        ImageStorage.StoredImage stored = storage.save(data, extension, request.getContextPath());
        try {
            int imageId = imageDAO.insert(variantId, stored.url(), stored.publicId());
            return ok("Đã tải ảnh lên", "imageId", imageId);
        } catch (SQLException | RuntimeException exception) {
            storage.delete(stored.publicId()); // không để file mồ côi khi lưu DB lỗi
            throw exception;
        }
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > ImageStorage.MAX_BYTES) {
            throw new IllegalArgumentException("Ảnh tối đa 5 MB");
        }
        try (InputStream input = request.getInputStream()) {
            byte[] data = input.readNBytes((int) ImageStorage.MAX_BYTES + 1);
            if (data.length == 0) {
                throw new IllegalArgumentException("Chưa chọn ảnh");
            }
            if (data.length > ImageStorage.MAX_BYTES) {
                throw new IllegalArgumentException("Ảnh tối đa 5 MB");
            }
            return data;
        }
    }
}
