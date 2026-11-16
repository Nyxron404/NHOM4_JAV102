package models.quangndth08631;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Sửa dòng hàng khi phiếu nhập vẫn ở trạng thái nháp. */
public class UpdatePurchaseInvoiceLine {

    private static final String SQL = """
            UPDATE purchase_invoice_lines pil
            SET package_id = ?, supplier_item_code = ?, supplier_item_name = ?,
                quantity = ?, unit_cost = ?
            FROM purchase_invoices pi
            WHERE pil.purchase_line_id = ?
              AND pi.purchase_invoice_id = pil.purchase_invoice_id
              AND pi.status = 'DRAFT'
            """;

    public boolean update(Connection connection, long purchaseLineId, int packageId,
                          String supplierItemCode, String supplierItemName, int quantity,
                          BigDecimal unitCost) throws SQLException {
        if (quantity < 1 || unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Invalid purchase line values");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, packageId);
            statement.setString(2, supplierItemCode);
            statement.setString(3, supplierItemName);
            statement.setInt(4, quantity);
            statement.setBigDecimal(5, unitCost);
            statement.setLong(6, purchaseLineId);
            return statement.executeUpdate() == 1;
        }
    }
}
