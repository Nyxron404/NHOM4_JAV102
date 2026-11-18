-- ============================================================================
-- Dữ liệu quyền cho phần Tài khoản & phân quyền (UC1–UC17, Phạm Văn Minh)
-- Chạy lại nhiều lần không bị trùng (ON CONFLICT DO NOTHING).
-- Vai trò OWNER / STAFF và 2 tài khoản owner / quay01 đã có sẵn trong DB.
-- Mật khẩu KHÔNG để trong file này: đặt bằng trang "Quản lý tài khoản" -> "Đặt lại MK",
-- hoặc tự băm bằng dao.minhpvth08530.PasswordHasher.hash(...).
-- ============================================================================

INSERT INTO permissions (permission_code, permission_name, module) VALUES
    ('USER_MANAGE',          'Quản lý tài khoản và phân quyền',     'Tài khoản'),
    ('SALES_POS',            'Bán hàng tại quầy',                    'Bán hàng'),
    ('SALES_INVOICE_VIEW',   'Xem và in lại hóa đơn',                'Bán hàng'),
    ('SALES_INVOICE_CANCEL', 'Hủy hóa đơn đã xác nhận',              'Bán hàng'),
    ('PRODUCT_MANAGE',       'Quản lý sản phẩm, gói bán và giá bán', 'Sản phẩm'),
    ('CATALOG_MANAGE',       'Quản lý danh mục và nhà cung cấp',     'Danh mục'),
    ('INVENTORY_MANAGE',     'Nhập kho, kiểm kê và xuất hủy',        'Kho hàng'),
    ('REPORT_VIEW',          'Xem báo cáo thống kê',                 'Thống kê')
ON CONFLICT (permission_code) DO NOTHING;

-- Chủ cửa hàng: toàn bộ quyền
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.role_code = 'OWNER'
ON CONFLICT DO NOTHING;

-- Nhân viên quầy: bán hàng và xem hóa đơn
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r JOIN permissions p ON p.permission_code IN ('SALES_POS', 'SALES_INVOICE_VIEW')
WHERE r.role_code = 'STAFF'
ON CONFLICT DO NOTHING;
