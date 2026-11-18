// Đường dẫn logo và trang chủ tính theo vị trí file shared.js (dùng được cho cả index.html và pages/*.html)
const LOGO_SRC = new URL("../img/logo.png", document.currentScript.src).href;
const HOME_URL = new URL("../../index.html", document.currentScript.src).href;

function renderHeader() {
    const header = `<!-- HEADER -->
    <header class="header">
        <a href="${HOME_URL}" class="logo" title="Về trang chủ"><img src="${LOGO_SRC}" alt="VCR Roastery"> VIETNAM COFFEE ROASTERY</a>

        <div class="header-right">
            <!-- Thông báo -->
            <a href="thong-bao.html" class="notify">
                🔔
                <span class="notify-count">2</span>
            </a>

            <!-- Tài khoản -->
            <div class="account">
                <div class="account-btn" onclick="toggleMenu()">
                    <div class="avatar">L</div>
                    <span>Tài khoản ▾</span>
                </div>

                <div class="dropdown" id="accountMenu">
                    <div class="dropdown-top">
                        <div class="dropdown-avatar">L</div>
                        <div class="dropdown-name">Đặng Thành Long</div>
                    </div>

                    <a href="ho-so.html" class="dropdown-item">
                        <div class="item-left">
                            <div class="item-icon">👤</div>
                            <div>
                                <div class="item-title">Hồ sơ cá nhân</div>
                                <div class="item-sub">Thông tin cá nhân</div>
                            </div>
                        </div>
                        <span>›</span>
                    </a>

                    <div class="dropdown-bottom">
                        <a href="dang-nhap.html" class="logout-btn">Đăng xuất</a>
                    </div>
                </div>
            </div>
        </div>
    </header>`
    const el = document.getElementById("header");
    if (el) el.innerHTML = header;   // trang nào không có <div id="header"> thì bỏ qua
}
renderHeader();

// Mở/đóng menu "Tài khoản". Trang nào đã tự viết toggleMenu thì giữ nguyên bản của trang đó;
// các trang chưa có (Bán hàng, Hóa đơn) thì dùng bản này để bấm "Tài khoản" không bị lỗi.
if (typeof window.toggleMenu !== "function") {
    window.toggleMenu = function () {
        const menu = document.getElementById("accountMenu");
        if (menu) menu.classList.toggle("show");
    };
    window.addEventListener("click", function (e) {
        const menu = document.getElementById("accountMenu");
        if (menu && !e.target.closest(".account")) menu.classList.remove("show");
    });
}
function renderHome() {
    const home = `<!-- Nút về trang chủ -->
      <div class="home-bar">
        <a href="../index.html" class="home-btn">🏠 Trang chủ</a>
      </div>`
      const el = document.getElementById("home");
      if (el) el.innerHTML = home;   // index.html không có <div id="home"> nên phải kiểm tra
}
renderHome();