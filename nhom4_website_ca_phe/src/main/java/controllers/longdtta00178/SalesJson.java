package controllers.longdtta00178;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
// import java.util.Map;
import models.longdtta00178.Payment;
import models.longdtta00178.SalesLineAllocation;
import models.longdtta00178.SalesProductCard;
import models.longdtta00178.SellablePackage;
import models.longdtta00178.SalesInvoice;
import models.longdtta00178.SalesInvoiceLine;

/**
 * Tự ghép chuỗi JSON cho phần bán hàng (dự án chưa có thư viện Gson/Jackson).
 * Viết tay từng trường để dễ đọc: object Java -> chuỗi JSON gửi về cho JavaScript.
 *
 * VD 1 thẻ:
 * {"variantId":13,"variantName":"Robusta Honey",...,"packages":[{"packageId":40,"sku":"FR-CUMGAR-HN-250-WB",...}]}
 */
public final class SalesJson {

    private SalesJson() {}

    /** Danh sách thẻ cà phê -> mảng JSON [ {...}, {...} ] */
    public static String cards(List<SalesProductCard> cards) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(',');
            card(sb, cards.get(i));
        }
        return sb.append(']').toString();
    }

    private static void card(StringBuilder sb, SalesProductCard c) {
        sb.append('{');
        field(sb, "variantId", c.getVariantId()).append(',');
        field(sb, "variantName", c.getVariantName()).append(',');
        field(sb, "productName", c.getProductName()).append(',');
        field(sb, "speciesName", c.getSpeciesName()).append(',');
        field(sb, "regionName", c.getRegionName()).append(',');
        field(sb, "processName", c.getProcessName()).append(',');
        field(sb, "roastName", c.getRoastName()).append(',');
        field(sb, "imageUrl", c.getImageUrl()).append(',');
        field(sb, "flavorNotes", c.getFlavorNotes()).append(',');
        sb.append("\"packages\":[");
        List<SellablePackage> packages = c.getPackages();
        for (int i = 0; i < packages.size(); i++) {
            if (i > 0) sb.append(',');
            pkg(sb, packages.get(i));
        }
        sb.append("]}");
    }

    private static void pkg(StringBuilder sb, SellablePackage p) {
        sb.append('{');
        field(sb, "packageId", p.getPackageId()).append(',');
        field(sb, "sku", p.getSku()).append(',');
        field(sb, "weightG", p.getWeightG()).append(',');
        field(sb, "grindName", p.getGrindName()).append(',');
        field(sb, "wholeBean", p.getWholeBean()).append(',');
        field(sb, "minStockLevel", p.getMinStockLevel()).append(',');
        field(sb, "salePrice", p.getSalePrice()).append(',');
        field(sb, "sellableQty", p.getSellableQty()).append(',');
        field(sb, "nearExpiry", p.getNearExpiry()).append(',');
        field(sb, "lowStock", p.getLowStock()).append(',');
                field(sb, "canSell", p.getCanSell()).append(',');
        field(sb, "matchedLotId", p.getMatchedLotId()).append(",\"lots\":[");
        for (int i = 0; i < p.getLots().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(object(p.getLots().get(i)));
        }
        sb.append("]}");
    }

    /**
     * Danh mục ô lọc -> {"species":[{"id":1,"name":"Arabica"},...],"regions":[...],"roastLevels":[...]}
     */
    public static String filters(Map<String, List<Map<String, Object>>> options) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstGroup = true;
        for (Map.Entry<String, List<Map<String, Object>>> group : options.entrySet()) {
            if (!firstGroup) sb.append(',');
            firstGroup = false;
            text(sb, group.getKey());
            sb.append(":[");
            List<Map<String, Object>> items = group.getValue();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('{');
                field(sb, "id", items.get(i).get("id")).append(',');
                field(sb, "name", items.get(i).get("name"));
                sb.append('}');
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

        /**
     * Đơn đang lập -> {"invoice":{...},"lines":[{...},...]}
     */
        /**
     * Đơn đang lập -> {"invoice":{...},"lines":[...],"payments":[...],"allocations":[...]}
     */
    public static String order(SalesInvoice inv, List<SalesInvoiceLine> lines,
                               List<Payment> payments, List<SalesLineAllocation> allocations) {
        StringBuilder sb = new StringBuilder("{\"invoice\":{");
        field(sb, "salesInvoiceId", inv.getSalesInvoiceId()).append(',');
        field(sb, "invoiceNo", inv.getInvoiceNo()).append(',');
        field(sb, "status", inv.getStatus()).append(',');           // PENDING | PAID | CANCELLED
        field(sb, "createdAt", inv.getCreatedAt()).append(',');     // 2026-11-18T01:30:12+07:00
        field(sb, "cashierName", inv.getCashierName()).append(',');
        field(sb, "totalAmount", inv.getTotalAmount()).append(',');
        field(sb, "paidAmount", inv.getPaidAmount()).append(',');
        field(sb, "dueAmount", inv.getDueAmount());
        sb.append("},\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            SalesInvoiceLine l = lines.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            field(sb, "salesLineId", l.getSalesLineId()).append(',');
            field(sb, "packageId", l.getPackageId()).append(',');
            field(sb, "sku", l.getSku()).append(',');
            field(sb, "variantName", l.getVariantName()).append(',');
            field(sb, "weightG", l.getWeightG()).append(',');
            field(sb, "wholeBean", l.getWholeBean()).append(',');
            field(sb, "quantity", l.getQuantity()).append(',');
            field(sb, "unitPrice", l.getUnitPrice()).append(',');
            field(sb, "lineAmount", l.getLineAmount()).append(',');
            field(sb, "maxQty", l.getMaxQty());
            sb.append('}');
        }
        sb.append("],\"payments\":[");
        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            field(sb, "paymentId", p.getPaymentId()).append(',');
            field(sb, "methodCode", p.getMethodCode()).append(',');
            field(sb, "methodName", p.getMethodName()).append(',');
            field(sb, "amount", p.getAmount()).append(',');
            field(sb, "status", p.getStatus()).append(',');          // PENDING | SUCCESS | FAILED | CANCELLED
            field(sb, "paymentKind", p.getPaymentKind()).append(','); // CHARGE | REFUND
            field(sb, "paidAt", p.getPaidAt());
            sb.append('}');
        }
        sb.append("],\"allocations\":[");
        for (int i = 0; i < allocations.size(); i++) {
            SalesLineAllocation a = allocations.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            field(sb, "salesLineId", a.getSalesLineId()).append(',');
                        field(sb, "lotId", a.getLotId()).append(',');
            field(sb, "scannedQty", a.getScannedQty()).append(',');
            field(sb, "lotStock", a.getLotStock()).append(',');
            field(sb, "variantName", a.getVariantName()).append(',');
            field(sb, "weightG", a.getWeightG()).append(',');
            field(sb, "wholeBean", a.getWholeBean()).append(',');
            field(sb, "lotCode", a.getLotCode()).append(',');
            field(sb, "expiryDate", a.getExpiryDate()).append(',');   // 2026-12-08
            field(sb, "nearExpiry", a.getNearExpiry()).append(',');
            field(sb, "quantity", a.getQuantity());
            sb.append('}');
        }
        return sb.append("]}").toString();
    }

    /** Map phẳng -> {"key":value,...}  (dùng cho chỉ số trong ngày) */
    public static String object(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            field(sb, e.getKey(), e.getValue());
        }
        return sb.append('}').toString();
    }

    /** {"error":"..."} */
    public static String error(String message) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "error", message);
        return sb.append('}').toString();
    }

    /* ---------- Ghi 1 cặp "tên":giá trị ---------- */
    private static StringBuilder field(StringBuilder sb, String name, Object value) {
        text(sb, name);
        sb.append(':');
        if (value == null) {
            sb.append("null");
        } else if (value instanceof BigDecimal number) {
            sb.append(number.toPlainString());          // 120000 thay vì 1.2E+5
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);                           // số, true/false: không có ngoặc kép
        } else {
            text(sb, value.toString());                 // chuỗi: có ngoặc kép
        }
        return sb;
    }

    /** Ghi chuỗi trong ngoặc kép, thoát các ký tự đặc biệt của JSON. */
    private static void text(StringBuilder sb, String s) {
        sb.append('"');
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }
}
