package dao.minhpvth08530;

import models.enums.ActiveStatus;
import models.minhpvth08530.Product;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Truy cập bảng products — UC51..UC58. */
public class ProductDAO {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String SELECT = """
            SELECT p.product_id, p.product_code, p.product_name, p.description, p.qr_token,
                   p.status, p.created_at,
                   COUNT(v.variant_id) AS variant_count,
                   COUNT(v.variant_id) FILTER (WHERE v.status = 'ACTIVE') AS active_variant_count
            FROM products p
            LEFT JOIN product_variants v ON v.product_id = p.product_id
            """;

    /**
     * UC51 + UC52 + UC53 - Danh sách sản phẩm, tìm theo từ khóa và lọc theo trạng thái.
     * <p>Tên so khớp qua f_search_key nên gõ không dấu ("ca phe") vẫn ra "Cà phê";
     * từ khóa cũng khớp theo mã sản phẩm. Tham số null = không lọc.</p>
     */
    public List<Product> findAll(String keyword, ActiveStatus status) throws SQLException {
        String key = keyword == null || keyword.isBlank() ? null : keyword.strip();
        String statusValue = status == null ? null : status.name();
        String sql = SELECT + """
                WHERE (CAST(? AS text) IS NULL
                       OR f_search_key(p.product_name) LIKE '%' || f_search_key(?) || '%'
                       OR p.product_code ILIKE '%' || ? || '%')
                  AND (CAST(? AS text) IS NULL OR p.status = ?)
                GROUP BY p.product_id
                ORDER BY p.product_name, p.product_id
                """;
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, key);
            statement.setString(3, key);
            statement.setString(4, statusValue);
            statement.setString(5, statusValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (resultSet.next()) {
                    products.add(map(resultSet));
                }
                return products;
            }
        }
    }

    /** UC51 - Xem danh sách sản phẩm. */
    public List<Product> findAll() throws SQLException {
        return findAll(null, null);
    }

    /** UC52 - Tìm kiếm sản phẩm theo tên (không dấu) hoặc mã. */
    public List<Product> search(String keyword) throws SQLException {
        return findAll(keyword, null);
    }

    /** UC53 - Lọc sản phẩm theo trạng thái. */
    public List<Product> filterByStatus(ActiveStatus status) throws SQLException {
        return findAll(null, status);
    }

    /** UC54 - Xem chi tiết sản phẩm (biến thể lấy qua ProductVariantDAO.findByProduct). */
    public Product findById(int productId) throws SQLException {
        String sql = SELECT + " WHERE p.product_id = ? GROUP BY p.product_id";
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    /** UC55 - Thêm sản phẩm; qr_token sinh ngẫu nhiên cho trang QR của sản phẩm. */
    public int insert(Product product) throws SQLException {
        String code = DaoSupport.code(product.getProductCode(), "Mã sản phẩm");
        String name = DaoSupport.requiredText(product.getProductName(), "Tên sản phẩm", 200);
        String description = DaoSupport.optionalText(product.getDescription(), "Mô tả", 5000);
        String sql = """
                INSERT INTO products (product_code, product_name, description, qr_token)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setString(4, newQrToken());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** UC56 - Sửa mã, tên, mô tả sản phẩm. */
    public boolean update(Product product) throws SQLException {
        String code = DaoSupport.code(product.getProductCode(), "Mã sản phẩm");
        String name = DaoSupport.requiredText(product.getProductName(), "Tên sản phẩm", 200);
        String description = DaoSupport.optionalText(product.getDescription(), "Mô tả", 5000);
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.update(connection, """
                    UPDATE products
                    SET product_code = ?, product_name = ?, description = ?
                    WHERE product_id = ?
                    """, code, name, description, product.getProductId()) == 1;
        }
    }

    /**
     * UC57 - Xóa sản phẩm. Chỉ xóa khi chưa có biến thể nào;
     * sản phẩm đã có biến thể phải ngừng kinh doanh (UC58) để giữ lịch sử bán.
     */
    public boolean delete(int productId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            if (DaoSupport.exists(connection,
                    "SELECT EXISTS (SELECT 1 FROM product_variants WHERE product_id = ?)", productId)) {
                throw new IllegalStateException(
                        "Sản phẩm đã có biến thể, không thể xóa. Hãy ngừng kinh doanh sản phẩm.");
            }
            return DaoSupport.update(connection,
                    "DELETE FROM products WHERE product_id = ?", productId) == 1;
        }
    }

    /**
     * UC58 - Ngừng kinh doanh sản phẩm: sản phẩm, các biến thể đang bán và gói bán của nó
     * cùng chuyển INACTIVE trong một giao dịch để quầy không chọn bán được nữa.
     */
    public boolean deactivate(int productId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.inTransaction(connection, c -> {
                int changed = DaoSupport.update(c, """
                        UPDATE products SET status = 'INACTIVE'
                        WHERE product_id = ? AND status = 'ACTIVE'
                        """, productId);
                DaoSupport.update(c, """
                        UPDATE product_variants SET status = 'INACTIVE'
                        WHERE product_id = ? AND status = 'ACTIVE'
                        """, productId);
                DaoSupport.update(c, """
                        UPDATE packages pk SET status = 'INACTIVE'
                        FROM product_variants v
                        WHERE v.variant_id = pk.variant_id AND v.product_id = ?
                          AND pk.status = 'ACTIVE'
                        """, productId);
                return changed == 1;
            });
        }
    }

    /** Kinh doanh lại sản phẩm (biến thể và gói bán được kích hoạt lại riêng từng cái). */
    public boolean activate(int productId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.update(connection, """
                    UPDATE products SET status = 'ACTIVE'
                    WHERE product_id = ? AND status = 'INACTIVE'
                    """, productId) == 1;
        }
    }

    /**
     * Tạo lại mã QR cho sản phẩm: sinh qr_token mới, link/QR cũ sẽ hết hiệu lực.
     * @return token mới, null nếu không có sản phẩm này
     */
    public String regenerateQrToken(int productId) throws SQLException {
        String token = newQrToken();
        try (Connection connection = DaoSupport.openConnection()) {
            int rows = DaoSupport.update(connection,
                    "UPDATE products SET qr_token = ? WHERE product_id = ?", token, productId);
            return rows == 1 ? token : null;
        }
    }

    private static String newQrToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Product map(ResultSet resultSet) throws SQLException {
        Product product = new Product();
        product.setProductId(resultSet.getInt("product_id"));
        product.setProductCode(resultSet.getString("product_code"));
        product.setProductName(resultSet.getString("product_name"));
        product.setDescription(resultSet.getString("description"));
        product.setQrToken(resultSet.getString("qr_token"));
        product.setStatus(ActiveStatus.valueOf(resultSet.getString("status")));
        product.setCreatedAt(DaoSupport.getTime(resultSet, "created_at"));
        product.setVariantCount(resultSet.getInt("variant_count"));
        product.setActiveVariantCount(resultSet.getInt("active_variant_count"));
        return product;
    }
}
