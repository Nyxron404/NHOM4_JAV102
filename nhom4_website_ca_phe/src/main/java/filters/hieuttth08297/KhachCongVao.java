package filters.hieuttth08297;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class KhachCongVao implements Filter {

    private static final String PAGE = "/pages/hieuttth08297/hieuttth08297.html";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        String target = null;
        boolean pageRequest = false;
        if (path.equals("/p") || path.startsWith("/p/")) {
            target = PAGE;
            pageRequest = true;
        } else if (path.equals("/api/khach/san-pham")) {
            target = "/khach-noi-bo/san-pham";
        } else if (path.equals("/api/khach/bao-loi")) {
            target = "/khach-noi-bo/bao-loi";
        }

        if (target == null) {
            chain.doFilter(request, response);
            return;
        }
        if (pageRequest) {
            if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            resp.setHeader("Cache-Control", "no-cache, must-revalidate");
        }
        req.getRequestDispatcher(target).forward(request, response);
    }
}