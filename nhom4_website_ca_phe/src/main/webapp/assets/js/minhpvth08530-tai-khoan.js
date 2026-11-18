/* =====================================================================
 * Tài khoản, đăng nhập, đăng xuất — phần Phạm Văn Minh (UC1 – UC17)
 * Dùng cho 3 trang (chọn theo <body data-page>):
 *   login    : pages/dang-nhap.html  — UC1 đăng nhập, UC3 quên mật khẩu, UC4 đặt lại mật khẩu
 *   profile  : pages/ho-so.html      — UC5 đổi mật khẩu, UC6 xem thông tin, UC7 cập nhật thông tin
 *   accounts : pages/tai-khoan.html  — UC8..UC17 quản lý nhân viên / tài khoản (chủ cửa hàng)
 * Gọi API: ../api/auth, ../api/accounts
 * ===================================================================== */
(function () {
    "use strict";

    const API = "../../api";
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
    const STATUS_TEXT = { ACTIVE: "Hoạt động", LOCKED: "Đã khóa", SENT: "Đã gửi", FAILED: "Lỗi" };
    const TEMPLATE_TEXT = { FORGOT_PASSWORD: "Quên mật khẩu" };

    /**
     * Ảnh nền trang đăng nhập (tự chuyển 6 giây/ảnh). Ảnh miễn phí từ Unsplash (Unsplash License).
     * Muốn dùng ảnh của quán: chép vào assets/img/login/ rồi thay bằng "../assets/img/login/ten-anh.jpg".
     */
    const LOGIN_SLIDES = [
        "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=1920&q=70",
        "https://images.unsplash.com/photo-1766767164066-d2f4571e75d1?auto=format&fit=crop&w=1920&q=70",
        "https://images.unsplash.com/photo-1743356629215-abb470447e71?auto=format&fit=crop&w=1920&q=70",
        "https://images.unsplash.com/photo-1756158449200-678a8de156ac?auto=format&fit=crop&w=1920&q=70",
    ];

    const EYE = '<svg viewBox="0 0 24 24"><path d="M12 5C6.5 5 2.7 8.6 1 12c1.7 3.4 5.5 7 11 7s9.3-3.6 11-7c-1.7-3.4-5.5-7-11-7Zm0 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Zm0-2.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/></svg>';
    /** Mắt gạch chéo: đang hiện mật khẩu, bấm để ẩn. */
    const EYE_OFF = '<svg viewBox="0 0 24 24"><path d="M12 5C6.5 5 2.7 8.6 1 12c1.7 3.4 5.5 7 11 7s9.3-3.6 11-7c-1.7-3.4-5.5-7-11-7Zm0 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9Zm0-2.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/><path d="M3.3 2 22 20.7 20.7 22 2 3.3Z" stroke="#faf7f2" stroke-width="1.2"/></svg>';

    // ------------------------------------------------------------------ tiện ích

    function esc(value) {
        return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
        }[ch]));
    }

    function dateTime(value) {
        if (!value) return "—";
        return new Date(value).toLocaleString("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric",
            hour: "2-digit", minute: "2-digit",
        });
    }

    function duration(seconds) {
        if (seconds === null || seconds === undefined) return "—";
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        return h ? `${h} giờ ${m} phút` : m ? `${m} phút` : `${seconds} giây`;
    }

    function badge(status) {
        return `<span class="badge badge-${esc(status)}">${esc(STATUS_TEXT[status] || status)}</span>`;
    }

    /** Rút gọn User-Agent thành "Chrome · Windows". */
    function browserName(ua) {
        if (!ua) return "—";
        const browser = /Edg\//.test(ua) ? "Edge" : /coc_coc|CocCoc/i.test(ua) ? "Cốc Cốc" : /Chrome\//.test(ua) ? "Chrome"
            : /Firefox\//.test(ua) ? "Firefox" : /Safari\//.test(ua) ? "Safari" : "Trình duyệt khác";
        const os = /Windows/.test(ua) ? "Windows" : /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS"
            : /Mac OS/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "";
        return os ? `${browser} · ${os}` : browser;
    }

    async function api(path, { params, form } = {}) {
        let url = `${API}/${path}`;
        if (params) {
            const query = new URLSearchParams();
            Object.entries(params).forEach(([key, value]) => {
                if (value !== null && value !== undefined && value !== "") query.append(key, value);
            });
            if (query.toString()) url += "?" + query;
        }
        const options = { method: form ? "POST" : "GET", headers: {} };
        if (form) {
            options.body = new URLSearchParams(form);
            options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
        }
        let response;
        try {
            response = await fetch(url, options);
        } catch (e) {
            throw new Error("Không kết nối được máy chủ");
        }
        let data = null;
        try { data = await response.json(); } catch (e) { data = null; }
        if (response.status === 401 && document.body.dataset.page !== "login") {
            location.href = "dang-nhap.html?next=" + encodeURIComponent(location.pathname + location.search);
            throw new Error("Phiên đăng nhập đã hết");
        }
        if (!response.ok) throw new Error((data && data.error) || `Lỗi ${response.status}`);
        return data;
    }

    function formData(form) {
        const data = {};
        new FormData(form).forEach((value, key) => { data[key] = typeof value === "string" ? value.trim() : value; });
        return data;
    }

    function toast(message, type = "ok") {
        const root = $("#toastRoot");
        if (!root) return;
        const el = document.createElement("div");
        el.className = "toast" + (type === "error" ? " error" : "");
        el.textContent = message;
        root.appendChild(el);
        setTimeout(() => el.remove(), 3600);
    }

    function showError(box, message) {
        box.textContent = message || "";
        box.classList.toggle("show", !!message);
    }

    /** Nút con mắt hiện/ẩn mật khẩu. */
    function bindEyes(root = document) {
        $$("[data-eye]", root).forEach((btn) => {
            const input = btn.parentElement.querySelector("input");
            const paint = () => {
                const shown = input.type === "text";
                btn.innerHTML = shown ? EYE_OFF : EYE;
                btn.title = shown ? "Ẩn mật khẩu" : "Hiện mật khẩu";
            };
            btn.onclick = () => {
                input.type = input.type === "password" ? "text" : "password";
                paint();
            };
            input.type = "password";
            paint();
        });
    }

    /** Hiện ✓ / ✗ cho từng quy tắc mật khẩu khi gõ. */
    function bindRules(root = document) {
        $$("[data-rules-for]", root).forEach((list) => {
            const input = root.querySelector(`[name="${list.dataset.rulesFor}"]`);
            const check = () => {
                const value = input.value;
                const rules = { length: value.length >= 8, mix: /[A-Za-z]/.test(value) && /\d/.test(value) };
                $$("li", list).forEach((li) => {
                    li.classList.toggle("ok", value !== "" && rules[li.dataset.rule]);
                    li.classList.toggle("bad", value !== "" && !rules[li.dataset.rule]);
                });
            };
            input.addEventListener("input", check);
            check();
        });
    }

    function openModal({ title, body, submitText = "Lưu", onSubmit, danger = false, wide = false }) {
        const root = $("#modalRoot");
        root.innerHTML = `
          <div class="modal-backdrop">
            <form class="modal ${wide ? "wide" : ""}" novalidate>
              <div class="modal-head"><h3>${esc(title)}</h3>
                <button type="button" class="modal-close" aria-label="Đóng">×</button></div>
              <div class="modal-body"><div class="form-error"></div>${body}</div>
              <div class="modal-foot">
                <button type="button" class="btn" data-close>${onSubmit ? "Hủy" : "Đóng"}</button>
                ${onSubmit ? `<button type="submit" class="btn ${danger ? "btn-danger" : "btn-primary"}">${esc(submitText)}</button>` : ""}
              </div>
            </form>
          </div>`;
        const form = $("form", root);
        const close = () => { root.innerHTML = ""; };
        $(".modal-close", root).onclick = close;
        $("[data-close]", root).onclick = close;
        $(".modal-backdrop", root).addEventListener("mousedown", (e) => {
            if (e.target.classList.contains("modal-backdrop")) close();
        });
        form.addEventListener("submit", async (e) => {
            e.preventDefault();
            if (!onSubmit) return;
            const button = $("button[type=submit]", form);
            button.disabled = true;
            showError($(".form-error", form), "");
            try {
                const keepOpen = await onSubmit(formData(form), form);
                if (!keepOpen) close();
            } catch (err) {
                showError($(".form-error", form), err.message);
                button.disabled = false;
            }
        });
        bindEyes(form);
        bindRules(form);
        const first = $("input:not([type=hidden]):not([readonly]), select, textarea", form);
        if (first) setTimeout(() => first.focus(), 30);
        return form;
    }

    function debounce(fn, wait) {
        let timer;
        return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), wait); };
    }

    // ================================================================== trang đăng nhập (UC1, UC3, UC4)

    function startSlides() {
        const box = $("#loginSlides");
        const slides = LOGIN_SLIDES.map((url) => {
            const el = document.createElement("div");
            el.className = "slide";
            box.appendChild(el);
            const img = new Image();
            img.onload = () => { el.style.backgroundImage = `url("${url}")`; el.dataset.ready = "1"; };
            img.src = url;
            return el;
        });
        let index = -1;
        const next = () => {
            const ready = slides.filter((s) => s.dataset.ready);
            if (!ready.length) return;
            const current = slides[index];
            let pick = ready[(ready.indexOf(current) + 1) % ready.length];
            slides.forEach((s) => s.classList.toggle("show", s === pick));
            index = slides.indexOf(pick);
        };
        const first = setInterval(() => {      // hiện ngay khi ảnh đầu tiên tải xong
            if (slides.some((s) => s.dataset.ready)) { clearInterval(first); next(); }
        }, 150);
        setInterval(next, 6000);
    }

    function initLogin() {
        startSlides();
        const params = new URLSearchParams(location.search);
        const views = { login: $("#viewLogin"), forgot: $("#viewForgot"), reset: $("#viewReset") };
        const show = (name) => Object.entries(views).forEach(([key, el]) => { el.hidden = key !== name; });
        const message = (el, text, type) => {
            el.textContent = text || "";
            el.className = "auth-msg" + (text ? " show " + type : "");
        };
        $$("[data-view]").forEach((link) => {
            link.onclick = (e) => {
                e.preventDefault();
                show(link.dataset.view);
                $("input", views[link.dataset.view])?.focus();
            };
        });
        bindRules(document);

        // Chỉ quay lại trang trong cùng web (chống chuyển hướng ra trang lạ)
        const next = (() => {
            const value = params.get("next");
            return value && value.startsWith("/") && !value.startsWith("//") ? value : "../../index.html";
        })();

        // UC1
        const loginForm = $("#loginForm");
        loginForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const data = formData(loginForm);
            if (!data.username || !data.password) {
                message($("#loginMsg"), "Nhập tên đăng nhập và mật khẩu", "error");
                return;
            }
            const button = $("button[type=submit]", loginForm);
            button.disabled = true;
            button.textContent = "ĐANG ĐĂNG NHẬP...";
            try {
                await api("auth", { form: { action: "login", username: data.username, password: data.password } });
                location.replace(next);
            } catch (err) {
                message($("#loginMsg"), err.message, "error");
                $("#password").value = "";
                $("#password").focus();
                button.disabled = false;
                button.textContent = "ĐĂNG NHẬP";
            }
        });

        // UC3 — mỗi lần gửi xong phải chờ 15 giây mới bấm lại được (giữ cả khi tải lại trang)
        const forgotForm = $("#forgotForm");
        const forgotButton = $("button[type=submit]", forgotForm);
        const COOLDOWN_MS = 15000;
        const COOLDOWN_KEY = "vcr.forgot.nextAt";
        const readNextAt = () => { try { return Number(sessionStorage.getItem(COOLDOWN_KEY)) || 0; } catch (e) { return 0; } };
        let nextAt = readNextAt();
        let timer = null;
        function tick() {
            const left = Math.ceil((nextAt - Date.now()) / 1000);
            if (left > 0) {
                forgotButton.disabled = true;
                forgotButton.textContent = `GỬI LẠI SAU ${left}s`;
                return;
            }
            clearInterval(timer);
            timer = null;
            forgotButton.disabled = false;
            forgotButton.textContent = "RESET MẬT KHẨU";
        }
        function startCooldown(ms) {
            nextAt = Date.now() + ms;
            try { sessionStorage.setItem(COOLDOWN_KEY, String(nextAt)); } catch (e) { /* bỏ qua */ }
            if (!timer) timer = setInterval(tick, 250);
            tick();
        }
        if (nextAt > Date.now()) startCooldown(nextAt - Date.now());

        forgotForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            if (nextAt > Date.now()) return;
            const data = formData(forgotForm);
            if (!data.email) {
                message($("#forgotMsg"), "Nhập địa chỉ email", "error");
                return;
            }
            forgotButton.disabled = true;
            try {
                const result = await api("auth", { form: { action: "forgot", email: data.email } });
                message($("#forgotMsg"), result.message, "ok");
                startCooldown(COOLDOWN_MS);
            } catch (err) {
                message($("#forgotMsg"), err.message, "error");
                const wait = /(\d+) giây/.exec(err.message);   // server báo còn phải chờ bao lâu
                if (wait) startCooldown(Number(wait[1]) * 1000);
                else tick();
            }
        });

        // UC4: mở từ liên kết trong email (?token=...)
        const token = params.get("token");
        if (token) {
            show("reset");
            api("auth", { params: { action: "checkToken", token } }).then((result) => {
                if (!result.valid) {
                    $("#resetSub").textContent = "Liên kết đã hết hạn hoặc đã được dùng. Hãy yêu cầu liên kết mới ở mục Quên mật khẩu.";
                    return;
                }
                $("#resetSub").innerHTML = `Tài khoản: <b>${esc(result.username)}</b>`;
                $("#resetForm").hidden = false;
                $("#newPassword").focus();
            }).catch((err) => { $("#resetSub").textContent = err.message; });

            const resetForm = $("#resetForm");
            resetForm.addEventListener("submit", async (e) => {
                e.preventDefault();
                const data = formData(resetForm);
                const button = $("button[type=submit]", resetForm);
                button.disabled = true;
                try {
                    const result = await api("auth", { form: { action: "reset", token, ...data } });
                    resetForm.hidden = true;
                    $("#resetSub").textContent = result.message;
                    history.replaceState(null, "", "dang-nhap.html");
                } catch (err) {
                    message($("#resetMsg"), err.message, "error");
                    button.disabled = false;
                }
            });
            return;
        }

        if (location.hash === "#quen-mat-khau") show("forgot");
        // Đã đăng nhập rồi thì vào thẳng trang chính
        api("auth", { params: { action: "me" } }).then(() => location.replace(next)).catch(() => {
            $("#username").focus();
        });
    }

    // ================================================================== thông tin tài khoản (UC5, UC6, UC7)

    async function initProfile() {
        const form = $("#profileForm");
        bindEyes(form);
        bindRules(form);

        async function load() {
            let p;
            try {
                p = await api("auth", { params: { action: "profile" } });
            } catch (err) {
                showError($("#profileError"), err.message);
                return;
            }
            form.username.value = p.username || "";
            form.fullName.value = p.fullName || "";
            form.email.value = p.email || "";
            form.phone.value = p.phone || "";
            form.roleName.value = p.roleName || "";
            const perms = p.permissionList || [];
            $("#permList").innerHTML = perms.map((x) =>
                `<span title="${esc(x.module)} · ${esc(x.permissionCode)}">${esc(x.permissionName)}</span>`).join("")
                || '<span class="acc-hint">Vai trò chưa có quyền nào</span>';
            $("#chkActive").checked = p.status === "ACTIVE";
            $("#chkManager").checked = perms.some((x) => x.permissionCode === "USER_MANAGE");
            $("#loginInfo").textContent = `Tạo ngày ${dateTime(p.createdAt)} · Đăng nhập gần nhất ${dateTime(p.lastLoginAt)}`;
        }

        form.addEventListener("submit", async (e) => {
            e.preventDefault();
            const data = formData(form);
            const button = $("button[type=submit]", form);
            button.disabled = true;
            showError($("#profileError"), "");
            try {
                const changingPassword = data.currentPassword || data.newPassword || data.confirmPassword;
                if (changingPassword) {
                    if (!data.currentPassword) throw new Error("Nhập mật khẩu hiện tại để đổi mật khẩu");
                    if (!data.newPassword) throw new Error("Nhập mật khẩu mới");
                }
                const result = await api("auth", {
                    form: { action: "updateProfile", fullName: data.fullName, email: data.email, phone: data.phone },
                });
                if (changingPassword) {
                    await api("auth", {
                        form: { action: "changePassword", currentPassword: data.currentPassword,
                            newPassword: data.newPassword, confirmPassword: data.confirmPassword },
                    });
                    ["currentPassword", "newPassword", "confirmPassword"].forEach((name) => { form[name].value = ""; });
                    form.newPassword.dispatchEvent(new Event("input"));
                    toast("Đã lưu thông tin và đổi mật khẩu");
                } else {
                    toast(result.message);
                }
                await load();
                window.refreshHeaderUser?.();
            } catch (err) {
                showError($("#profileError"), err.message);
            } finally {
                button.disabled = false;
            }
        });

        await load();
    }

    // ================================================================== quản lý nhân viên (UC8..UC17)

    async function initAccounts() {
        const state = {
            tab: "users", roles: [], me: null, open: null,
            paging: { users: { page: 1, size: 20 }, logins: { page: 1, size: 20 }, emails: { page: 1, size: 20 } },
        };
        const box = $("#tableBox");
        const filters = { users: $("#userFilter"), logins: $("#loginFilter"), emails: $("#emailFilter") };
        const titles = { users: "Quản lý nhân viên", logins: "Lịch sử đăng nhập", emails: "Nhật ký gửi email" };

        try {
            [state.roles, state.me] = await Promise.all([
                api("accounts", { params: { view: "roles" } }),
                api("auth", { params: { action: "me" } }),
            ]);
        } catch (err) {
            box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
            return;
        }
        $("#roleFilter").insertAdjacentHTML("beforeend",
            state.roles.map((r) => `<option value="${r.roleId}">${esc(r.roleName)}</option>`).join(""));

        function openTab(name) {
            state.tab = name;
            $$("#mainTabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
            Object.entries(filters).forEach(([key, el]) => { el.hidden = key !== name; });
            $("#btnAddUser").hidden = name !== "users";
            $("#pageTitle").textContent = titles[name];
            load();
        }
        $$("#mainTabs button").forEach((b) => { b.onclick = () => openTab(b.dataset.tab); });

        Object.entries(filters).forEach(([key, form]) => {
            const reload = () => { state.paging[key].page = 1; load(); };
            form.addEventListener("submit", (e) => { e.preventDefault(); reload(); });
            $$("input[type=search]", form).forEach((el) => el.addEventListener("input", debounce(reload, 300)));
            $$("select, input[type=date]", form).forEach((el) => el.addEventListener("change", reload));
        });

        function load() {
            ({ users: loadUsers, logins: loadLogins, emails: loadEmails })[state.tab]();
        }

        async function fetchPage(view) {
            const paging = state.paging[state.tab];
            return api("accounts", { params: { view, ...formData(filters[state.tab]), page: paging.page, size: paging.size } });
        }

        /** Thanh phân trang kiểu |< < 1 > >|  [20] items per page   1 - 2 of 2 items */
        function pager(result) {
            const paging = state.paging[state.tab];
            const pages = Math.max(1, Math.ceil(result.total / result.size));
            const from = result.total ? (result.page - 1) * result.size + 1 : 0;
            const to = Math.min(result.total, result.page * result.size);
            const wrap = document.createElement("div");
            wrap.className = "staff-pager";
            wrap.innerHTML = `
                <button type="button" data-go="1" ${result.page <= 1 ? "disabled" : ""} title="Trang đầu">⏮</button>
                <button type="button" data-go="${result.page - 1}" ${result.page <= 1 ? "disabled" : ""} title="Trang trước">◀</button>
                <span class="cur">${result.page}</span>
                <button type="button" data-go="${result.page + 1}" ${result.page >= pages ? "disabled" : ""} title="Trang sau">▶</button>
                <button type="button" data-go="${pages}" ${result.page >= pages ? "disabled" : ""} title="Trang cuối">⏭</button>
                <select>${[10, 20, 50, 100].map((n) => `<option ${n === result.size ? "selected" : ""}>${n}</option>`).join("")}</select>
                <span>dòng mỗi trang</span>
                <span class="info">${from} - ${to} / ${result.total} dòng</span>`;
            $$("button", wrap).forEach((b) => { b.onclick = () => { paging.page = Number(b.dataset.go); load(); }; });
            $("select", wrap).onchange = (e) => { paging.size = Number(e.target.value); paging.page = 1; load(); };
            return wrap;
        }

        function render(result, head, rows, empty) {
            box.innerHTML = result.rows.length
                ? `<div class="scroll"><table class="staff"><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table></div>`
                : `<div class="staff-empty">${empty}</div>`;
            box.appendChild(pager(result));
        }

        // ---------- UC8, UC9: danh sách + tìm kiếm (bấm dòng để mở thao tác)
        async function loadUsers() {
            let result;
            try { result = await fetchPage("users"); } catch (err) {
                box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
                return;
            }
            const head = `<th></th><th>Tên đăng nhập</th><th>Tên</th><th>Email</th><th>Điện thoại</th>
                <th class="center">Quyền admin/chủ cửa hàng?</th><th class="center">Cho phép đăng nhập?</th>`;
            const rows = result.rows.map((u) => {
                const self = u.userId === state.me.userId;
                const open = state.open === u.userId;
                return `<tr class="row-main ${open ? "open" : ""}" data-id="${u.userId}">
                    <td class="caret"><span>▶</span></td>
                    <td>${esc(u.username)}${self ? ' <span class="badge badge-ME">Bạn</span>' : ""}</td>
                    <td>${esc(u.fullName)}</td>
                    <td>${esc(u.email || "")}</td>
                    <td>${esc(u.phone || "")}</td>
                    <td class="center"><input type="checkbox" disabled ${u.isManager ? "checked" : ""}></td>
                    <td class="center"><input type="checkbox" disabled ${u.status === "ACTIVE" ? "checked" : ""}></td>
                  </tr>
                  ${open ? `<tr class="row-detail"><td colspan="7">
                    <div class="detail-box">
                      <div class="detail-tabs"><span class="active">Chi tiết</span></div>
                      <table class="detail-table">
                        <tr><th>Tên đăng nhập</th><td>${esc(u.username)}</td><th>Trạng thái</th><td>${badge(u.status)}</td></tr>
                        <tr><th>Tên</th><td>${esc(u.fullName)}</td><th>Vai trò</th><td>${esc(u.roleName)}</td></tr>
                        <tr><th>Điện thoại</th><td>${esc(u.phone || "")}</td><th>Ngày tạo</th><td>${dateTime(u.createdAt)}</td></tr>
                        <tr><th>Email</th><td>${esc(u.email || "")}</td><th>Đăng nhập gần nhất</th><td>${dateTime(u.lastLoginAt)}</td></tr>
                      </table>
                      <div class="detail-buttons">
                        ${self ? "" : `<button type="button" class="btn-big warn" data-act="delete">
                          <svg viewBox="0 0 24 24"><path d="M9 3h6l1 2h4v2H4V5h4l1-2Zm-3 6h12l-1 12H7L6 9Zm4 2v8h1.5v-8H10Zm3.5 0v8H15v-8h-1.5Z"/></svg> Xoá</button>`}
                        <button type="button" class="btn-big primary" data-act="edit">
                          <svg viewBox="0 0 24 24"><path d="M4 17.2V20h2.8l8.3-8.3-2.8-2.8L4 17.2Zm13.7-7.5a1 1 0 0 0 0-1.4L15.7 6.3a1 1 0 0 0-1.4 0l-1.1 1.1 2.8 2.8 1.7-1.5ZM14 18h6v2h-6v-2Z"/></svg> Cập nhật</button>
                      </div>
                    </div></td></tr>` : ""}`;
            }).join("");
            render(result, head, rows, "Không có nhân viên phù hợp.");
            $$("tr.row-main", box).forEach((tr) => {
                tr.onclick = () => {
                    const id = Number(tr.dataset.id);
                    state.open = state.open === id ? null : id;
                    loadUsers();
                };
            });
            const user = result.rows.find((u) => u.userId === state.open);
            $$("[data-act]", box).forEach((btn) => {
                btn.onclick = () => (btn.dataset.act === "edit" ? showEditor(user) : deleteUser(user));
            });
        }

        // ---------- Form Thêm mới / Cập nhật nhân viên (UC10, UC11, UC12, UC13, UC14, UC15 gộp một form)
        const editForm = $("#staffForm");
        bindEyes(editForm);
        bindRules(editForm);
        let editing = null;

        function showEditor(user) {
            editing = user || null;
            const self = !!user && user.userId === state.me.userId;
            $("#editTitle").textContent = user ? "Cập nhật nhân viên" : "Thêm mới nhân viên";
            editForm.reset();
            showError($("#staffError"), "");
            editForm.username.value = user?.username || "";
            editForm.fullName.value = user?.fullName || "";
            editForm.email.value = user?.email || "";
            editForm.phone.value = user?.phone || "";
            editForm.active.checked = user ? user.status === "ACTIVE" : true;
            editForm.manager.checked = user ? !!user.isManager : false;
            editForm.active.disabled = self;
            editForm.manager.disabled = self;
            $("#selfHint").hidden = !self;
            $("#pwReq").hidden = !!user;
            $("#pwHint").hidden = !user;
            bindEyes(editForm);
            editForm.password.dispatchEvent(new Event("input"));
            $("#listView").hidden = true;
            $("#editView").hidden = false;
            $(".staff-side").style.visibility = "hidden";
            window.scrollTo(0, 0);
            editForm.username.focus();
        }

        function hideEditor() {
            $("#editView").hidden = true;
            $("#listView").hidden = false;
            $(".staff-side").style.visibility = "";
        }

        $("#btnExit").onclick = hideEditor;
        editForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const data = formData(editForm);
            const button = $("button[type=submit]", editForm);
            button.disabled = true;
            showError($("#staffError"), "");
            try {
                if (data.password !== data.confirmPassword) throw new Error("Mật khẩu nhập lại không khớp");
                const result = await api("accounts", {
                    form: {
                        action: "save", id: editing ? editing.userId : "",
                        username: data.username, password: data.password, confirmPassword: data.confirmPassword,
                        fullName: data.fullName, email: data.email, phone: data.phone,
                        active: editForm.active.checked, manager: editForm.manager.checked,
                    },
                });
                toast(result.message);
                state.open = result.id;
                hideEditor();
                await loadUsers();
                if (editing && editing.userId === state.me.userId) window.refreshHeaderUser?.();
            } catch (err) {
                showError($("#staffError"), err.message);
            } finally {
                button.disabled = false;
            }
        });

        // ---------- Xoá nhân viên
        function deleteUser(user) {
            openModal({
                title: "Xoá nhân viên",
                submitText: "Xoá",
                danger: true,
                body: `<p style="line-height:1.6">Xoá tài khoản <b>${esc(user.username)}</b> (${esc(user.fullName)})? Lịch sử đăng nhập của tài khoản cũng bị xoá.
                    Nhân viên đã có hóa đơn, phiếu nhập hay thao tác hệ thống thì không xoá được — khi đó hãy bỏ tích <b>Cho phép đăng nhập?</b>.</p>`,
                onSubmit: async () => {
                    const result = await api("accounts", { form: { action: "delete", id: user.userId } });
                    toast(result.message);
                    state.open = null;
                    await loadUsers();
                },
            });
        }

        // ---------- UC16
        async function loadLogins() {
            let result;
            try { result = await fetchPage("logins"); } catch (err) {
                box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
                return;
            }
            const head = "<th>Thời gian</th><th>Tên đăng nhập</th><th>Tên</th><th>Kết quả</th><th>IP</th><th>Trình duyệt</th><th>Đăng xuất</th><th>Thời lượng</th>";
            const rows = result.rows.map((h) => `<tr>
                <td>${dateTime(h.loggedInAt)}</td>
                <td>${esc(h.username)}</td>
                <td>${esc(h.fullName || "(không tồn tại)")}</td>
                <td>${h.isSuccess ? '<span class="badge badge-OK">Thành công</span>' : '<span class="badge badge-FAILED">Thất bại</span>'}</td>
                <td>${esc(h.ipAddress || "—")}</td>
                <td class="ua" title="${esc(h.userAgent)}">${esc(browserName(h.userAgent))}</td>
                <td>${h.isSuccess ? dateTime(h.loggedOutAt) : "—"}</td>
                <td>${duration(h.sessionSeconds)}</td></tr>`).join("");
            render(result, head, rows, "Chưa có lượt đăng nhập phù hợp.");
        }

        // ---------- UC17
        async function loadEmails() {
            let result;
            try { result = await fetchPage("emails"); } catch (err) {
                box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
                return;
            }
            const select = $("#templateFilter");
            const current = select.value;
            select.innerHTML = '<option value="">Tất cả</option>' + (result.templates || []).map((t) =>
                `<option value="${esc(t.templateCode)}" ${t.templateCode === current ? "selected" : ""}>${esc(TEMPLATE_TEXT[t.templateCode] || t.templateCode)}</option>`).join("");
            const head = "<th>Thời gian</th><th>Email nhận</th><th>Tên</th><th>Loại email</th><th>Trạng thái</th><th>Chi tiết</th>";
            const rows = result.rows.map((e) => `<tr>
                <td>${dateTime(e.sentAt)}</td>
                <td>${esc(e.toEmail)}</td>
                <td>${esc(e.fullName || "")}</td>
                <td>${esc(TEMPLATE_TEXT[e.templateCode] || e.templateCode)}</td>
                <td>${badge(e.status)}</td>
                <td class="ua">${esc(e.status === "FAILED" ? e.errorMessage : e.providerMessageId || "")}</td></tr>`).join("");
            render(result, head, rows, "Chưa có email nào được gửi.");
        }

        $("#btnAddUser").onclick = () => showEditor(null);
        document.addEventListener("keydown", (e) => { if (e.key === "Escape") $("#modalRoot").innerHTML = ""; });
        openTab("users");
    }

    // ------------------------------------------------------------------ chạy theo trang

    const page = document.body.dataset.page;
    if (page === "login") initLogin();
    else if (page === "profile") initProfile();
    else if (page === "accounts") initAccounts();
})();
