package filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Bắt trình duyệt hỏi lại server mỗi lần mở trang HTML/CSS/JS.
 * <p>File trong .war mang ngày sửa năm 1980, nên nếu không có header này trình duyệt
 * tự giữ bản cũ rất lâu: sửa giao diện xong mở trang vẫn thấy bản cũ
 * (vd. vào Hóa đơn từ trang chủ bị mất style, phải Ctrl + F5 mới đúng).</p>
 */
@WebFilter(urlPatterns = {"/", "*.html", "*.css", "*.js"})
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("Cache-Control", "no-cache, must-revalidate");
        chain.doFilter(request, response);
    }
}
