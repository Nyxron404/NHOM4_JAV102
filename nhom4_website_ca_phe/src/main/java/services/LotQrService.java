package services;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import uri.DBConnect;

/**
 * Mã QR của LÔ HÀNG (dùng chung: nhập kho tạo, kho xem / xuất ảnh, bán hàng quét).
 *
 * - Nội dung QR = đúng lot_code (màn bán hàng quét ra mã lô).
 * - Ảnh lưu trên Cloudinary (thư mục coffee-shop/lot-qr, public_id = mã lô), link lưu ở lots.qr_url.
 * - Cần biến môi trường CLOUDINARY_URL (giống ảnh biến thể). Chưa cấu hình thì không upload,
 *   lots.qr_url để trống; ảnh vẫn xem / tải được qua /api/lot-qr (tạo trực tiếp).
 */
public final class LotQrService {

    private static final String FOLDER = "coffee-shop/lot-qr";

    static {
        System.setProperty("java.awt.headless", "true");     // vẽ ảnh trên server không có màn hình
    }

    private LotQrService() {}

    /** Ảnh QR (PNG) chứa lotCode, bên dưới in các dòng chữ chú thích (VD mã lô, SKU, HSD). */
    public static byte[] png(String lotCode, int size, String... captions) throws IOException {
        BufferedImage qr;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(lotCode, BarcodeFormat.QR_CODE, size, size, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
            qr = MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IOException("Không tạo được mã QR cho " + lotCode, e);
        }

        int lineHeight = Math.max(16, size / 14);
        int height = size + (captions.length == 0 ? 0 : captions.length * lineHeight + lineHeight / 2);
        BufferedImage image = new BufferedImage(size, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, height);
            g.drawImage(qr, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            int y = size;
            for (int i = 0; i < captions.length; i++) {
                Font font = new Font(Font.SANS_SERIF, i == 0 ? Font.BOLD : Font.PLAIN, lineHeight - 4);
                g.setFont(font);
                FontMetrics fm = g.getFontMetrics();
                String text = captions[i] == null ? "" : captions[i];
                while (fm.stringWidth(text) > size - 8 && font.getSize() > 8) {      // chữ dài thì thu nhỏ
                    font = font.deriveFont(font.getSize2D() - 1);
                    g.setFont(font);
                    fm = g.getFontMetrics();
                }
                y += lineHeight;
                g.drawString(text, (size - fm.stringWidth(text)) / 2, y - 4);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** true = đã cấu hình Cloudinary */
    public static boolean cloudEnabled() {
        return cloudinary() != null;
    }

    /**
     * Tạo + upload QR cho các lô CHƯA có qr_url. purchaseInvoiceId = null -> mọi lô.
     * Gọi SAU khi commit phiếu nhập (upload mạng không nằm trong transaction).
     * @return số lô đã tạo QR
     */
    public static int ensureForPurchaseInvoice(Long purchaseInvoiceId) throws SQLException, IOException {
        Cloudinary cloudinary = cloudinary();
        if (cloudinary == null) return 0;

        List<Object[]> lots = new ArrayList<>();          // {lot_id, lot_code}
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT lo.lot_id, lo.lot_code
                     FROM lots lo
                     JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                     WHERE lo.qr_url IS NULL
                       AND (CAST(? AS BIGINT) IS NULL OR pil.purchase_invoice_id = CAST(? AS BIGINT))
                     ORDER BY lo.lot_id
                     """)) {
            ps.setObject(1, purchaseInvoiceId, java.sql.Types.BIGINT);
            ps.setObject(2, purchaseInvoiceId, java.sql.Types.BIGINT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lots.add(new Object[] {rs.getLong(1), rs.getString(2)});
            }
        }

        int created = 0;
        for (Object[] lot : lots) {
            String lotCode = (String) lot[1];
            Map<?, ?> result = cloudinary.uploader().upload(png(lotCode, 400, lotCode), ObjectUtils.asMap(
                    "folder", FOLDER,
                    "public_id", lotCode.replaceAll("[^A-Za-z0-9_-]", "_"),
                    "overwrite", true,
                    "resource_type", "image"));
            try (Connection con = connect();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE lots SET qr_url = ?, qr_public_id = ? WHERE lot_id = ?")) {
                ps.setString(1, String.valueOf(result.get("secure_url")));
                ps.setString(2, String.valueOf(result.get("public_id")));
                ps.setLong(3, (Long) lot[0]);
                created += ps.executeUpdate();
            }
        }
        return created;
    }

    private static Cloudinary cloudinary() {
        String url = System.getProperty("CLOUDINARY_URL");
        if (url == null || url.isBlank()) url = System.getenv("CLOUDINARY_URL");
        return url == null || url.isBlank() ? null : new Cloudinary(url.strip());
    }

    private static Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database");
        return con;
    }
}