package controllers.hieulnmth08470;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Đọc + kiểm tra dữ liệu form, luật bám theo các DOMAIN trong schema:
 * <ul>
 *   <li>d_name / d_note / d_text: chuẩn hóa Unicode NFC, cắt khoảng trắng 2 đầu, không rỗng</li>
 *   <li>d_code: chữ IN HOA không dấu, số, _ - .</li>
 *   <li>d_email (chữ thường), d_phone (+84/0 + 9-10 số), d_tax_code (10 số hoặc 10-3)</li>
 * </ul>
 * Lỗi được gom vào map tên_ô -> thông báo để JSP hiện ngay dưới ô nhập.
 */
public class Form {

    /** Khóa chứa lỗi chung (không gắn với ô nào). */
    public static final String GLOBAL = "_global";

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_.-]*$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^(\\+84|0)[0-9]{9,10}$");
    private static final Pattern TAX_CODE = Pattern.compile("^[0-9]{10}(-[0-9]{3})?$");

    private final HttpServletRequest req;
    private final Map<String, String> errors = new LinkedHashMap<>();

    public Form(HttpServletRequest req) {
        this.req = req;
    }

    /** Chuẩn NFC + cắt khoảng trắng; chuỗi rỗng -> null. */
    public static String clean(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        return s.isEmpty() ? null : s;
    }

    public String raw(String field) {
        return clean(req.getParameter(field));
    }

    /** Tên bắt buộc (d_name, tối đa 200 ký tự). */
    public String name(String field, String label) {
        return requiredText(field, label, 200);
    }

    public String requiredText(String field, String label, int maxLength) {
        String v = raw(field);
        if (v == null) {
            error(field, label + " không được để trống");
        } else if (v.length() > maxLength) {
            error(field, label + " tối đa " + maxLength + " ký tự");
        }
        return v;
    }

    /** Văn bản không bắt buộc; maxLength &lt;= 0 nghĩa là không giới hạn (d_text). */
    public String optionalText(String field, String label, int maxLength) {
        String v = raw(field);
        if (v != null && maxLength > 0 && v.length() > maxLength) {
            error(field, label + " tối đa " + maxLength + " ký tự");
        }
        return v;
    }

    /** Mã nội bộ d_code: tự đổi sang IN HOA, bỏ khoảng trắng 2 đầu. */
    public String code(String field, String label) {
        String v = raw(field);
        if (v == null) {
            error(field, label + " không được để trống");
            return null;
        }
        v = v.toUpperCase();
        if (v.length() > 40) {
            error(field, label + " tối đa 40 ký tự");
        } else if (!CODE.matcher(v).matches()) {
            error(field, label + " chỉ gồm chữ IN HOA không dấu, số và _ - . (bắt đầu bằng chữ hoặc số)");
        }
        return v;
    }

    public String email(String field, String label) {
        String v = raw(field);
        if (v == null) return null;
        v = v.toLowerCase();
        if (v.length() > 255 || !EMAIL.matcher(v).matches()) {
            error(field, label + " không đúng định dạng");
        }
        return v;
    }

    /** Số điện thoại: cho phép gõ có dấu cách/chấm/gạch, lưu dạng chỉ có số. */
    public String phone(String field, String label) {
        String v = raw(field);
        if (v == null) return null;
        v = v.replaceAll("[\\s.\\-()]", "");
        if (!PHONE.matcher(v).matches()) {
            error(field, label + " phải bắt đầu bằng 0 hoặc +84, theo sau 9-10 chữ số");
        }
        return v;
    }

    public String taxCode(String field, String label) {
        String v = raw(field);
        if (v == null) return null;
        v = v.replaceAll("\\s", "");
        if (!TAX_CODE.matcher(v).matches()) {
            error(field, label + " gồm 10 chữ số hoặc 10 số - 3 số (VD: 0312345678-001)");
        }
        return v;
    }

    /** Số nguyên; required = false thì để trống được (trả null). */
    public Integer integer(String field, String label, boolean required, int min, int max) {
        String v = raw(field);
        if (v == null) {
            if (required) error(field, label + " không được để trống");
            return null;
        }
        try {
            int n = Integer.parseInt(v.replace(".", "").replace(",", ""));
            if (n < min || n > max) {
                error(field, label + " phải từ " + min + " đến " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            error(field, label + " phải là số nguyên");
            return null;
        }
    }

    /** Ô chọn (select) bắt buộc trỏ tới 1 khóa ngoại. */
    public Integer select(String field, String label) {
        Integer id = parseId(req.getParameter(field));
        if (id == null) error(field, "Vui lòng chọn " + label);
        return id;
    }

    public boolean checkbox(String field) {
        String v = req.getParameter(field);
        return v != null && (v.equals("on") || v.equals("true") || v.equals("1"));
    }

    public static Integer parseId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int id = Integer.parseInt(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void error(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
