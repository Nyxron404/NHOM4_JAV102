package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Lấy thông tin phiếu nhập cùng các dòng hàng, lô và tồn của từng lô. */
public class GetPurchaseInvoiceDetail {

    private static final String SQL = """
            SELECT pit.purchase_invoice_id, pit.invoice_no, pit.status,
                   pit.total_amount, pit.confirmed_at, pit.supplier_id,
                   s.supplier_code, s.supplier_name, pi.invoice_date,
                   pi.input_method, pi.created_by, pi.created_at,
                   pil.purchase_line_id, pil.line_no, pil.package_id,
                   p.sku, pil.supplier_item_code, pil.supplier_item_name,
                   pil.quantity, pil.unit_cost,
                   l.lot_id, l.lot_code, l.roast_date, l.expiry_date,
                   ls.quantity_on_hand
            FROM v_purchase_invoice_totals pit
            JOIN purchase_invoices pi ON pi.purchase_invoice_id = pit.purchase_invoice_id
            JOIN suppliers s ON s.supplier_id = pit.supplier_id
            LEFT JOIN purchase_invoice_lines pil
                ON pil.purchase_invoice_id = pit.purchase_invoice_id
            LEFT JOIN packages p ON p.package_id = pil.package_id
            LEFT JOIN lots l ON l.purchase_line_id = pil.purchase_line_id
            LEFT JOIN v_lot_stock ls ON ls.lot_id = l.lot_id
            WHERE pit.purchase_invoice_id = ?
            ORDER BY pil.line_no
            """;

    public List<Map<String, Object>> get(Connection connection, long purchaseInvoiceId)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, purchaseInvoiceId);
    }
}
