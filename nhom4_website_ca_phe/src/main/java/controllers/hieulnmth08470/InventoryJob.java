package controllers.hieulnmth08470;

import dao.hieulnmth08470.NotificationDAO;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Job nền UC113 (lô hết hạn), UC125 (lô cận hạn), UC126 (tồn thấp) — Lê Nguyễn Minh Hiếu.
 * <p>Chạy 1 phút sau khi server khởi động rồi cứ 60 phút một lần. Chạy nhiều lần trong ngày
 * vẫn không sinh trùng vì câu INSERT có NOT EXISTS và unique index ux_notif_once_per_day.
 * Dừng thread khi tắt ứng dụng để Tomcat không báo rò rỉ.</p>
 */
@WebListener
public class InventoryJob implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(InventoryJob.class.getName());
    private static final long FIRST_DELAY_MINUTES = 1;
    private static final long PERIOD_MINUTES = 60;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "inventory-alert-job");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(InventoryJob::runOnce, FIRST_DELAY_MINUTES, PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    static void runOnce() {
        try {
            Map<String, Object> created = new NotificationDAO().detectAll();
            LOG.info("Quét cảnh báo kho: " + created);
        } catch (Exception exception) {
            // không để lỗi DB làm chết job: lần sau chạy lại
            LOG.log(Level.WARNING, "Quét cảnh báo kho lỗi", exception);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
