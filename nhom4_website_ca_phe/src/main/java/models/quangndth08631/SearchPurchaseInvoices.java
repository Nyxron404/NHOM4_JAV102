package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tìm và lọc phiếu nhập theo từ khóa, ngày xác nhận và nhà cung cấp. */
public class SearchPurchaseInvoices {

    private static final String SQL = """
            SELECT pit.purchase_invoice_id, pit.invoice_no, pit.status,
                   pit.total_amount, pit.confirmed_at, pit.supplier_id,
                   s.supplier_code, s.supplier_name, pi.invoice_date,
                   pi.created_at
            FROM v_purchase_invoice_totals pit
            JOIN purchase_invoices pi ON pi.purchase_invoice_id = pit.purchase_invoice_id
            JOIN suppliers s ON s.supplier_id = pit.supplier_id
                        WHERE (CAST(? AS integer) IS NULL OR pit.supplier_id = ?)
                              AND (CAST(? AS date) IS NULL OR pi.invoice_date >= ?)
                              AND (CAST(? AS date) IS NULL OR pi.invoice_date <= ?)
                            AND (CAST(? AS text) IS NULL OR pit.invoice_no ILIKE '%' || ? || '%'
                   OR s.supplier_name ILIKE '%' || ? || '%')
            ORDER BY pi.created_at DESC, pit.purchase_invoice_id DESC
            """;

    public List<Map<String, Object>> get(Connection connection, Integer supplierId,
                                         LocalDate from, LocalDate to, String keyword)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL,
                supplierId, supplierId,
                from, from,
                to, to,
                keyword, keyword, keyword);
    }
}
