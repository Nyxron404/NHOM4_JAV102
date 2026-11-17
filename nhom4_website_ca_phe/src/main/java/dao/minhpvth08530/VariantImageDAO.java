package dao.minhpvth08530;

import models.minhpvth08530.VariantImage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy cập bảng variant_images — UC72, UC73. */
public class VariantImageDAO {

    /** Danh sách ảnh của biến thể theo thứ tự hiển thị. */
    public List<VariantImage> findByVariant(int variantId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return findByVariant(connection, variantId);
        }
    }

    List<VariantImage> findByVariant(Connection connection, int variantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT image_id, variant_id, image_url, public_id, sort_order
                FROM variant_images
                WHERE variant_id = ?
                ORDER BY sort_order, image_id
                """)) {
            statement.setInt(1, variantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<VariantImage> images = new ArrayList<>();
                while (resultSet.next()) {
                    VariantImage image = new VariantImage();
                    image.setImageId(resultSet.getInt("image_id"));
                    image.setVariantId(resultSet.getInt("variant_id"));
                    image.setImageUrl(resultSet.getString("image_url"));
                    image.setPublicId(resultSet.getString("public_id"));
                    image.setSortOrder(resultSet.getInt("sort_order"));
                    images.add(image);
                }
                return images;
            }
        }
    }

    /**
     * UC72 - Lưu ảnh đã upload (link + public_id Cloudinary) cho biến thể.
     * sort_order tự tăng nên không vi phạm UNIQUE(variant_id, sort_order).
     */
    public int insert(int variantId, String imageUrl, String publicId) throws SQLException {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 500) {
            throw new IllegalArgumentException("Link ảnh không hợp lệ");
        }
        if (publicId == null || publicId.isBlank() || publicId.length() > 255) {
            throw new IllegalArgumentException("public_id ảnh không hợp lệ");
        }
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO variant_images (variant_id, image_url, public_id, sort_order)
                     SELECT ?, ?, ?, COALESCE(MAX(sort_order) + 1, 0)
                     FROM variant_images
                     WHERE variant_id = ?
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, variantId);
            statement.setString(2, imageUrl.strip());
            statement.setString(3, publicId.strip());
            statement.setInt(4, variantId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /**
     * UC73 - Xóa ảnh.
     * @return public_id của ảnh vừa xóa để xóa tiếp trên Cloudinary; null nếu ảnh không tồn tại
     */
    public String delete(int imageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM variant_images WHERE image_id = ? RETURNING public_id")) {
            statement.setInt(1, imageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }
}
