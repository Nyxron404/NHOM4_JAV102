package controllers.minhpvth08530;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;

/**
 * Chuyển đối tượng Java sang JSON (dự án chưa có thư viện Gson/Jackson).
 * Hỗ trợ: null, String, số, boolean, enum, ngày giờ java.time, Map, Collection, mảng
 * và JavaBean (đọc theo getter của từng field, đúng thứ tự khai báo).
 */
public final class Json {

    private Json() {}

    public static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        write(builder, value);
        return builder.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(out, text);
        } else if (value instanceof BigDecimal decimal) {
            out.append(decimal.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Enum<?> constant) {
            quote(out, constant.name());
        } else if (value instanceof TemporalAccessor || value instanceof Character) {
            quote(out, value.toString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Collection<?> collection) {
            out.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    out.append(',');
                }
                write(out, java.lang.reflect.Array.get(value, index));
            }
            out.append(']');
        } else {
            writeBean(out, value);
        }
    }

    private static void writeBean(StringBuilder out, Object bean) {
        out.append('{');
        boolean first = true;
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Method getter = getter(bean.getClass(), field);
            if (getter == null) {
                continue;
            }
            Object value;
            try {
                value = getter.invoke(bean);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Không đọc được " + field.getName(), exception);
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            quote(out, field.getName());
            out.append(':');
            write(out, value);
        }
        out.append('}');
    }

    private static Method getter(Class<?> type, Field field) {
        String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method method = type.getMethod(prefix + suffix);
                if (method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // thử tiền tố tiếp theo
            }
        }
        return null;
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }
}
