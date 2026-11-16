function renderHeader() {
    const header = `<!-- HEADER -->
    <header class="header">
        <div class="logo"><img src="https://res.cloudinary.com/v8tued67/image/upload/v1794839228/Gemini_Generated_Image_p421a8p421a8p421_1.png"> Tung Tung Tung Robusta</div>

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
    document.getElementById("header").innerHTML = header;
}
renderHeader();
function renderHome() {
    const home = `<!-- Nút về trang chủ -->
      <div class="home-bar">
        <a href="../index.html" class="home-btn">🏠 Trang chủ</a>
      </div>`
      document.getElementById("home").innerHTML = home;
}
renderHome();