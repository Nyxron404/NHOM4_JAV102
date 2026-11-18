package dao.minhpvth08530;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tài khoản, đăng nhập, đăng xuất, mật khẩu và nhật ký — UC1..UC17 (Phạm Văn Minh).
 * <p>Câu SQL bám theo cột "Truy vấn SQL" trong file phân công. Kết quả trả về dạng Map
 * (khóa camelCase) để controller chuyển thẳng sang JSON, không trả password_hash.</p>
 */
public class AccountDAO {

    /** Quyền dùng để vào trang quản lý tài khoản (UC8..UC17). */
    public static final String MANAGE_PERMISSION = "USER_MANAGE";

    private static final String USERNAME = "^[a-z0-9_.]{3,50}$";
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String PHONE = "^(\\+84|0)[0-9]{9,10}$";
    /** Băm giả để đăng nhập sai username mất thời gian như sai mật khẩu (không đoán được username). */
    private static final String DUMMY_HASH = PasswordHasher.hash("khong-dung-mat-khau-nay-1");

    // ================================================================== UC1 đăng nhập

    /**
     * UC1 - Đăng nhập. Đúng mật khẩu và tài khoản ACTIVE thì ghi lịch sử, cập nhật lần đăng nhập cuối
     * và trả thông tin phiên (kèm danh sách quyền). Sai thì ghi lịch sử thất bại rồi báo lỗi.
     */
    public Map<String, Object> login(String usernameInput, String password, String ip, String userAgent)
            throws SQLException {
        String username = usernameInput == null ? "" : usernameInput.strip().toLowerCase();
        if (username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Nhập tên đăng nhập và mật khẩu");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> user = one(connection, """
                    SELECT u.user_id, u.username, u.password_hash, u.full_name, u.email, u.status,
                           u.role_id, r.role_code, r.role_name
                    FROM users u
                    JOIN roles r ON r.role_id = u.role_id
                    WHERE u.username = ?
                    """, username);
            String storedHash = user == null ? DUMMY_HASH : (String) user.remove("passwordHash");
            boolean passwordOk = PasswordHasher.verify(password, storedHash) && user != null;

            if (!passwordOk || !"ACTIVE".equals(user.get("status"))) {
                // B3b: username có thật -> lưu user_id; không có -> lưu attempted_username
                exec(connection, """
                        INSERT INTO login_histories (user_id, attempted_username, is_success, ip_address, user_agent)
                        SELECT u.user_id, CASE WHEN u.user_id IS NULL THEN ? END,
                               false, CAST(? AS INET), ?
                        FROM (SELECT 1) d
                        LEFT JOIN users u ON u.username = ?
                        """, truncate(username, 50), ip, truncate(userAgent, 255), username);
                if (passwordOk) {
                    throw new IllegalStateException("Tài khoản đã bị khóa, hãy liên hệ chủ cửa hàng");
                }
                throw new IllegalArgumentException("Sai tên đăng nhập hoặc mật khẩu");
            }

            int userId = (Integer) user.get("userId");
            user.put("permissions", permissionCodes(connection, (Integer) user.get("roleId")));
            // B3a: ghi lịch sử + cập nhật lần đăng nhập cuối trong một giao dịch
            long loginId = DaoSupport.inTransaction(connection, c -> {
                long id;
                try (PreparedStatement statement = c.prepareStatement("""
                        INSERT INTO login_histories (user_id, is_success, ip_address, user_agent)
                        VALUES (?, true, CAST(? AS INET), ?)
                        RETURNING login_id
                        """)) {
                    statement.setInt(1, userId);
                    statement.setString(2, ip);
                    statement.setString(3, truncate(userAgent, 255));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        id = resultSet.getLong(1);
                    }
                }
                exec(c, "UPDATE users SET last_login_at = now() WHERE user_id = ?", userId);
                return id;
            });
            user.put("loginId", loginId);
            return user;
        }
    }

    // ================================================================== UC2 đăng xuất

    /** UC2 - Đóng phiên đăng nhập hiện tại (chỉ phiên thành công, chưa đăng xuất, của đúng người dùng). */
    public boolean logout(long loginId, int userId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return exec(connection, """
                    UPDATE login_histories
                    SET logged_out_at = now()
                    WHERE login_id = ? AND user_id = ? AND is_success AND logged_out_at IS NULL
                    """, loginId, userId) == 1;
        }
    }

    // ================================================================== UC3, UC4 quên / đặt lại mật khẩu

    /**
     * UC3 - Tạo mã khôi phục cho email (B1 + B2). Trả null nếu email không thuộc tài khoản ACTIVE nào;
     * controller vẫn báo "đã gửi nếu email tồn tại" để không lộ email.
     * @return map gồm userId, fullName, email, token (mã gốc để gửi email, DB chỉ lưu bản băm)
     */
    public Map<String, Object> createResetToken(String emailInput) throws SQLException {
        String email = optionalEmail(emailInput);
        if (email == null) {
            throw new IllegalArgumentException("Nhập email");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> user = one(connection, """
                    SELECT user_id, full_name, email
                    FROM users
                    WHERE email = ? AND status = 'ACTIVE'
                    """, email);
            if (user == null) {
                return null;
            }
            String token = PasswordHasher.randomToken();
            int userId = (Integer) user.get("userId");
            DaoSupport.inTransaction(connection, c -> {
                exec(c, """
                        UPDATE password_reset_tokens
                        SET expires_at = now()
                        WHERE user_id = ? AND used_at IS NULL AND expires_at > now()
                        """, userId);
                exec(c, """
                        INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                        VALUES (?, ?, now() + INTERVAL '15 minutes')
                        """, userId, PasswordHasher.sha256(token));
                return null;
            });
            user.put("token", token);
            return user;
        }
    }

    /** UC3 B3 - Ghi nhật ký email sau khi gửi (SENT / FAILED). */
    public void logEmail(Integer userId, String toEmail, String templateCode, String providerMessageId,
                         boolean sent, String errorMessage) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            exec(connection, """
                    INSERT INTO email_logs (user_id, to_email, template_code, provider_message_id, status, error_message)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, toEmail.toLowerCase(), templateCode, truncate(providerMessageId, 100),
                    sent ? "SENT" : "FAILED", sent ? null : truncate(errorMessage, 500));
        }
    }

    /** UC4 B1 - Mã đặt lại còn hiệu lực thì trả username để hiện trên form, không thì null. */
    public String checkResetToken(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = one(connection, """
                    SELECT u.username
                    FROM password_reset_tokens t
                    JOIN users u ON u.user_id = t.user_id
                    WHERE t.token_hash = ? AND t.used_at IS NULL AND t.expires_at > now()
                      AND u.status = 'ACTIVE'
                    """, PasswordHasher.sha256(token.strip()));
            return row == null ? null : (String) row.get("username");
        }
    }

    /** UC4 B2 - Dùng mã (chỉ 1 lần), đổi mật khẩu, vô hiệu mọi mã còn lại của người dùng. */
    public void resetPassword(String token, String newPassword) throws SQLException {
        PasswordHasher.validate(newPassword);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã đặt lại mật khẩu");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Integer userId = DaoSupport.inTransaction(connection, c -> {
                Map<String, Object> row = one(c, """
                        WITH t AS (
                            UPDATE password_reset_tokens
                            SET used_at = now()
                            WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                            RETURNING user_id
                        ), u AS (
                            UPDATE users
                            SET password_hash = ?
                            WHERE user_id IN (SELECT user_id FROM t) AND status = 'ACTIVE'
                            RETURNING user_id
                        )
                        SELECT user_id FROM u
                        """, PasswordHasher.sha256(token.strip()), PasswordHasher.hash(newPassword));
                if (row == null) {
                    return null;
                }
                int id = (Integer) row.get("userId");
                exec(c, """
                        UPDATE password_reset_tokens SET used_at = now()
                        WHERE user_id = ? AND used_at IS NULL
                        """, id);
                return id;
            });
            if (userId == null) {
                throw new IllegalStateException("Liên kết đặt lại mật khẩu đã hết hạn hoặc đã được dùng");
            }
        }
    }

    // ================================================================== UC5, UC6, UC7 tài khoản của tôi

    /** UC5 - Đổi mật khẩu: kiểm tra mật khẩu hiện tại rồi lưu mật khẩu mới (đã băm). */
    public void changePassword(int userId, String currentPassword, String newPassword) throws SQLException {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new IllegalArgumentException("Nhập mật khẩu hiện tại");
        }
        PasswordHasher.validate(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = one(connection,
                    "SELECT password_hash FROM users WHERE user_id = ? AND status = 'ACTIVE'", userId);
            if (row == null) {
                throw new IllegalStateException("Tài khoản không còn hoạt động");
            }
            if (!PasswordHasher.verify(currentPassword, (String) row.get("passwordHash"))) {
                throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
            }
            exec(connection,
                    "UPDATE users SET password_hash = ? WHERE user_id = ? AND status = 'ACTIVE'",
                    PasswordHasher.hash(newPassword), userId);
        }
    }

    /** UC6 - Thông tin tài khoản + vai trò + các quyền của vai trò. */
    public Map<String, Object> profile(int userId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> user = one(connection, """
                    SELECT u.user_id, u.username, u.full_name, u.email, u.phone, u.status,
                           u.created_at, u.last_login_at, u.role_id, r.role_code, r.role_name
                    FROM users u
                    JOIN roles r ON r.role_id = u.role_id
                    WHERE u.user_id = ?
                    """, userId);
            if (user == null) {
                return null;
            }
            user.put("permissionList", list(connection, """
                    SELECT p.module, p.permission_code, p.permission_name
                    FROM users u
                    JOIN role_permissions rp ON rp.role_id = u.role_id
                    JOIN permissions p ON p.permission_id = rp.permission_id
                    WHERE u.user_id = ?
                    ORDER BY p.module, p.permission_name
                    """, userId));
            return user;
        }
    }

    /** UC7 - Cập nhật họ tên, email, SĐT của chính mình (không đổi username, vai trò, trạng thái). */
    public void updateProfile(int userId, String fullName, String email, String phone) throws SQLException {
        updateContact(userId, fullName, email, phone, null);
    }

    // ================================================================== UC8..UC15 quản lý tài khoản

    /** UC8 + UC9 - Danh sách / tìm kiếm tài khoản, phân trang. Tham số null = không lọc. */
    public Map<String, Object> searchUsers(String keyword, Integer roleId, String status, int page, int size)
            throws SQLException {
        String key = blankToNull(keyword);
        String state = blankToNull(status);
        boolean filtered = key != null || roleId != null || state != null;
        String sql = """
                SELECT u.user_id, u.username, u.full_name, u.email, u.phone, u.status,
                       u.role_id, r.role_name, r.role_code, u.created_at, u.last_login_at,
                       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.permission_id = rp.permission_id
                               WHERE rp.role_id = u.role_id AND p.permission_code = 'USER_MANAGE') AS is_manager,
                       COUNT(*) OVER () AS total_rows
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                WHERE (CAST(? AS TEXT) IS NULL
                       OR f_search_key(u.full_name) LIKE '%' || f_search_key(CAST(? AS TEXT)) || '%'
                       OR u.username ILIKE '%' || CAST(? AS TEXT) || '%'
                       OR u.email ILIKE '%' || CAST(? AS TEXT) || '%'
                       OR u.phone LIKE '%' || CAST(? AS TEXT) || '%')
                  AND (CAST(? AS INT) IS NULL OR u.role_id = CAST(? AS INT))
                  AND (CAST(? AS TEXT) IS NULL OR u.status = CAST(? AS TEXT))
                """ + (filtered ? "ORDER BY u.full_name, u.user_id" : "ORDER BY u.created_at DESC, u.user_id DESC")
                + " LIMIT ? OFFSET ?";
        try (Connection connection = DaoSupport.openConnection()) {
            List<Map<String, Object>> rows = list(connection, sql, key, key, key, key, key,
                    intParam(roleId), intParam(roleId), state, state, size, (page - 1) * size);
            return page(rows, page, size);
        }
    }

    /** UC11 B1 - Một tài khoản (dữ liệu lên form). */
    public Map<String, Object> findUser(int userId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return one(connection, """
                    SELECT u.user_id, u.username, u.full_name, u.email, u.phone, u.status,
                           u.role_id, r.role_name, r.role_code, u.created_at, u.last_login_at
                    FROM users u
                    JOIN roles r ON r.role_id = u.role_id
                    WHERE u.user_id = ?
                    """, userId);
        }
    }

    /** UC10 B1 + UC15 B1 - Danh sách vai trò kèm số quyền. */
    public List<Map<String, Object>> roles() throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return list(connection, """
                    SELECT r.role_id, r.role_code, r.role_name, r.description,
                           COUNT(rp.permission_id) AS permission_count
                    FROM roles r
                    LEFT JOIN role_permissions rp ON rp.role_id = r.role_id
                    GROUP BY r.role_id
                    ORDER BY r.role_name
                    """);
        }
    }

    /** UC15 B3 - Xem trước quyền của một vai trò. */
    public List<Map<String, Object>> rolePermissions(int roleId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return list(connection, """
                    SELECT p.module, p.permission_code, p.permission_name
                    FROM role_permissions rp
                    JOIN permissions p ON p.permission_id = rp.permission_id
                    WHERE rp.role_id = ?
                    ORDER BY p.module, p.permission_name
                    """, roleId);
        }
    }

    /** UC10 - Thêm tài khoản + ghi nhật ký thao tác. */
    public int createUser(int adminId, Integer roleId, String usernameInput, String password,
                          String fullNameInput, String emailInput, String phoneInput) throws SQLException {
        String username = usernameInput == null ? null : usernameInput.strip().toLowerCase();
        if (username == null || !username.matches(USERNAME)) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ thường không dấu, số, dấu _ và .");
        }
        if (roleId == null) {
            throw new IllegalArgumentException("Chọn vai trò");
        }
        PasswordHasher.validate(password);
        String fullName = DaoSupport.requiredText(fullNameInput, "Họ tên", 200);
        String email = optionalEmail(emailInput);
        String phone = optionalPhone(phoneInput);
        try (Connection connection = DaoSupport.openConnection()) {
            if (DaoSupport.exists(connection, "SELECT EXISTS (SELECT 1 FROM users WHERE username = ?)", username)) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
            }
            if (email != null && DaoSupport.exists(connection,
                    "SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)", email)) {
                throw new IllegalArgumentException("Email đã được tài khoản khác sử dụng");
            }
            Map<String, Object> row = one(connection, """
                    WITH u AS (
                        INSERT INTO users (role_id, username, password_hash, full_name, email, phone)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING user_id, username, role_id
                    )
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                    SELECT ?, 'CREATE_USER', 'users', u.user_id,
                           jsonb_build_object('username', u.username, 'role_id', u.role_id)
                    FROM u
                    RETURNING entity_id
                    """, roleId, username, PasswordHasher.hash(password), fullName, email, phone, adminId);
            return ((Number) row.get("entityId")).intValue();
        }
    }

    /** UC11 - Sửa họ tên, email, SĐT của một tài khoản + ghi nhật ký. */
    public void updateUser(int adminId, int userId, String fullName, String email, String phone)
            throws SQLException {
        updateContact(userId, fullName, email, phone, adminId);
    }

    /** UC12 - Khóa tài khoản đang ACTIVE (không tự khóa mình) + ghi nhật ký lý do. */
    public void lockUser(int adminId, int userId, String reason) throws SQLException {
        if (adminId == userId) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản của mình");
        }
        String text = DaoSupport.requiredText(reason, "Lý do khóa", 500);
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = one(connection, """
                    WITH u AS (
                        UPDATE users SET status = 'LOCKED'
                        WHERE user_id = ? AND status = 'ACTIVE' AND user_id <> ?
                        RETURNING user_id
                    )
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                    SELECT ?, 'LOCK_USER', 'users', u.user_id, jsonb_build_object('reason', CAST(? AS TEXT))
                    FROM u
                    RETURNING entity_id
                    """, userId, adminId, adminId, text);
            if (row == null) {
                throw new IllegalStateException("Tài khoản không tồn tại hoặc đã bị khóa");
            }
        }
    }

    /** UC13 - Mở khóa tài khoản đang LOCKED + ghi nhật ký lý do. */
    public void unlockUser(int adminId, int userId, String reason) throws SQLException {
        String text = DaoSupport.requiredText(reason, "Lý do mở khóa", 500);
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = one(connection, """
                    WITH u AS (
                        UPDATE users SET status = 'ACTIVE'
                        WHERE user_id = ? AND status = 'LOCKED'
                        RETURNING user_id
                    )
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                    SELECT ?, 'UNLOCK_USER', 'users', u.user_id, jsonb_build_object('reason', CAST(? AS TEXT))
                    FROM u
                    RETURNING entity_id
                    """, userId, adminId, text);
            if (row == null) {
                throw new IllegalStateException("Tài khoản không tồn tại hoặc không bị khóa");
            }
        }
    }

    /**
     * UC14 - Đặt mật khẩu tạm cho tài khoản, thu hồi mã quên mật khẩu còn hiệu lực, ghi nhật ký.
     * @return mật khẩu tạm (chỉ hiện một lần cho chủ cửa hàng, DB chỉ lưu bản băm)
     */
    public String adminResetPassword(int adminId, int userId) throws SQLException {
        String temporary = PasswordHasher.randomPassword();
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = DaoSupport.inTransaction(connection, c -> one(c, """
                    WITH u AS (
                        UPDATE users SET password_hash = ?
                        WHERE user_id = ?
                        RETURNING user_id
                    ), rv AS (
                        UPDATE password_reset_tokens SET used_at = now()
                        WHERE user_id IN (SELECT user_id FROM u) AND used_at IS NULL
                        RETURNING token_id
                    )
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id)
                    SELECT ?, 'RESET_USER_PASSWORD', 'users', u.user_id FROM u
                    RETURNING entity_id
                    """, PasswordHasher.hash(temporary), userId, adminId));
            if (row == null) {
                throw new IllegalArgumentException("Không tìm thấy tài khoản");
            }
        }
        return temporary;
    }

    /**
     * Form "Thêm mới / Cập nhật nhân viên": lưu một lần tên đăng nhập, mật khẩu (tùy chọn khi sửa),
     * họ tên, email, SĐT, "Cho phép đăng nhập?" (ACTIVE/LOCKED) và "Quyền admin/chủ cửa hàng?"
     * (vai trò có / không có quyền USER_MANAGE). Mỗi thay đổi ghi audit_logs như UC10..UC15.
     * @param userId null = thêm mới
     * @return map: id, sessionChanged (true nếu phải buộc tài khoản đăng nhập lại)
     */
    public Map<String, Object> saveUser(int adminId, Integer userId, String usernameInput, String password,
                                        String fullNameInput, String emailInput, String phoneInput,
                                        boolean active, boolean manager) throws SQLException {
        boolean creating = userId == null;
        String username = usernameInput == null ? null : usernameInput.strip().toLowerCase();
        if (username == null || !username.matches(USERNAME)) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ thường không dấu, số, dấu _ và .");
        }
        boolean newPassword = password != null && !password.isEmpty();
        if (creating && !newPassword) {
            throw new IllegalArgumentException("Nhập mật khẩu cho nhân viên mới");
        }
        if (newPassword) {
            PasswordHasher.validate(password);
        }
        String fullName = DaoSupport.requiredText(fullNameInput, "Tên", 200);
        String email = optionalEmail(emailInput);
        String phone = optionalPhone(phoneInput);
        if (!creating && userId == adminId && (!active || !manager)) {
            throw new IllegalArgumentException(
                    "Không thể tự bỏ quyền đăng nhập hoặc quyền admin của chính mình");
        }
        int excludeId = creating ? -1 : userId;
        try (Connection connection = DaoSupport.openConnection()) {
            if (DaoSupport.exists(connection,
                    "SELECT EXISTS (SELECT 1 FROM users WHERE username = ? AND user_id <> ?)", username, excludeId)) {
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
            }
            if (email != null && DaoSupport.exists(connection,
                    "SELECT EXISTS (SELECT 1 FROM users WHERE email = ? AND user_id <> ?)", email, excludeId)) {
                throw new IllegalArgumentException("Email đã được tài khoản khác sử dụng");
            }
            Map<String, Object> role = one(connection, """
                    SELECT r.role_id FROM roles r
                    WHERE EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.permission_id = rp.permission_id
                                  WHERE rp.role_id = r.role_id AND p.permission_code = ?) = ?
                    ORDER BY r.role_id LIMIT 1
                    """, MANAGE_PERMISSION, manager);
            if (role == null) {
                throw new IllegalStateException(manager ? "Chưa có vai trò nào có quyền quản lý tài khoản"
                        : "Chưa có vai trò nhân viên (không có quyền quản lý tài khoản)");
            }
            int roleId = (Integer) role.get("roleId");
            String status = active ? "ACTIVE" : "LOCKED";
            String hash = newPassword ? PasswordHasher.hash(password) : null;

            return DaoSupport.inTransaction(connection, c -> {
                Map<String, Object> result = new LinkedHashMap<>();
                if (creating) {
                    Map<String, Object> row = one(c, """
                            INSERT INTO users (role_id, username, password_hash, full_name, email, phone, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            RETURNING user_id
                            """, roleId, username, hash, fullName, email, phone, status);
                    int id = (Integer) row.get("userId");
                    exec(c, """
                            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                            VALUES (?, 'CREATE_USER', 'users', ?, jsonb_build_object('username', CAST(? AS TEXT), 'role_id', ?, 'status', CAST(? AS TEXT)))
                            """, adminId, id, username, roleId, status);
                    result.put("id", id);
                    result.put("sessionChanged", false);
                    return result;
                }
                Map<String, Object> old = one(c,
                        "SELECT username, role_id, status FROM users WHERE user_id = ? FOR UPDATE", userId);
                if (old == null) {
                    throw new IllegalArgumentException("Không tìm thấy tài khoản");
                }
                exec(c, """
                        UPDATE users SET username = ?, full_name = ?, email = ?, phone = ?, role_id = ?, status = ?,
                               password_hash = COALESCE(?, password_hash)
                        WHERE user_id = ?
                        """, username, fullName, email, phone, roleId, status, hash, userId);
                exec(c, """
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        VALUES (?, 'UPDATE_USER', 'users', ?, jsonb_build_object('old_username', CAST(? AS TEXT), 'username', CAST(? AS TEXT)))
                        """, adminId, userId, old.get("username"), username);
                boolean statusChanged = !status.equals(old.get("status"));
                boolean roleChanged = roleId != (Integer) old.get("roleId");
                if (statusChanged) {
                    exec(c, """
                            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                            VALUES (?, ?, 'users', ?, jsonb_build_object('reason', 'Cập nhật trên form nhân viên'))
                            """, adminId, active ? "UNLOCK_USER" : "LOCK_USER", userId);
                }
                if (roleChanged) {
                    exec(c, """
                            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                            VALUES (?, 'CHANGE_USER_ROLE', 'users', ?, jsonb_build_object('old_role_id', ?, 'new_role_id', ?))
                            """, adminId, userId, old.get("roleId"), roleId);
                }
                if (newPassword) {
                    exec(c, "UPDATE password_reset_tokens SET used_at = now() WHERE user_id = ? AND used_at IS NULL", userId);
                    exec(c, """
                            INSERT INTO audit_logs (user_id, action, entity_type, entity_id)
                            VALUES (?, 'RESET_USER_PASSWORD', 'users', ?)
                            """, adminId, userId);
                }
                result.put("id", userId);
                result.put("sessionChanged", statusChanged || roleChanged || newPassword
                        || !username.equals(old.get("username")));
                return result;
            });
        }
    }

    /**
     * Xóa tài khoản chưa phát sinh dữ liệu nghiệp vụ (hóa đơn, nhập kho, kiểm kê, giá bán, nhật ký thao tác).
     * Lịch sử đăng nhập, mã khôi phục, trạng thái đã đọc thông báo của tài khoản xóa cùng; email_logs giữ lại.
     */
    public void deleteUser(int adminId, int userId) throws SQLException {
        if (adminId == userId) {
            throw new IllegalArgumentException("Không thể tự xóa tài khoản của mình");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            DaoSupport.inTransaction(connection, c -> {
                Map<String, Object> user = one(c, "SELECT username FROM users WHERE user_id = ? FOR UPDATE", userId);
                if (user == null) {
                    throw new IllegalArgumentException("Không tìm thấy tài khoản");
                }
                exec(c, "DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
                exec(c, "DELETE FROM notification_reads WHERE user_id = ?", userId);
                exec(c, "DELETE FROM login_histories WHERE user_id = ?", userId);
                exec(c, "UPDATE email_logs SET user_id = NULL WHERE user_id = ?", userId);
                exec(c, "DELETE FROM users WHERE user_id = ?", userId);
                exec(c, """
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        VALUES (?, 'DELETE_USER', 'users', ?, jsonb_build_object('username', CAST(? AS TEXT)))
                        """, adminId, userId, user.get("username"));
                return null;
            });
        } catch (SQLException exception) {
            if ("23503".equals(exception.getSQLState())) {
                throw new IllegalStateException("Nhân viên đã có dữ liệu bán hàng / nhập kho / thao tác hệ thống nên không xóa được. "
                        + "Hãy bỏ tích \"Cho phép đăng nhập?\" để khóa tài khoản.");
            }
            throw exception;
        }
    }

    /** UC15 - Đổi vai trò (không tự đổi vai trò của mình) + ghi nhật ký vai trò cũ/mới. */
    public void changeRole(int adminId, int userId, Integer roleId) throws SQLException {
        if (roleId == null) {
            throw new IllegalArgumentException("Chọn vai trò mới");
        }
        if (adminId == userId) {
            throw new IllegalArgumentException("Không thể tự đổi vai trò của mình");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            Map<String, Object> row = one(connection, """
                    WITH old AS (
                        SELECT user_id, role_id FROM users WHERE user_id = ?
                    ), u AS (
                        UPDATE users SET role_id = ?
                        WHERE user_id = ? AND role_id <> ? AND user_id <> ?
                        RETURNING user_id
                    )
                    INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                    SELECT ?, 'CHANGE_USER_ROLE', 'users', u.user_id,
                           jsonb_build_object('old_role_id', old.role_id, 'new_role_id', CAST(? AS INT))
                    FROM u
                    JOIN old ON old.user_id = u.user_id
                    RETURNING entity_id
                    """, userId, roleId, userId, roleId, adminId, adminId, roleId);
            if (row == null) {
                throw new IllegalStateException("Tài khoản đã có vai trò này hoặc không tồn tại");
            }
        }
    }

    // ================================================================== UC16, UC17 nhật ký

    /** UC16 - Lịch sử đăng nhập, lọc theo người dùng / từ khóa / kết quả / khoảng ngày. */
    public Map<String, Object> loginHistory(Integer userId, String keyword, Boolean success,
                                            LocalDate from, LocalDate to, int page, int size)
            throws SQLException {
        String key = blankToNull(keyword);
        try (Connection connection = DaoSupport.openConnection()) {
            List<Map<String, Object>> rows = list(connection, """
                    SELECT h.login_id,
                           COALESCE(u.username, h.attempted_username) AS username,
                           u.full_name, h.is_success,
                           host(h.ip_address) AS ip_address,
                           h.user_agent, h.logged_in_at, h.logged_out_at,
                           CAST(EXTRACT(EPOCH FROM (h.logged_out_at - h.logged_in_at)) AS BIGINT) AS session_seconds,
                           COUNT(*) OVER () AS total_rows
                    FROM login_histories h
                    LEFT JOIN users u ON u.user_id = h.user_id
                    WHERE (CAST(? AS INT) IS NULL OR h.user_id = CAST(? AS INT))
                      AND (CAST(? AS TEXT) IS NULL
                           OR COALESCE(u.username, h.attempted_username) ILIKE '%' || CAST(? AS TEXT) || '%')
                      AND (CAST(? AS BOOLEAN) IS NULL OR h.is_success = CAST(? AS BOOLEAN))
                      AND (CAST(? AS DATE) IS NULL
                           OR h.logged_in_at >= CAST(? AS DATE)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                      AND (CAST(? AS DATE) IS NULL
                           OR h.logged_in_at < (CAST(? AS DATE) + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                    ORDER BY h.logged_in_at DESC, h.login_id DESC
                    LIMIT ? OFFSET ?
                    """, intParam(userId), intParam(userId), key, key, boolParam(success), boolParam(success),
                    dateParam(from), dateParam(from), dateParam(to), dateParam(to), size, (page - 1) * size);
            return page(rows, page, size);
        }
    }

    /** UC17 - Nhật ký gửi email, lọc theo email / trạng thái / loại mẫu / khoảng ngày. */
    public Map<String, Object> emailLogs(String keyword, String status, String templateCode,
                                         LocalDate from, LocalDate to, int page, int size) throws SQLException {
        String key = blankToNull(keyword);
        String state = blankToNull(status);
        String template = blankToNull(templateCode);
        try (Connection connection = DaoSupport.openConnection()) {
            List<Map<String, Object>> rows = list(connection, """
                    SELECT e.email_log_id, e.to_email, u.full_name, e.template_code, e.status,
                           e.error_message, e.provider_message_id, e.sent_at,
                           COUNT(*) OVER () AS total_rows
                    FROM email_logs e
                    LEFT JOIN users u ON u.user_id = e.user_id
                    WHERE (CAST(? AS TEXT) IS NULL OR e.to_email ILIKE '%' || CAST(? AS TEXT) || '%')
                      AND (CAST(? AS TEXT) IS NULL OR e.status = CAST(? AS TEXT))
                      AND (CAST(? AS TEXT) IS NULL OR e.template_code = CAST(? AS TEXT))
                      AND (CAST(? AS DATE) IS NULL
                           OR e.sent_at >= CAST(? AS DATE)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                      AND (CAST(? AS DATE) IS NULL
                           OR e.sent_at < (CAST(? AS DATE) + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                    ORDER BY e.sent_at DESC, e.email_log_id DESC
                    LIMIT ? OFFSET ?
                    """, key, key, state, state, template, template,
                    dateParam(from), dateParam(from), dateParam(to), dateParam(to), size, (page - 1) * size);
            Map<String, Object> result = page(rows, page, size);
            result.put("templates", list(connection,
                    "SELECT DISTINCT template_code FROM email_logs ORDER BY template_code"));
            return result;
        }
    }

    // ================================================================== hàm phụ

    private void updateContact(int userId, String fullNameInput, String emailInput, String phoneInput,
                               Integer adminId) throws SQLException {
        String fullName = DaoSupport.requiredText(fullNameInput, "Họ tên", 200);
        String email = optionalEmail(emailInput);
        String phone = optionalPhone(phoneInput);
        try (Connection connection = DaoSupport.openConnection()) {
            if (email != null && DaoSupport.exists(connection,
                    "SELECT EXISTS (SELECT 1 FROM users WHERE email = ? AND user_id <> ?)", email, userId)) {
                throw new IllegalArgumentException("Email đã được tài khoản khác sử dụng");
            }
            Map<String, Object> row;
            if (adminId == null) {
                row = one(connection, """
                        UPDATE users SET full_name = ?, email = ?, phone = ?
                        WHERE user_id = ?
                        RETURNING user_id
                        """, fullName, email, phone, userId);
            } else {
                row = one(connection, """
                        WITH u AS (
                            UPDATE users SET full_name = ?, email = ?, phone = ?
                            WHERE user_id = ?
                            RETURNING user_id
                        )
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id)
                        SELECT ?, 'UPDATE_USER', 'users', u.user_id FROM u
                        RETURNING entity_id
                        """, fullName, email, phone, userId, adminId);
            }
            if (row == null) {
                throw new IllegalArgumentException("Không tìm thấy tài khoản");
            }
        }
    }

    private static List<String> permissionCodes(Connection connection, int roleId) throws SQLException {
        List<String> codes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.permission_code
                FROM role_permissions rp
                JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE rp.role_id = ?
                """)) {
            statement.setInt(1, roleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    codes.add(resultSet.getString(1));
                }
            }
        }
        return codes;
    }

    private static String optionalEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String email = value.strip().toLowerCase();
        if (email.length() > 255 || !email.matches(EMAIL)) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        return email;
    }

    private static String optionalPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String phone = value.replaceAll("[\\s.-]", "");
        if (!phone.matches(PHONE)) {
            throw new IllegalArgumentException("Số điện thoại phải bắt đầu bằng 0 hoặc +84 và có 10–11 số");
        }
        return phone;
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows, int page, int size) {
        long total = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("totalRows")).longValue();
        rows.forEach(row -> row.remove("totalRows"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null ? null : (value.length() > max ? value.substring(0, max) : value);
    }

    /** Tham số null cần kiểu rõ ràng cho PostgreSQL. */
    private record Typed(Object value, int sqlType) {}

    private static Typed intParam(Integer value) { return new Typed(value, Types.INTEGER); }
    private static Typed boolParam(Boolean value) { return new Typed(value, Types.BOOLEAN); }
    private static Typed dateParam(LocalDate value) { return new Typed(value, Types.DATE); }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            Object value = parameters[index];
            if (value instanceof Typed typed) {
                if (typed.value() == null) {
                    statement.setNull(index + 1, typed.sqlType());
                } else {
                    statement.setObject(index + 1, typed.value(), typed.sqlType());
                }
            } else if (value == null) {
                statement.setNull(index + 1, Types.VARCHAR);
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    private static List<Map<String, Object>> list(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        Object value = meta.getColumnType(column) == Types.TIMESTAMP_WITH_TIMEZONE
                                || "timestamptz".equals(meta.getColumnTypeName(column))
                                ? resultSet.getObject(column, OffsetDateTime.class)
                                : resultSet.getObject(column);
                        row.put(camel(meta.getColumnLabel(column)), value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private static int exec(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    private static Map<String, Object> one(Connection connection, String sql, Object... parameters)
            throws SQLException {
        List<Map<String, Object>> rows = list(connection, sql, parameters);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String camel(String column) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : column.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else {
                builder.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return builder.toString();
    }
}
