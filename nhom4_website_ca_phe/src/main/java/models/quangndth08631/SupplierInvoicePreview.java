package models.quangndth08631;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Xem trước dữ liệu hóa đơn đã được QR/lookup parser đọc, chưa ghi dữ liệu. */
public class SupplierInvoicePreview {

    private final CheckDuplicatePurchaseInvoice duplicateCheck =
            new CheckDuplicatePurchaseInvoice();
    private final AutoMatchSupplierItem autoMatch = new AutoMatchSupplierItem();

    public Preview get(Connection connection, int supplierId, String invoiceNo,
                       List<InvoiceLineInput> lines) throws SQLException {
        boolean duplicate = duplicateCheck.exists(connection, supplierId, invoiceNo, null);
        List<LinePreview> previews = new ArrayList<>();
        if (lines != null) {
            for (InvoiceLineInput line : lines) {
                Integer suggestedPackageId = null;
                if (line.supplierItemCode() != null && !line.supplierItemCode().isBlank()) {
                    List<Map<String, Object>> matches = autoMatch.find(
                            connection, supplierId, line.supplierItemCode());
                    if (!matches.isEmpty()) {
                        suggestedPackageId = ((Number) matches.getFirst().get("package_id"))
                                .intValue();
                    }
                }
                previews.add(new LinePreview(line.supplierItemCode(), line.supplierItemName(),
                        line.quantity(), line.unitCost(), suggestedPackageId));
            }
        }
        return new Preview(supplierId, invoiceNo, duplicate, List.copyOf(previews));
    }

    public record InvoiceLineInput(String supplierItemCode, String supplierItemName,
                                   Integer quantity, BigDecimal unitCost) {}

    public record LinePreview(String supplierItemCode, String supplierItemName,
                              Integer quantity, BigDecimal unitCost,
                              Integer suggestedPackageId) {}

    public record Preview(int supplierId, String invoiceNo, boolean duplicate,
                          List<LinePreview> lines) {}
}
