package controllers.hieulnmth08470;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Đổi dữ liệu Java sang chuỗi JSON để gửi cho JavaScript (không cần thư viện Gson/Jackson).
 * Hỗ trợ: null, String, số, Boolean, enum, Map, List/Collection và model (đọc qua các hàm getXxx()).
 * VD: GrowingRegion -> {"regionId":1,"provinceId":1,"regionName":"Cầu Đất",...}
 */
public final class Json {

    private Json() {}

    /** Ghi JSON ra response với mã HTTP (200 OK, 400 lỗi nhập, 404, 409, 500...). */
    public static void send(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(stringify(data));
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        } else if (v instanceof Collection<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            writeBean(sb, v);
        }
    }

    /** Model: mỗi hàm public getXxx() (không tham số) thành 1 thuộc tính "xxx". */
    private static void writeBean(StringBuilder sb, Object bean) {
        sb.append('{');
        boolean first = true;
        for (Method m : bean.getClass().getMethods()) {
            String name = m.getName();
            if (!name.startsWith("get") || name.equals("getClass") || name.length() == 3
                    || m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Object value;
            try {
                value = m.invoke(bean);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Không đọc được " + name, e);
            }
            if (!first) sb.append(',');
            first = false;
            writeString(sb, Character.toLowerCase(name.charAt(3)) + name.substring(4));
            sb.append(':');
            write(sb, value);
        }
        sb.append('}');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");   // an toàn khi chuỗi JSON nằm trong HTML
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
