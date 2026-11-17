package dao.minhpvth08530;

import models.enums.ActiveStatus;
import models.minhpvth08530.CoffeePackage;
import models.minhpvth08530.PackagePriceHistory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Truy cập bảng packages và package_price_histories — UC74..UC80. */
public class CoffeePackageDAO {

    private static final String SELECT = """
            SELECT pk.package_id, pk.variant_id, pk.grind_size_id, pk.sku, pk.weight_g,
                   pk.min_stock_level, pk.status,
                   v.variant_name, v.status AS variant_status, p.product_id, p.product_name,
                   g.grind_name, g.is_whole_bean,
                   cp.sale_price, cp.effective_from,
                   COALESCE(ps.quantity_on_hand, 0) AS quantity_on_hand,
                   COALESCE(ps.is_low_stock, false) AS is_low_stock
            FROM packages pk
            JOIN product_variants v ON v.variant_id = pk.variant_id
            JOIN products p ON p.product_id = v.product_id
            JOIN grind_sizes g ON g.grind_size_id = pk.grind_size_id
            LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
            LEFT JOIN v_package_stock ps ON ps.package_id = pk.package_id
            """;

    /**
     * UC74 - Danh sách gói bán kèm giá hiện hành và tồn kho.
     * Lọc theo biến thể / sản phẩm / trạng thái (null = không lọc).
     */
    public List<CoffeePackage> findAll(Integer variantId, Integer productId, ActiveStatus status)
            throws SQLException {
        String statusValue = status == null ? null : status.name();
        String sql = SELECT + """
                WHERE (CAST(? AS integer) IS NULL OR pk.variant_id = ?)
                  AND (CAST(? AS integer) IS NULL OR p.product_id = ?)
                  AND (CAST(? AS text) IS NULL OR pk.status = ?)
                ORDER BY p.product_name, v.display_order, pk.weight_g, g.sort_order, pk.package_id
                """;
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            DaoSupport.setNullableInt(statement, 1, variantId);
            DaoSupport.setNullableInt(statement, 2, variantId);
            DaoSupport.setNullableInt(statement, 3, productId);
            DaoSupport.setNullableInt(statement, 4, productId);
            statement.setString(5, statusValue);
            statement.setString(6, statusValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CoffeePackage> packages = new ArrayList<>();
                while (resultSet.next()) {
                    packages.add(map(resultSet));
                }
                return packages;
            }
        }
    }

    public CoffeePackage findById(int packageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + " WHERE pk.package_id = ?")) {
            statement.setInt(1, packageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    /**
     * UC75 - Thêm gói bán và giá bán đầu tiên trong cùng một giao dịch
     * (không có gói bán nào tồn tại mà chưa có giá).
     */
    public int insert(CoffeePackage pkg, BigDecimal salePrice, int changedBy) throws SQLException {
        String sku = DaoSupport.code(pkg.getSku(), "SKU");
        validateNumbers(pkg);
        BigDecimal price = DaoSupport.money(salePrice, "Giá bán");
        try (Connection connection = DaoSupport.openConnection()) {
            if (DaoSupport.exists(connection, """
                    SELECT EXISTS (SELECT 1 FROM product_variants
                                   WHERE variant_id = ? AND status = 'INACTIVE')
                    """, pkg.getVariantId())) {
                throw new IllegalStateException("Biến thể đã ngừng kinh doanh, không thêm gói bán được");
            }
            return DaoSupport.inTransaction(connection, c -> {
                int packageId;
                try (PreparedStatement statement = c.prepareStatement("""
                        INSERT INTO packages (variant_id, grind_size_id, sku, weight_g, min_stock_level)
                        VALUES (?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, pkg.getVariantId());
                    statement.setInt(2, pkg.getGrindSizeId());
                    statement.setString(3, sku);
                    statement.setInt(4, pkg.getWeightG());
                    statement.setInt(5, pkg.getMinStockLevel());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        packageId = keys.getInt(1);
                    }
                }
                DaoSupport.update(c, """
                        INSERT INTO package_price_histories (package_id, sale_price, changed_by, note)
                        VALUES (?, ?, ?, ?)
                        """, packageId, price, changedBy, "Giá khởi tạo gói bán");
                return packageId;
            });
        }
    }

    /**
     * UC76 - Sửa gói bán. Đổi trọng lượng hoặc độ xay chỉ được khi gói chưa phát sinh nhập/bán,
     * vì lô hàng và dòng hóa đơn cũ sẽ không còn khớp với gói.
     */
    public boolean update(CoffeePackage pkg) throws SQLException {
        String sku = DaoSupport.code(pkg.getSku(), "SKU");
        validateNumbers(pkg);
        CoffeePackage current = findById(pkg.getPackageId());
        if (current == null) {
            return false;
        }
        boolean shapeChanged = !current.getWeightG().equals(pkg.getWeightG())
                || !current.getGrindSizeId().equals(pkg.getGrindSizeId());
        try (Connection connection = DaoSupport.openConnection()) {
            if (shapeChanged && isTraded(connection, pkg.getPackageId())) {
                throw new IllegalStateException(
                        "Gói bán đã phát sinh nhập/bán, không đổi được trọng lượng hoặc độ xay");
            }
            return DaoSupport.update(connection, """
                    UPDATE packages
                    SET sku = ?, grind_size_id = ?, weight_g = ?, min_stock_level = ?
                    WHERE package_id = ?
                    """, sku, pkg.getGrindSizeId(), pkg.getWeightG(), pkg.getMinStockLevel(),
                    pkg.getPackageId()) == 1;
        }
    }

    /**
     * UC77 - Xóa gói bán chưa từng nhập, bán, ánh xạ mã NCC hay có thông báo;
     * lịch sử giá của gói xóa cùng giao dịch.
     */
    public boolean delete(int packageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.inTransaction(connection, c -> {
                if (isTraded(c, packageId) || DaoSupport.exists(c, """
                        SELECT EXISTS (SELECT 1 FROM supplier_item_mappings WHERE package_id = ?)
                            OR EXISTS (SELECT 1 FROM notifications WHERE package_id = ?)
                        """, packageId, packageId)) {
                    throw new IllegalStateException(
                            "Gói bán đã phát sinh dữ liệu (nhập/bán/ánh xạ), hãy ngừng bán thay vì xóa");
                }
                DaoSupport.update(c, "DELETE FROM package_price_histories WHERE package_id = ?", packageId);
                return DaoSupport.update(c, "DELETE FROM packages WHERE package_id = ?", packageId) == 1;
            });
        }
    }

    /**
     * UC78 - Cập nhật giá bán: thêm mốc giá mới (không sửa giá cũ).
     * effectiveFrom null = áp dụng ngay; có thể hẹn ngày trong tương lai.
     */
    public long updatePrice(int packageId, BigDecimal salePrice, OffsetDateTime effectiveFrom,
                            int changedBy, String note) throws SQLException {
        BigDecimal price = DaoSupport.money(salePrice, "Giá bán");
        String text = DaoSupport.optionalText(note, "Ghi chú", 500);
        if (effectiveFrom != null && effectiveFrom.isBefore(OffsetDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("Ngày áp dụng giá phải ở hiện tại hoặc tương lai");
        }
        CoffeePackage current = findById(packageId);
        if (current == null) {
            throw new IllegalArgumentException("Không tìm thấy gói bán");
        }
        if (effectiveFrom == null && current.getSalePrice() != null
                && current.getSalePrice().compareTo(price) == 0) {
            throw new IllegalArgumentException("Giá mới trùng với giá hiện hành");
        }
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO package_price_histories
                         (package_id, sale_price, effective_from, changed_by, note)
                     VALUES (?, ?, COALESCE(?, now()), ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, packageId);
            statement.setBigDecimal(2, price);
            statement.setObject(3, effectiveFrom);
            statement.setInt(4, changedBy);
            statement.setString(5, text);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** UC79 - Lịch sử giá của gói, mới nhất trước; đánh dấu giá đang áp dụng và giá hẹn trước. */
    public List<PackagePriceHistory> findPriceHistory(int packageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT h.price_history_id, h.package_id, h.sale_price, h.effective_from,
                            h.changed_by, h.note, u.full_name AS changed_by_name,
                            h.effective_from <= now() AS is_applied,
                            h.price_history_id = (
                                SELECT h2.price_history_id FROM package_price_histories h2
                                WHERE h2.package_id = h.package_id AND h2.effective_from <= now()
                                ORDER BY h2.effective_from DESC LIMIT 1) AS is_current
                     FROM package_price_histories h
                     LEFT JOIN users u ON u.user_id = h.changed_by
                     WHERE h.package_id = ?
                     ORDER BY h.effective_from DESC, h.price_history_id DESC
                     """)) {
            statement.setInt(1, packageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PackagePriceHistory> list = new ArrayList<>();
                while (resultSet.next()) {
                    PackagePriceHistory history = new PackagePriceHistory();
                    history.setPriceHistoryId(resultSet.getLong("price_history_id"));
                    history.setPackageId(resultSet.getInt("package_id"));
                    history.setSalePrice(resultSet.getBigDecimal("sale_price"));
                    history.setEffectiveFrom(DaoSupport.getTime(resultSet, "effective_from"));
                    history.setChangedBy(resultSet.getInt("changed_by"));
                    history.setNote(resultSet.getString("note"));
                    history.setChangedByName(resultSet.getString("changed_by_name"));
                    history.setApplied(resultSet.getBoolean("is_applied"));
                    history.setCurrent(resultSet.getBoolean("is_current"));
                    list.add(history);
                }
                return list;
            }
        }
    }

    /** UC80 - Ngừng bán gói; tồn kho và lịch sử vẫn giữ để báo cáo. */
    public boolean deactivate(int packageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.update(connection, """
                    UPDATE packages SET status = 'INACTIVE'
                    WHERE package_id = ? AND status = 'ACTIVE'
                    """, packageId) == 1;
        }
    }

    /** Mở bán lại gói, chỉ khi biến thể của gói đang kinh doanh. */
    public boolean activate(int packageId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            int changed = DaoSupport.update(connection, """
                    UPDATE packages pk SET status = 'ACTIVE'
                    FROM product_variants v
                    WHERE pk.package_id = ? AND v.variant_id = pk.variant_id
                      AND v.status = 'ACTIVE' AND pk.status = 'INACTIVE'
                    """, packageId);
            if (changed == 0) {
                throw new IllegalStateException("Chỉ mở bán lại được khi biến thể đang kinh doanh");
            }
            return true;
        }
    }

    private static boolean isTraded(Connection connection, int packageId) throws SQLException {
        return DaoSupport.exists(connection, """
                SELECT EXISTS (SELECT 1 FROM purchase_invoice_lines WHERE package_id = ?)
                    OR EXISTS (SELECT 1 FROM sales_invoice_lines WHERE package_id = ?)
                """, packageId, packageId);
    }

    private static void validateNumbers(CoffeePackage pkg) {
        if (pkg.getVariantId() == null && pkg.getPackageId() == null) {
            throw new IllegalArgumentException("Thiếu biến thể của gói bán");
        }
        if (pkg.getGrindSizeId() == null) {
            throw new IllegalArgumentException("Chọn dạng hạt / độ xay");
        }
        if (pkg.getWeightG() == null || pkg.getWeightG() <= 0 || pkg.getWeightG() > 100_000) {
            throw new IllegalArgumentException("Trọng lượng phải lớn hơn 0 gram");
        }
        if (pkg.getMinStockLevel() == null) {
            pkg.setMinStockLevel(0);
        }
        if (pkg.getMinStockLevel() < 0) {
            throw new IllegalArgumentException("Tồn tối thiểu không được âm");
        }
    }

    private static CoffeePackage map(ResultSet resultSet) throws SQLException {
        CoffeePackage pkg = new CoffeePackage();
        pkg.setPackageId(resultSet.getInt("package_id"));
        pkg.setVariantId(resultSet.getInt("variant_id"));
        pkg.setGrindSizeId(resultSet.getInt("grind_size_id"));
        pkg.setSku(resultSet.getString("sku"));
        pkg.setWeightG(resultSet.getInt("weight_g"));
        pkg.setMinStockLevel(resultSet.getInt("min_stock_level"));
        pkg.setStatus(ActiveStatus.valueOf(resultSet.getString("status")));
        pkg.setVariantName(resultSet.getString("variant_name"));
        pkg.setVariantStatus(resultSet.getString("variant_status"));
        pkg.setProductId(resultSet.getInt("product_id"));
        pkg.setProductName(resultSet.getString("product_name"));
        pkg.setGrindName(resultSet.getString("grind_name"));
        pkg.setWholeBean(resultSet.getBoolean("is_whole_bean"));
        pkg.setSalePrice(resultSet.getBigDecimal("sale_price"));
        pkg.setPriceEffectiveFrom(DaoSupport.getTime(resultSet, "effective_from"));
        pkg.setQuantityOnHand(resultSet.getInt("quantity_on_hand"));
        pkg.setLowStock(resultSet.getBoolean("is_low_stock"));
        return pkg;
    }
}
