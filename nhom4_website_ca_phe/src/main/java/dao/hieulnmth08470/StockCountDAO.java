package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static dao.hieulnmth08470.InventorySupport.exec;
import static dao.hieulnmth08470.InventorySupport.inTransaction;
import static dao.hieulnmth08470.InventorySupport.list;
import static dao.hieulnmth08470.InventorySupport.one;
import static dao.hieulnmth08470.InventorySupport.open;

/**
 * Phiếu kiểm kê và phiếu điều chỉnh tồn theo lô — UC117..UC124 (Lê Nguyễn Minh Hiếu).
 * <p>Luồng: tạo phiếu nháp (DRAFT) → nhập SL thực tế → gửi báo cáo (PENDING)
 * → chủ cửa hàng xác nhận (APPROVED, ghi sổ ADJUSTMENT) hoặc từ chối (REJECTED).
 * Phiếu điều chỉnh 1 lô (UC124) tạo thẳng ở PENDING và duyệt bằng cùng UC122/UC123.</p>
 */
public class StockCountDAO {

    // ================================================================== UC117

    /** UC117-B1: các lô sẽ được kiểm (packageId = null -> tất cả lô còn tồn). */
    public List<Map<String, Object>> lotsToCount(Integer packageId) throws SQLException {
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT s.lot_id, s.lot_code, p.sku, s.expiry_date, s.quantity_on_hand
                    FROM v_lot_stock s
                    JOIN packages p ON p.package_id = s.package_id
                    WHERE s.quantity_on_hand <> 0
                      AND (CAST(? AS INT) IS NULL OR s.package_id = CAST(? AS INT))
                    ORDER BY p.sku, s.expiry_date
                    """, packageId, packageId);
        }
    }

    /**
     * UC117-B2: tạo phiếu kiểm kê nháp và chụp tồn hệ thống của từng lô;
     * SL thực tế mặc định bằng SL hệ thống để chỉ phải sửa dòng lệch.
     * @return mã phiếu vừa tạo
     */
    public long createCount(Integer packageId, String noteInput, int userId) throws SQLException {
        String note = InventorySupport.note(noteInput, "Ghi chú", 500);
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                // Kiểm trước: CTE INSERT luôn chạy nên không kiểm thì sẽ sinh phiếu rỗng
                Map<String, Object> any = one(c, """
                        SELECT 1 AS found FROM v_lot_stock s
                        WHERE s.quantity_on_hand <> 0
                          AND (CAST(? AS INT) IS NULL OR s.package_id = CAST(? AS INT))
                        LIMIT 1
                        """, packageId, packageId);
                if (any == null) {
                    throw new IllegalStateException("Không có lô nào còn tồn (hoặc đang âm) để kiểm kê");
                }
                List<Map<String, Object>> rows = list(c, """
                        WITH c AS (
                          INSERT INTO stock_counts (count_type, status, created_by, note)
                          VALUES ('COUNT', 'DRAFT', ?, ?)
                          RETURNING stock_count_id
                        )
                        INSERT INTO stock_count_lines (stock_count_id, lot_id, system_qty, counted_qty)
                        SELECT c.stock_count_id, s.lot_id, s.quantity_on_hand, GREATEST(s.quantity_on_hand, 0)
                        FROM c, v_lot_stock s
                        JOIN packages p ON p.package_id = s.package_id
                        WHERE s.quantity_on_hand <> 0
                          AND (CAST(? AS INT) IS NULL OR s.package_id = CAST(? AS INT))
                        RETURNING stock_count_id, count_line_id
                        """, userId, note, packageId, packageId);
                return ((Number) rows.get(0).get("stockCountId")).longValue();
            });
        }
    }

    // ================================================================== UC118

    /** Một dòng SL thực tế người dùng nhập. */
    public record CountedLine(long countLineId, Integer countedQty, String note) {}

    /**
     * UC118-B2: lưu SL thực tế cho các dòng của phiếu (chỉ khi phiếu còn nháp), một giao dịch.
     * @return số dòng đã lưu
     */
    public int saveCounted(long stockCountId, List<CountedLine> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Không có dòng nào để lưu");
        }
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                requireStatus(c, stockCountId, "DRAFT", "Chỉ sửa được phiếu đang ở trạng thái nháp");
                int saved = 0;
                for (CountedLine line : lines) {
                    int counted = InventorySupport.nonNegative(line.countedQty(), "Số lượng thực tế");
                    String note = InventorySupport.note(line.note(), "Ghi chú dòng", 500);
                    saved += exec(c, """
                            UPDATE stock_count_lines cl
                            SET counted_qty = ?,
                                note = ?
                            FROM stock_counts c
                            WHERE cl.count_line_id = ?
                              AND cl.stock_count_id = ?
                              AND c.stock_count_id = cl.stock_count_id
                              AND c.status = 'DRAFT'
                            """, counted, note, line.countLineId(), stockCountId);
                }
                return saved;
            });
        }
    }

    // ================================================================== UC119

    /**
     * UC119: gửi báo cáo chênh lệch — chuyển phiếu DRAFT sang PENDING (chờ chủ cửa hàng duyệt)
     * và ghi audit_logs số dòng lệch, tổng SL lệch.
     */
    public Map<String, Object> submit(long stockCountId, int userId) throws SQLException {
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                Map<String, Object> diff = one(c, """
                        SELECT COUNT(*) FILTER (WHERE counted_qty <> system_qty) AS diff_lines,
                               COALESCE(SUM(counted_qty - system_qty), 0) AS diff_qty
                        FROM stock_count_lines
                        WHERE stock_count_id = ?
                        """, stockCountId);
                int changed = exec(c, """
                        UPDATE stock_counts
                        SET status = 'PENDING'
                        WHERE stock_count_id = ? AND status = 'DRAFT'
                        """, stockCountId);
                if (changed != 1) {
                    throw new IllegalStateException("Phiếu không còn ở trạng thái nháp");
                }
                exec(c, """
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        SELECT ?, 'SUBMIT_STOCK_COUNT', 'stock_counts', ?,
                               jsonb_build_object('diff_lines', COUNT(*) FILTER (WHERE counted_qty <> system_qty),
                                                  'diff_qty', COALESCE(SUM(counted_qty - system_qty), 0))
                        FROM stock_count_lines
                        WHERE stock_count_id = ?
                        """, userId, stockCountId, stockCountId);
                return diff;
            });
        }
    }

    // ================================================================== UC120, UC121

    /** UC120 danh sách phiếu (lọc trạng thái, loại phiếu). */
    public List<Map<String, Object>> search(String statusInput, String typeInput) throws SQLException {
        String status = upper(statusInput, List.of("DRAFT", "PENDING", "APPROVED", "REJECTED"), "Trạng thái phiếu");
        String type = upper(typeInput, List.of("COUNT", "ADJUSTMENT"), "Loại phiếu");
        try (Connection connection = open()) {
            return InventorySupport.list(connection, """
                    SELECT c.stock_count_id, c.count_type, c.status, c.created_at, c.created_by,
                           u.full_name AS created_by_name, c.reviewed_at, rv.full_name AS reviewed_by_name, c.note,
                           COUNT(cl.count_line_id) AS line_count,
                           COUNT(cl.count_line_id) FILTER (WHERE cl.counted_qty <> cl.system_qty) AS diff_line_count,
                           COALESCE(SUM(cl.counted_qty - cl.system_qty), 0) AS diff_qty
                    FROM stock_counts c
                    JOIN users u ON u.user_id = c.created_by
                    LEFT JOIN users rv ON rv.user_id = c.reviewed_by
                    LEFT JOIN stock_count_lines cl ON cl.stock_count_id = c.stock_count_id
                    WHERE (CAST(? AS TEXT) IS NULL OR c.status = ?)
                      AND (CAST(? AS TEXT) IS NULL OR c.count_type = ?)
                    GROUP BY c.stock_count_id, u.full_name, rv.full_name
                    ORDER BY c.created_at DESC
                    """, status, status, type, type);
        }
    }

    /** UC121 chi tiết phiếu: đầu phiếu + các dòng (chênh lệch SL và giá trị theo giá nhập). */
    public Map<String, Object> detail(long stockCountId) throws SQLException {
        try (Connection connection = open()) {
            Map<String, Object> head = one(connection, """
                    SELECT c.stock_count_id, c.count_type, c.status, c.created_at, c.created_by, c.reviewed_at, c.note,
                           cu.full_name AS created_by_name, rv.full_name AS reviewed_by_name,
                           (SELECT a.detail ->> 'reason' FROM audit_logs a
                             WHERE a.entity_type = 'stock_counts' AND a.entity_id = c.stock_count_id
                               AND a.action = 'REJECT_STOCK_COUNT'
                             ORDER BY a.created_at DESC LIMIT 1) AS reject_reason
                    FROM stock_counts c
                    JOIN users cu ON cu.user_id = c.created_by
                    LEFT JOIN users rv ON rv.user_id = c.reviewed_by
                    WHERE c.stock_count_id = ?
                    """, stockCountId);
            if (head == null) {
                return null;
            }
            head.put("lines", InventorySupport.list(connection, """
                    SELECT cl.count_line_id, cl.lot_id, s.lot_code, p.sku, s.expiry_date,
                           cl.system_qty, cl.counted_qty,
                           cl.counted_qty - cl.system_qty AS diff_qty,
                           (cl.counted_qty - cl.system_qty) * s.unit_cost AS diff_value,
                           s.unit_cost, s.quantity_on_hand AS current_qty,
                           cl.note
                    FROM stock_count_lines cl
                    JOIN v_lot_stock s ON s.lot_id = cl.lot_id
                    JOIN packages p ON p.package_id = s.package_id
                    WHERE cl.stock_count_id = ?
                    ORDER BY p.sku, s.expiry_date
                    """, stockCountId));
            return head;
        }
    }

    // ================================================================== UC122, UC123

    /**
     * UC122 xác nhận điều chỉnh: khóa phiếu PENDING, ghi sổ ADJUSTMENT theo chênh lệch từng lô,
     * chuyển APPROVED và ghi nhật ký — tất cả trong 1 giao dịch.
     * @return số dòng sổ cái đã ghi
     */
    public int approve(long stockCountId, int userId) throws SQLException {
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                requireStatus(c, stockCountId, "PENDING", "Phiếu không ở trạng thái chờ duyệt");   // B1 FOR UPDATE
                int movements = exec(c, """
                        INSERT INTO stock_movements (lot_id, movement_type, quantity_change, stock_count_id, performed_by)
                        SELECT cl.lot_id, 'ADJUSTMENT', cl.counted_qty - cl.system_qty, cl.stock_count_id, ?
                        FROM stock_count_lines cl
                        WHERE cl.stock_count_id = ? AND cl.counted_qty <> cl.system_qty
                        ORDER BY cl.lot_id
                        """, userId, stockCountId);
                exec(c, """
                        UPDATE stock_counts
                        SET status = 'APPROVED', reviewed_by = ?, reviewed_at = now()
                        WHERE stock_count_id = ? AND status = 'PENDING'
                        """, userId, stockCountId);
                exec(c, """
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id)
                        VALUES (?, 'APPROVE_STOCK_COUNT', 'stock_counts', ?)
                        """, userId, stockCountId);
                return movements;
            });
        }
    }

    /** UC123 từ chối điều chỉnh (bắt buộc nhập lý do, lưu vào audit_logs). */
    public void reject(long stockCountId, String reasonInput, int userId) throws SQLException {
        String reason = InventorySupport.requiredNote(reasonInput, "Lý do từ chối", 500);
        try (Connection connection = open()) {
            inTransaction(connection, c -> {
                int changed = exec(c, """
                        UPDATE stock_counts
                        SET status = 'REJECTED', reviewed_by = ?, reviewed_at = now()
                        WHERE stock_count_id = ? AND status = 'PENDING'
                        """, userId, stockCountId);
                if (changed != 1) {
                    throw new IllegalStateException("Phiếu không ở trạng thái chờ duyệt");
                }
                exec(c, """
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        VALUES (?, 'REJECT_STOCK_COUNT', 'stock_counts', ?, jsonb_build_object('reason', CAST(? AS TEXT)))
                        """, userId, stockCountId, reason);
                return null;
            });
        }
    }

    // ================================================================== UC124

    /**
     * UC124 điều chỉnh tồn theo lô: tạo phiếu ADJUSTMENT 1 dòng ở trạng thái chờ duyệt.
     * Chỉ tạo khi SL mới khác tồn hiện tại; lý do bắt buộc.
     * @return mã phiếu
     */
    public long requestAdjustment(long lotId, Integer newQtyInput, String reasonInput, int userId)
            throws SQLException {
        int newQty = InventorySupport.nonNegative(newQtyInput, "Số lượng mới");
        String reason = InventorySupport.requiredNote(reasonInput, "Lý do điều chỉnh", 500);
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                Map<String, Object> lot = one(c, """
                        SELECT s.lot_id, s.lot_code, s.expiry_date, s.quantity_on_hand
                        FROM v_lot_stock s
                        WHERE s.lot_id = ?
                        """, lotId);
                if (lot == null) {
                    throw new IllegalArgumentException("Không tìm thấy lô");
                }
                if (((Number) lot.get("quantityOnHand")).longValue() == newQty) {
                    throw new IllegalArgumentException("Số lượng mới bằng tồn hiện tại, không cần điều chỉnh");
                }
                List<Map<String, Object>> rows = list(c, """
                        WITH c AS (
                          INSERT INTO stock_counts (count_type, status, created_by, note)
                          SELECT 'ADJUSTMENT', 'PENDING', ?, ?
                          WHERE EXISTS (SELECT 1 FROM v_lot_stock
                                        WHERE lot_id = ? AND quantity_on_hand <> ?)
                          RETURNING stock_count_id
                        )
                        INSERT INTO stock_count_lines (stock_count_id, lot_id, system_qty, counted_qty, note)
                        SELECT c.stock_count_id, s.lot_id, s.quantity_on_hand, ?, ?
                        FROM c, v_lot_stock s
                        WHERE s.lot_id = ?
                        RETURNING stock_count_id
                        """, userId, reason, lotId, newQty, newQty, reason, lotId);
                if (rows.isEmpty()) {
                    throw new IllegalStateException("Tồn của lô vừa thay đổi, hãy tải lại và thử lại");
                }
                return ((Number) rows.get(0).get("stockCountId")).longValue();
            });
        }
    }

    // ------------------------------------------------------------------ tiện ích

    /** Khóa phiếu (SELECT ... FOR UPDATE) và kiểm tra trạng thái. */
    private static void requireStatus(Connection connection, long stockCountId, String status, String message)
            throws SQLException {
        Map<String, Object> row = one(connection, """
                SELECT stock_count_id, status FROM stock_counts
                WHERE stock_count_id = ?
                FOR UPDATE
                """, stockCountId);
        if (row == null) {
            throw new IllegalArgumentException("Không tìm thấy phiếu kiểm kê");
        }
        if (!status.equals(row.get("status"))) {
            throw new IllegalStateException(message);
        }
    }

    private static String upper(String value, List<String> allowed, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip().toUpperCase();
        if (!allowed.contains(text)) {
            throw new IllegalArgumentException(label + " không hợp lệ");
        }
        return text;
    }
}
