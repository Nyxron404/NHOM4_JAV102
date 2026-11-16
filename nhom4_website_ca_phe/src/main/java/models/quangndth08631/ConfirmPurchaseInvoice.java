package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

/** Xác nhận phiếu nháp, tạo lô và ghi sổ cái nhập kho nguyên tử. */
public class ConfirmPurchaseInvoice {

    private static final String LOCK_INVOICE_SQL = """
            SELECT status
            FROM purchase_invoices
            WHERE purchase_invoice_id = ?
            FOR UPDATE
            """;
    private static final String LINES_SQL = """
            SELECT purchase_line_id, line_no, quantity
            FROM purchase_invoice_lines
            WHERE purchase_invoice_id = ?
            ORDER BY line_no
            """;

    public void confirm(Connection connection, long purchaseInvoiceId, Integer performedBy,
                        Map<Long, LotDates> lotDatesByLine) throws SQLException {
        boolean ownsTransaction = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (ownsTransaction) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }

        try {
            ensureDraftInvoice(connection, purchaseInvoiceId);
            int lineCount = createLotsAndMovements(
                    connection, purchaseInvoiceId, performedBy, lotDatesByLine);
            if (lineCount == 0) {
                throw new IllegalStateException("Cannot confirm an invoice without lines");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE purchase_invoices
                    SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP
                    WHERE purchase_invoice_id = ? AND status = 'DRAFT'
                    """)) {
                statement.setLong(1, purchaseInvoiceId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Purchase invoice changed while being confirmed");
                }
            }
            if (ownsTransaction) {
                connection.commit();
            } else {
                connection.releaseSavepoint(savepoint);
            }
        } catch (SQLException | RuntimeException exception) {
            if (ownsTransaction) {
                connection.rollback();
            } else if (savepoint != null) {
                connection.rollback(savepoint);
            }
            throw exception;
        } finally {
            if (ownsTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    private void ensureDraftInvoice(Connection connection, long purchaseInvoiceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_INVOICE_SQL)) {
            statement.setLong(1, purchaseInvoiceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Purchase invoice not found");
                }
                if (!"DRAFT".equals(result.getString("status"))) {
                    throw new IllegalStateException("Only DRAFT invoices can be confirmed");
                }
            }
        }
    }

    private int createLotsAndMovements(Connection connection, long purchaseInvoiceId,
                                       Integer performedBy, Map<Long, LotDates> lotDatesByLine)
            throws SQLException {
        int lineCount = 0;
        try (PreparedStatement lines = connection.prepareStatement(LINES_SQL);
             PreparedStatement insertLot = connection.prepareStatement("""
                     INSERT INTO lots (purchase_line_id, lot_code, roast_date, expiry_date)
                     VALUES (?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement insertMovement = connection.prepareStatement("""
                     INSERT INTO stock_movements
                         (lot_id, movement_type, quantity_change, performed_by)
                     VALUES (?, 'IMPORT', ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            lines.setLong(1, purchaseInvoiceId);
            try (ResultSet result = lines.executeQuery()) {
                while (result.next()) {
                    long lineId = result.getLong("purchase_line_id");
                    int lineNo = result.getInt("line_no");
                    int quantity = result.getInt("quantity");
                    LotDates dates = lotDatesByLine == null ? null : lotDatesByLine.get(lineId);
                    if (dates == null || dates.expiryDate() == null) {
                        throw new IllegalArgumentException(
                                "Expiry date is required for purchase line " + lineId);
                    }
                    if (dates.roastDate() != null && dates.roastDate().isAfter(dates.expiryDate())) {
                        throw new IllegalArgumentException(
                                "Roast date must not be after expiry date for line " + lineId);
                    }

                    insertLot.setLong(1, lineId);
                    insertLot.setString(2, "PI-" + purchaseInvoiceId + "-" + lineNo);
                    insertLot.setObject(3, dates.roastDate());
                    insertLot.setObject(4, dates.expiryDate());
                    insertLot.executeUpdate();
                    try (ResultSet generatedKeys = insertLot.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            throw new SQLException("Database did not return the new lot id");
                        }
                        insertMovement.setLong(1, generatedKeys.getLong(1));
                    }
                    insertMovement.setInt(2, quantity);
                    if (performedBy == null) {
                        insertMovement.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        insertMovement.setInt(3, performedBy);
                    }
                    insertMovement.executeUpdate();
                    lineCount++;
                }
            }
        }
        return lineCount;
    }

    public record LotDates(LocalDate roastDate, LocalDate expiryDate) {}
}
