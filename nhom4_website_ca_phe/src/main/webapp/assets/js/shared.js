// Đường dẫn logo và trang chủ tính theo vị trí file shared.js (dùng được cho cả index.html và pages/*.html)
const LOGO_SRC = new URL("../img/logo.png", document.currentScript.src).href;
const HOME_URL = new URL("../../index.html", document.currentScript.src).href;
const PROFILE_URL = new URL("../../pages/minhpvth08530/ho-so.html", document.currentScript.src).href;
const LOGIN_URL = new URL("../../pages/minhpvth08530/dang-nhap.html", document.currentScript.src).href;
const AUTH_API = new URL("../../api/auth", document.currentScript.src).href;

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
                    <div class="avatar"><svg viewBox="0 0 24 24" width="60%" height="60%" fill="currentColor" aria-hidden="true"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5Z"/></svg></div>
                    <span>Tài khoản ▾</span>
                </div>

                <div class="dropdown" id="accountMenu">
                    <div class="dropdown-top">
                        <div class="dropdown-avatar"><svg viewBox="0 0 24 24" width="60%" height="60%" fill="currentColor" aria-hidden="true"><path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5Z"/></svg></div>
                        <div>
                            <div class="dropdown-name" data-user-name>Đang tải...</div>
                            <div class="dropdown-role" data-user-role></div>
                        </div>
                    </div>

                    <a href="${PROFILE_URL}" class="dropdown-item">
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
                        <a href="${LOGIN_URL}" class="logout-btn" onclick="return logout(event)">Đăng xuất</a>
                    </div>
                </div>
            </div>
        </div>
    </header>`
    const el = document.getElementById("header");
    if (el) el.innerHTML = header;   // trang nào không có <div id="header"> thì bỏ qua
}
renderHeader();

// Hiện tên người đang đăng nhập trên header (UC6)
async function refreshHeaderUser() {
    try {
        const response = await fetch(AUTH_API + "?action=me", { cache: "no-store" });
        if (!response.ok) return;
        const me = await response.json();
        const name = (me.fullName || me.username || "").trim();
        document.querySelectorAll("[data-user-name]").forEach(function (el) { el.textContent = name; });
        document.querySelectorAll("[data-user-role]").forEach(function (el) { el.textContent = me.roleName || ""; });
    } catch (e) {
        // không có mạng thì giữ nguyên header
    }
}
window.refreshHeaderUser = refreshHeaderUser;
if (document.getElementById("header")) refreshHeaderUser();

// Đăng xuất (UC2): đóng phiên trên server rồi về trang đăng nhập
function logout(event) {
    if (event) event.preventDefault();
    fetch(AUTH_API, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: "action=logout",
    }).catch(function () {}).finally(function () { location.href = LOGIN_URL; });
    return false;
}
window.logout = logout;

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
        <a href="${HOME_URL}" class="home-btn">🏠 Trang chủ</a>
      </div>`
      const el = document.getElementById("home");
      if (el) el.innerHTML = home;   // index.html không có <div id="home"> nên phải kiểm tra
}
renderHome();