package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dao.hieulnmth08470.InventorySupport.exec;
import static dao.hieulnmth08470.InventorySupport.list;
import static dao.hieulnmth08470.InventorySupport.one;
import static dao.hieulnmth08470.InventorySupport.open;

/**
 * Phát hiện lô hết hạn / cận hạn / tồn thấp và thông báo — UC113, UC125..UC129 (Lê Nguyễn Minh Hiếu).
 * <p>Ba hàm detect* chạy bằng job nền (InventoryJob) mỗi giờ và khi bấm "Quét ngay".
 * Không sinh trùng: hết hạn / cận hạn mỗi lô chỉ báo 1 lần, tồn thấp mỗi gói tối đa 1 lần/ngày
 * (unique index ux_notif_once_per_day).</p>
 */
public class NotificationDAO {

    private static final List<String> TYPES = List.of("NEAR_EXPIRY", "EXPIRED", "LOW_STOCK");

    // ================================================================== UC113, UC125, UC126

    /** UC113-B2: thông báo EXPIRED cho lô đã hết hạn còn tồn (mỗi lô 1 lần). */
    public int detectExpired() throws SQLException {
        try (Connection connection = open()) {
            return exec(connection, """
                    INSERT INTO notifications (notification_type, lot_id, message)
                    SELECT 'EXPIRED', s.lot_id,
                           'Lô ' || s.lot_code || ' đã hết hạn ngày ' || to_char(s.expiry_date, 'DD/MM/YYYY')
                           || ', còn ' || s.quantity_on_hand
                    FROM v_lot_stock s
                    WHERE s.expiry_date < CURRENT_DATE AND s.quantity_on_hand > 0
                      AND NOT EXISTS (SELECT 1 FROM notifications n
                                      WHERE n.lot_id = s.lot_id AND n.notification_type = 'EXPIRED')
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    /** UC125: thông báo NEAR_EXPIRY cho lô còn hạn nhưng trong số ngày cận hạn (mỗi lô 1 lần). */
    public int detectNearExpiry() throws SQLException {
        try (Connection connection = open()) {
            return exec(connection, """
                    INSERT INTO notifications (notification_type, lot_id, message)
                    SELECT 'NEAR_EXPIRY', s.lot_id,
                           'Lô ' || s.lot_code || ' sắp hết hạn ngày ' || to_char(s.expiry_date, 'DD/MM/YYYY')
                           || ', còn ' || s.quantity_on_hand
                    FROM v_lot_stock s
                    CROSS JOIN (SELECT CAST(setting_value AS INT) AS days FROM system_settings
                                WHERE setting_key = 'inventory.near_expiry_days') st
                    WHERE s.quantity_on_hand > 0
                      AND s.expiry_date >= CURRENT_DATE
                      AND s.expiry_date <= CURRENT_DATE + st.days
                      AND NOT EXISTS (SELECT 1 FROM notifications n
                                      WHERE n.lot_id = s.lot_id AND n.notification_type = 'NEAR_EXPIRY')
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    /** UC126: thông báo LOW_STOCK cho gói đang bán có tồn <= ngưỡng (mỗi gói tối đa 1 lần/ngày). */
    public int detectLowStock() throws SQLException {
        try (Connection connection = open()) {
            return exec(connection, """
                    INSERT INTO notifications (notification_type, package_id, message)
                    SELECT 'LOW_STOCK', p.package_id,
                           'Gói ' || p.sku || ' còn ' || s.quantity_on_hand || ' (ngưỡng ' || p.min_stock_level || ')'
                    FROM v_package_stock s
                    JOIN packages p ON p.package_id = s.package_id
                    WHERE p.status = 'ACTIVE' AND s.is_low_stock
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    /** Chạy cả 3 bước phát hiện, trả số thông báo mới của từng loại. */
    public Map<String, Object> detectAll() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expired", detectExpired());
        result.put("nearExpiry", detectNearExpiry());
        result.put("lowStock", detectLowStock());
        return result;
    }

    // ================================================================== UC127

    /**
     * UC127 danh sách thông báo mới nhất trước, có phân trang, kèm cờ đã đọc của người đang xem.
     * @return rows, total, unread, page, size
     */
    public Map<String, Object> list(int userId, String typeInput, boolean unreadOnly, Integer pageInput,
                                    Integer sizeInput) throws SQLException {
        String type = typeInput == null || typeInput.isBlank() ? null : typeInput.strip().toUpperCase();
        if (type != null && !TYPES.contains(type)) {
            throw new IllegalArgumentException("Loại thông báo không hợp lệ");
        }
        int size = InventorySupport.pageSize(sizeInput);
        int page = InventorySupport.page(pageInput);
        String where = """
                WHERE (CAST(? AS TEXT) IS NULL OR n.notification_type = ?)
                  AND (NOT ? OR r.notification_id IS NULL)
                """;
        try (Connection connection = open()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", InventorySupport.list(connection, """
                    SELECT n.notification_id, n.notification_type, n.message, n.created_at,
                           (r.notification_id IS NOT NULL) AS is_read
                    FROM notifications n
                    LEFT JOIN notification_reads r ON r.notification_id = n.notification_id AND r.user_id = ?
                    """ + where + """
                    ORDER BY n.created_at DESC, n.notification_id DESC
                    LIMIT ? OFFSET ?
                    """, userId, type, type, unreadOnly, size, (page - 1) * size));
            Map<String, Object> count = one(connection, """
                    SELECT COUNT(*) AS total FROM notifications n
                    LEFT JOIN notification_reads r ON r.notification_id = n.notification_id AND r.user_id = ?
                    """ + where, userId, type, type, unreadOnly);
            result.put("total", count.get("total"));
            result.put("unread", unreadCount(connection, userId));
            result.put("page", page);
            result.put("size", size);
            return result;
        }
    }

    /** Số thông báo chưa đọc (chuông trên header). */
    public long unreadCount(int userId) throws SQLException {
        try (Connection connection = open()) {
            return unreadCount(connection, userId);
        }
    }

    private static long unreadCount(Connection connection, int userId) throws SQLException {
        Map<String, Object> row = one(connection, """
                SELECT COUNT(*) AS unread FROM notifications n
                WHERE NOT EXISTS (SELECT 1 FROM notification_reads r
                                  WHERE r.notification_id = n.notification_id AND r.user_id = ?)
                """, userId);
        return ((Number) row.get("unread")).longValue();
    }

    // ================================================================== UC128

    /** UC128 chi tiết thông báo + tình trạng hiện tại của lô / gói liên quan. */
    public Map<String, Object> detail(long notificationId, int userId) throws SQLException {
        try (Connection connection = open()) {
            return one(connection, """
                    SELECT n.notification_id, n.notification_type, n.message, n.created_at,
                           n.lot_id, s.lot_code, s.expiry_date, s.quantity_on_hand AS lot_qty,
                           s.expiry_date - CURRENT_DATE AS days_left,
                           p.package_id, p.sku, p.min_stock_level, ps.quantity_on_hand AS package_qty,
                           r.read_at
                    FROM notifications n
                    LEFT JOIN v_lot_stock s ON s.lot_id = n.lot_id
                    LEFT JOIN packages p ON p.package_id = COALESCE(n.package_id, s.package_id)
                    LEFT JOIN v_package_stock ps ON ps.package_id = p.package_id
                    LEFT JOIN notification_reads r ON r.notification_id = n.notification_id AND r.user_id = ?
                    WHERE n.notification_id = ?
                    """, userId, notificationId);
        }
    }

    // ================================================================== UC129

    /** UC129 đánh dấu đã đọc (đọc rồi thì thôi). @return true nếu vừa đánh dấu */
    public boolean markRead(long notificationId, int userId) throws SQLException {
        try (Connection connection = open()) {
            if (one(connection, "SELECT 1 AS found FROM notifications WHERE notification_id = ?", notificationId) == null) {
                throw new IllegalArgumentException("Không tìm thấy thông báo");
            }
            return exec(connection, """
                    INSERT INTO notification_reads (notification_id, user_id)
                    VALUES (?, ?)
                    ON CONFLICT (notification_id, user_id) DO NOTHING
                    """, notificationId, userId) == 1;
        }
    }

    /** Đánh dấu tất cả đã đọc. @return số thông báo vừa đánh dấu */
    public int markAllRead(int userId) throws SQLException {
        try (Connection connection = open()) {
            return exec(connection, """
                    INSERT INTO notification_reads (notification_id, user_id)
                    SELECT n.notification_id, ? FROM notifications n
                    ON CONFLICT (notification_id, user_id) DO NOTHING
                    """, userId);
        }
    }
}
