package controllers.minhpvth08530;

/** Lỗi trả về cho trang web kèm mã HTTP (404 không tìm thấy, 401 chưa đăng nhập...). */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
