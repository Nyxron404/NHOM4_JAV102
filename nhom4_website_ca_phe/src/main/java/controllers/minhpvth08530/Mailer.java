package controllers.minhpvth08530;

import dao.minhpvth08530.AccountDAO;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gửi email qua Gmail (SMTP, cổng 465 SSL) bằng mật khẩu ứng dụng — không cần thư viện ngoài.
 * <p>Cấu hình trong {@code src/main/resources/mail.properties} (xem mail.properties.example).
 * Chưa cấu hình thì không gửi được: vẫn ghi email_logs trạng thái FAILED và in liên kết ra log server
 * để thử chức năng quên mật khẩu.</p>
 * <p>Gửi chạy nền (job ngầm) để người dùng không phải chờ SMTP; gửi xong ghi email_logs (UC3 B3).</p>
 */
public final class Mailer {

    private static final Logger LOG = Logger.getLogger(Mailer.class.getName());
    private static final ExecutorService JOBS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mailer");
        thread.setDaemon(true);
        return thread;
    });
    private static final Properties CONFIG = loadConfig();

    private Mailer() {}

    /** UC3 - Gửi email đặt lại mật khẩu ở chế độ nền, gửi xong ghi nhật ký. */
    public static void sendPasswordReset(int userId, String fullName, String email, String link) {
        String subject = "Đặt lại mật khẩu - VIETNAM COFFEE ROASTERY";
        String html = """
                <div style="font-family:Arial,sans-serif;color:#2c1e16;max-width:520px">
                  <h2 style="color:#8b5a2b">VIETNAM COFFEE ROASTERY</h2>
                  <p>Xin chào <b>%s</b>,</p>
                  <p>Hệ thống nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                     Bấm nút dưới đây để đặt mật khẩu mới. Liên kết có hiệu lực trong <b>15 phút</b> và chỉ dùng được một lần.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#8b5a2b;color:#fff;
                        border-radius:8px;text-decoration:none;font-weight:bold">Đặt lại mật khẩu</a></p>
                  <p style="color:#8a7a6c;font-size:13px">Nếu bạn không yêu cầu, hãy bỏ qua email này — mật khẩu cũ vẫn dùng được.</p>
                </div>""".formatted(escape(fullName), escape(link));
        JOBS.submit(() -> {
            String messageId = null;
            String error = null;
            try {
                messageId = send(email, subject, html);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                LOG.log(Level.WARNING, "Gửi email đặt lại mật khẩu thất bại: " + error
                        + " | Liên kết để thử: " + link);
            }
            try {
                new AccountDAO().logEmail(userId, email, "FORGOT_PASSWORD", messageId, error == null, error);
            } catch (Exception exception) {
                LOG.log(Level.WARNING, "Không ghi được email_logs", exception);
            }
        });
    }

    /**
     * Gửi một email HTML qua SMTP của Gmail.
     * @return Message-ID của email (lưu vào email_logs.provider_message_id)
     */
    static String send(String to, String subject, String html) throws IOException {
        String username = CONFIG.getProperty("mail.username", "").strip();
        String password = CONFIG.getProperty("mail.app_password", "").replace(" ", "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new IOException("Chưa cấu hình Gmail trong mail.properties");
        }
        String host = CONFIG.getProperty("mail.host", "smtp.gmail.com");
        int port = Integer.parseInt(CONFIG.getProperty("mail.port", "465"));
        String fromName = CONFIG.getProperty("mail.from_name", "VIETNAM COFFEE ROASTERY");
        String messageId = "<" + UUID.randomUUID() + "@" + username.substring(username.indexOf('@') + 1) + ">";

        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port)) {
            socket.setSoTimeout(20_000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));

            expect(reader, 220);
            command(out, reader, "EHLO localhost", 250);
            String auth = Base64.getEncoder().encodeToString(
                    ("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8));
            command(out, reader, "AUTH PLAIN " + auth, 235);
            command(out, reader, "MAIL FROM:<" + username + ">", 250);
            command(out, reader, "RCPT TO:<" + to + ">", 250);
            command(out, reader, "DATA", 354);

            StringBuilder message = new StringBuilder();
            message.append("From: ").append(encodeHeader(fromName)).append(" <").append(username).append(">\r\n");
            message.append("To: <").append(to).append(">\r\n");
            message.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
            message.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
            message.append("Message-ID: ").append(messageId).append("\r\n");
            message.append("MIME-Version: 1.0\r\n");
            message.append("Content-Type: text/html; charset=UTF-8\r\n");
            message.append("Content-Transfer-Encoding: base64\r\n\r\n");
            String body = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(html.getBytes(StandardCharsets.UTF_8));
            message.append(body).append("\r\n.\r\n");
            out.write(message.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            expect(reader, 250);
            command(out, reader, "QUIT", 221);
        }
        return messageId;
    }

    /** Địa chỉ gốc của web để tạo liên kết trong email (mail.properties: app.base_url), null nếu chưa đặt. */
    static String baseUrl() {
        String value = CONFIG.getProperty("app.base_url", "").strip();
        return value.isEmpty() ? null : value.replaceAll("/+$", "");
    }

    private static void command(OutputStream out, BufferedReader reader, String line, int expected)
            throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        expect(reader, expected);
    }

    /** Đọc phản hồi SMTP (có thể nhiều dòng "250-...") và kiểm tra mã. */
    private static void expect(BufferedReader reader, int expected) throws IOException {
        String line;
        do {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("Máy chủ email đóng kết nối");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
        int code = line.length() >= 3 ? Integer.parseInt(line.substring(0, 3)) : -1;
        if (code != expected) {
            if (code == 535) {
                throw new IOException("Gmail từ chối đăng nhập: kiểm tra mail.username và mật khẩu ứng dụng");
            }
            throw new IOException("Máy chủ email trả lỗi: " + line);
        }
    }

    private static String encodeHeader(String text) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream stream = Mailer.class.getClassLoader().getResourceAsStream("mail.properties")) {
            if (stream != null) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Không đọc được mail.properties", exception);
        }
        return properties;
    }
}
