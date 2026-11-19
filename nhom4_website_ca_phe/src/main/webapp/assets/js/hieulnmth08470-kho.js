/* =====================================================================
 * Tồn kho, kiểm kê và thông báo — phần Lê Nguyễn Minh Hiếu (UC105 – UC129)
 * Dùng cho 3 trang (chọn theo <body data-page>):
 *   stock         : pages/hieulnmth08470/kho-hang.html  — UC105..UC116 tồn theo gói, lô, lịch sử, xuất hủy, ngưỡng
 *   counts        : pages/hieulnmth08470/kiem-ke.html   — UC117..UC124 phiếu kiểm kê / điều chỉnh tồn
 *   notifications : pages/hieulnmth08470/thong-bao.html — UC127..UC129 thông báo (UC113, UC125, UC126 chạy nền)
 * Gọi API: ../../api/kho, ../../api/kiem-ke, ../../api/thong-bao
 * Cách viết giống assets/js/minhpvth08530-tai-khoan.js (api(), openModal(), bảng mở dòng chi tiết).
 * ===================================================================== */
(function () {
    "use strict";

    const API = "../../api";
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    const STOCK_TEXT = { OUT_OF_STOCK: "Hết hàng", LOW_STOCK: "Tồn thấp", IN_STOCK: "Còn hàng" };
    const EXPIRY_TEXT = { EXPIRED: "Hết hạn", NEAR_EXPIRY: "Cận hạn", VALID: "Còn hạn" };
    const MOVE_TEXT = { IMPORT: "Nhập hàng", SALE: "Bán hàng", SALE_RETURN: "Hoàn bán", DISPOSAL: "Xuất hủy", ADJUSTMENT: "Điều chỉnh" };
    const COUNT_STATUS = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Từ chối" };
    const COUNT_TYPE = { COUNT: "Kiểm kê", ADJUSTMENT: "Điều chỉnh lô" };
    const NOTIFY_TYPE = { EXPIRED: "Lô hết hạn", NEAR_EXPIRY: "Lô cận hạn", LOW_STOCK: "Tồn thấp" };

    const ICON = {
        edit: '<svg viewBox="0 0 24 24"><path d="M4 17.2V20h2.8l8.3-8.3-2.8-2.8L4 17.2Zm13.7-7.5a1 1 0 0 0 0-1.4L15.7 6.3a1 1 0 0 0-1.4 0l-1.1 1.1 2.8 2.8 1.7-1.5ZM14 18h6v2h-6v-2Z"/></svg>',
        history: '<svg viewBox="0 0 24 24"><path d="M13 3a9 9 0 0 0-9 9H1l4 4 4-4H6a7 7 0 1 1 2.1 5l-1.4 1.4A9 9 0 1 0 13 3Zm-1 5v5l4.3 2.5.7-1.2-3.5-2.1V8H12Z"/></svg>',
        box: '<svg viewBox="0 0 24 24"><path d="M12 2 3 7v10l9 5 9-5V7l-9-5Zm0 2.3L18.6 8 12 11.7 5.4 8 12 4.3Z"/></svg>',
        check: '<svg viewBox="0 0 24 24"><path d="m9 16.2-4.2-4.2-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2Z"/></svg>',
        close: '<svg viewBox="0 0 24 24"><path d="M18.3 5.7 12 12l6.3 6.3-1.4 1.4L10.6 13.4 4.3 19.7 2.9 18.3 9.2 12 2.9 5.7 4.3 4.3l6.3 6.3 6.3-6.3 1.4 1.4Z"/></svg>',
        trash: '<svg viewBox="0 0 24 24"><path d="M9 3h6l1 2h4v2H4V5h4l1-2Zm-3 6h12l-1 12H7L6 9Zm4 2v8h1.5v-8H10Zm3.5 0v8H15v-8h-1.5Z"/></svg>',
        save: '<svg viewBox="0 0 24 24"><path d="M17 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7l-4-4Zm-5 16a3 3 0 1 1 0-6 3 3 0 0 1 0 6Zm3-10H5V5h10v4Z"/></svg>',
        send: '<svg viewBox="0 0 24 24"><path d="M2 21 23 12 2 3v7l15 2-15 2v7Z"/></svg>',
        count: '<svg viewBox="0 0 24 24"><path d="M9 2h6v2h4v18H5V4h4V2Zm0 4H7v14h10V6h-2v2H9V6Z"/></svg>',
    };

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

    /** "2026-11-19" -> "19/11/2026" (không đổi múi giờ). */
    function day(value) {
        if (!value) return "—";
        const [y, m, d] = String(value).slice(0, 10).split("-");
        return `${d}/${m}/${y}`;
    }

    function num(value) {
        if (value === null || value === undefined || value === "") return "—";
        return Number(value).toLocaleString("vi-VN");
    }

    function money(value) {
        if (value === null || value === undefined) return "—";
        return Number(value).toLocaleString("vi-VN") + " đ";
    }

    function signed(value) {
        const n = Number(value) || 0;
        if (n === 0) return '<span class="muted">0</span>';
        return n > 0 ? `<span class="qty-plus">+${num(n)}</span>` : `<span class="qty-minus">${num(n)}</span>`;
    }

    function badge(code, texts) {
        return code ? `<span class="badge badge-${esc(code)}">${esc(texts[code] || code)}</span>` : "";
    }

    function daysLeft(n) {
        if (n === null || n === undefined) return "—";
        if (n < 0) return `<span class="qty-minus">quá ${-n} ngày</span>`;
        if (n === 0) return '<span class="qty-minus">hôm nay</span>';
        return `${n} ngày`;
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
            options.body = form instanceof URLSearchParams ? form : new URLSearchParams(form);
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
        if (response.status === 401) {
            location.href = "../minhpvth08530/dang-nhap.html?next=" + encodeURIComponent(location.pathname + location.search);
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

    function openModal({ title, body, submitText = "Lưu", onSubmit, danger = false, wide = false, onOpen }) {
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
        if (onOpen) onOpen(form);
        const first = $("input:not([type=hidden]):not([readonly]), select, textarea", form);
        if (first) setTimeout(() => first.focus(), 30);
        return form;
    }

    function debounce(fn, wait) {
        let timer;
        return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), wait); };
    }

    /** Lọc ngay khi gõ / đổi ô. */
    function bindFilter(form, reload) {
        const later = debounce(reload, 300);
        form.addEventListener("input", (e) => (e.target.type === "search" ? later() : null));
        form.addEventListener("change", (e) => (e.target.type !== "search" ? reload() : null));
        form.addEventListener("submit", (e) => { e.preventDefault(); reload(); });
    }

    function table(head, rows, empty) {
        return rows
            ? `<div class="scroll"><table class="staff"><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table></div>`
            : `<div class="staff-empty">${esc(empty)}</div>`;
    }

    function detailBox(title, rowsHtml, buttons) {
        return `<div class="detail-box">
            <div class="detail-tabs"><span class="active">${esc(title)}</span></div>
            ${rowsHtml}
            ${buttons ? `<div class="detail-buttons">${buttons}</div>` : ""}
          </div>`;
    }

    /** Bảng 4 cột "nhãn | giá trị | nhãn | giá trị" như chi tiết nhân viên. */
    function pairs(items) {
        let html = '<table class="detail-table">';
        for (let i = 0; i < items.length; i += 2) {
            const a = items[i];
            const b = items[i + 1];
            html += `<tr><th>${esc(a[0])}</th><td>${a[1]}</td>${b ? `<th>${esc(b[0])}</th><td>${b[1]}</td>` : "<th></th><td></td>"}</tr>`;
        }
        return html + "</table>";
    }

    function button(act, cls, icon, text, extra = "") {
        return `<button type="button" class="btn-big ${cls}" data-act="${act}" ${extra}>${icon} ${esc(text)}</button>`;
    }

    // ================================================================== TRANG KHO HÀNG (UC105..UC116)

    function initStock() {
        const params = new URLSearchParams(location.search);
        const box = $("#tableBox");
        const filters = {
            packages: $("#packageFilter"),
            lots: $("#lotFilter"),
            movements: $("#movementFilter"),
            disposals: $("#disposalFilter"),
        };
        const state = { tab: "packages", open: null, canManage: false, labels: {} };

        // Mở từ link khác: ?tab=lots&keyword=LOT01 | ?keyword=SKU | ?tab=disposals
        const startTab = filters[params.get("tab")] ? params.get("tab") : "packages";
        if (params.get("keyword") && filters[startTab].keyword) filters[startTab].keyword.value = params.get("keyword");
        if (params.get("expiryStatus")) filters.lots.expiryStatus.value = params.get("expiryStatus");
        if (params.get("stockStatus")) filters.packages.stockStatus.value = params.get("stockStatus");

        function setTab(tab) {
            state.tab = tab;
            state.open = null;
            $$("#mainTabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
            Object.entries(filters).forEach(([name, form]) => { form.hidden = name !== tab; });
            $("#tiles").hidden = tab !== "packages";
            load();
        }
        $$("#mainTabs button").forEach((b) => { b.onclick = () => setTab(b.dataset.tab); });
        Object.values(filters).forEach((form) => bindFilter(form, () => { state.open = null; load(); }));

        // Chip "đang lọc theo gói / lô" cho tab Lô hàng và Lịch sử
        function renderChip() {
            const chip = $("#chipBox");
            const form = filters[state.tab];
            const lotId = form.lotId?.value;
            const packageId = form.packageId?.value;
            if (!lotId && !packageId) { chip.innerHTML = ""; return; }
            const label = lotId ? `lô ${state.labels["lot" + lotId] || "#" + lotId}` : `gói ${state.labels["pkg" + packageId] || "#" + packageId}`;
            chip.innerHTML = `<span class="filter-chip">Đang xem riêng ${esc(label)} <button type="button" title="Bỏ lọc">×</button></span>`;
            $("button", chip).onclick = () => {
                if (form.lotId) form.lotId.value = "";
                if (form.packageId) form.packageId.value = "";
                load();
            };
        }

        function load() {
            renderChip();
            $("#expiredBox").innerHTML = "";
            box.innerHTML = '<div class="staff-empty">Đang tải...</div>';
            const run = { packages: loadPackages, lots: loadLots, movements: loadMovements, disposals: loadDisposals }[state.tab];
            run().catch((err) => { box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`; });
        }

        // ---------- UC105, UC106, UC107: tồn theo gói
        async function loadPackages() {
            const data = await api("kho", { params: { view: "packages", ...formData(filters.packages) } });
            state.canManage = data.canManage;
            $("#btnSettings").hidden = !data.canManage;
            renderTiles(data.summary);
            const head = `<th></th><th>SKU</th><th>Sản phẩm</th><th>Biến thể</th><th class="num">Trọng lượng</th>
                <th>Dạng hạt</th><th class="num">Tồn</th><th class="num">Ngưỡng tồn thấp</th><th>Trạng thái</th>`;
            const rows = data.rows.map((p) => {
                state.labels["pkg" + p.packageId] = p.sku;
                const open = state.open === p.packageId;
                return `<tr class="row-main ${open ? "open" : ""}" data-id="${p.packageId}">
                    <td class="caret"><span>▶</span></td>
                    <td>${esc(p.sku)}</td><td>${esc(p.productName)}</td><td>${esc(p.variantName)}</td>
                    <td class="num">${num(p.weightG)} g</td><td>${esc(p.grindName)}</td>
                    <td class="num"><b>${num(p.quantityOnHand)}</b></td><td class="num">${num(p.minStockLevel)}</td>
                    <td>${badge(p.stockStatus, STOCK_TEXT)}</td>
                  </tr>
                  ${open ? `<tr class="row-detail"><td colspan="9">${detailBox("Chi tiết gói", pairs([
                        ["SKU", esc(p.sku)], ["Trạng thái tồn", badge(p.stockStatus, STOCK_TEXT)],
                        ["Sản phẩm", esc(p.productName)], ["Tồn hiện tại", `<b>${num(p.quantityOnHand)}</b> gói`],
                        ["Biến thể", `${esc(p.variantName)} · ${num(p.weightG)} g · ${esc(p.grindName)}`],
                        ["Ngưỡng tồn thấp", num(p.minStockLevel)],
                        ["Số lô còn hàng", num(p.lotCount)],
                    ]), button("lots", "light", ICON.box, "Xem các lô")
                        + button("history", "light", ICON.history, "Lịch sử biến động")
                        + `<a class="btn-big light" href="kiem-ke.html?create=${p.packageId}">${ICON.count} Kiểm kê gói này</a>`
                        + (data.canManage ? button("min", "primary", ICON.edit, "Đặt ngưỡng tồn thấp") : ""))}</td></tr>` : ""}`;
            }).join("");
            box.innerHTML = table(head, rows, "Không có gói nào phù hợp.");
            bindRows(() => loadPackages());
            const pkg = data.rows.find((p) => p.packageId === state.open);
            $$("[data-act]", box).forEach((btn) => {
                btn.onclick = () => {
                    if (btn.dataset.act === "lots") { filters.lots.packageId.value = pkg.packageId; setTab("lots"); }
                    if (btn.dataset.act === "history") {
                        filters.movements.lotId.value = ""; filters.movements.packageId.value = pkg.packageId; setTab("movements");
                    }
                    if (btn.dataset.act === "min") editMinStock(pkg);
                };
            });
        }

        function renderTiles(s) {
            const current = filters.packages.stockStatus.value;
            const tile = (status, cls, n, text) => `<button type="button" class="stock-tile ${cls} ${current === status ? "active" : ""}"
                data-status="${status}"><span class="n">${num(n)}</span><span class="t">${text}</span></button>`;
            $("#tiles").innerHTML = tile("", "", s.total, "Tổng số gói đang bán")
                + tile("OUT_OF_STOCK", "out", s.outOfStock, "Hết hàng")
                + tile("LOW_STOCK", "low", s.lowStock, "Tồn thấp")
                + tile("IN_STOCK", "in", s.inStock, "Còn hàng");
            $$(".stock-tile").forEach((t) => {
                t.onclick = () => { filters.packages.stockStatus.value = t.dataset.status; state.open = null; load(); };
            });
        }

        /** Bấm dòng để mở / đóng chi tiết. */
        function bindRows(rerender) {
            $$("tr.row-main", box).forEach((tr) => {
                tr.onclick = () => {
                    const id = Number(tr.dataset.id);
                    state.open = state.open === id ? null : id;
                    rerender().catch((err) => toast(err.message, "error"));
                };
            });
        }

        // ---------- UC115: đặt ngưỡng tồn thấp
        function editMinStock(pkg) {
            openModal({
                title: "Đặt ngưỡng tồn thấp — " + pkg.sku,
                body: `<div class="callout">Khi tồn của gói ≤ ngưỡng, hệ thống tạo thông báo "Tồn thấp" (tối đa 1 lần/ngày).
                         Tồn hiện tại: <b>${num(pkg.quantityOnHand)}</b> gói.</div>
                       <div class="acc-field"><label>Ngưỡng tồn thấp (gói)</label>
                         <input name="minStockLevel" type="number" min="0" step="1" value="${pkg.minStockLevel}"></div>`,
                onSubmit: async (data) => {
                    const result = await api("kho", { form: { action: "minStock", packageId: pkg.packageId, ...data } });
                    toast(result.message);
                    load();
                    window.refreshNotifyCount && window.refreshNotifyCount();
                },
            });
        }

        // ---------- UC116: ngưỡng cận hạn
        $("#btnSettings").onclick = async () => {
            let setting;
            try { setting = await api("kho", { params: { view: "settings" } }); } catch (err) { toast(err.message, "error"); return; }
            openModal({
                title: "Thiết lập ngưỡng cận hạn",
                body: `<div class="callout">Lô còn hạn nhưng sẽ hết hạn trong số ngày này được xem là <b>cận hạn</b>
                         và được báo 1 lần. Lần sửa gần nhất: ${dateTime(setting.updatedAt)}${setting.updatedByName ? " — " + esc(setting.updatedByName) : ""}.</div>
                       <div class="acc-field"><label>Số ngày cận hạn (1 – 365)</label>
                         <input name="days" type="number" min="1" max="365" step="1" value="${setting.nearExpiryDays}"></div>`,
                onSubmit: async (data) => {
                    const result = await api("kho", { form: { action: "nearExpiry", ...data } });
                    toast(result.message);
                    load();
                    window.refreshNotifyCount && window.refreshNotifyCount();
                },
            });
        };

        // ---------- UC108, UC109, UC110: lô hàng
        // Ảnh QR của lô: dùng ảnh Cloudinary nếu có, chưa có thì lấy ảnh tạo trực tiếp từ /api/lot-qr
        function qrBlock(d) {
            const src = d.qrUrl || `../../api/lot-qr?lotId=${d.lotId}`;
            return `<div class="lot-qr-box">
                <img src="${esc(src)}" alt="QR ${esc(d.lotCode)}">
                <div><b>Mã QR lô</b><br><span class="acc-hint">Nội dung: ${esc(d.lotCode)}<br>
                ${d.qrUrl ? "Đã lưu trên Cloudinary" : "Chưa lưu trên Cloudinary"}</span></div>
              </div>`;
        }

        async function loadLots() {
            const data = await api("kho", { params: { view: "lots", ...formData(filters.lots) } });
            state.canManage = data.canManage;
            $("#btnSettings").hidden = !data.canManage;
            const nearDays = data.setting ? data.setting.nearExpiryDays : "";
            const head = `<th></th><th>Mã lô</th><th>SKU</th><th>Ngày rang</th><th>Hạn sử dụng</th>
                <th class="num">Còn lại</th><th class="num">Tồn</th><th>Hạn (cận hạn: ${esc(nearDays)} ngày)</th>`;
            let detail = null;
            if (state.open) {
                try { detail = await api("kho", { params: { view: "lot", id: state.open } }); } catch (err) { toast(err.message, "error"); }
            }
            const rows = data.rows.map((l) => {
                state.labels["lot" + l.lotId] = l.lotCode;
                const open = state.open === l.lotId && detail;
                return `<tr class="row-main ${open ? "open" : ""}" data-id="${l.lotId}">
                    <td class="caret"><span>▶</span></td>
                    <td>${esc(l.lotCode)}</td><td>${esc(l.sku)}</td><td>${day(l.roastDate)}</td><td>${day(l.expiryDate)}</td>
                    <td class="num">${daysLeft(l.daysLeft)}</td><td class="num"><b>${num(l.quantityOnHand)}</b></td>
                    <td>${badge(l.expiryStatus, EXPIRY_TEXT)}</td>
                  </tr>
                  ${open ? `<tr class="row-detail"><td colspan="8">${detailBox("Chi tiết lô", pairs([
                        ["Mã lô", esc(detail.lotCode)], ["Tình trạng hạn", badge(l.expiryStatus, EXPIRY_TEXT)],
                        ["SKU", esc(detail.sku)], ["Sản phẩm", `${esc(detail.productName)} · ${esc(detail.variantName)} · ${num(detail.weightG)} g`],
                        ["Ngày rang", day(detail.roastDate)], ["Hạn sử dụng", `${day(detail.expiryDate)} (${daysLeft(detail.daysLeft)})`],
                        ["Số lượng nhập", num(detail.importedQty)], ["Tồn hiện tại", `<b>${num(detail.quantityOnHand)}</b>`],
                        ["Giá nhập / gói", money(detail.unitCost)], ["Giá trị tồn", money(detail.unitCost * detail.quantityOnHand)],
                        ["Hóa đơn nhập", `${esc(detail.invoiceNo)} · ${day(detail.invoiceDate)}`], ["Nhà cung cấp", esc(detail.supplierName || "")],
                    ]) + qrBlock(detail), button("history", "light", ICON.history, "Lịch sử biến động")
                        + button("adjust", "primary", ICON.edit, "Điều chỉnh tồn")
                        + button("qr-download", "light", ICON.box, "Tải ảnh QR")
                        + button("qr-print", "light", ICON.box, "In tem QR")
                        + (state.canManage && !detail.qrUrl ? button("qr-create", "light", ICON.check, "Tạo QR còn thiếu") : ""))}</td></tr>` : ""}`;
            }).join("");
            box.innerHTML = table(head, rows, "Không có lô nào còn tồn phù hợp.");
            bindRows(() => loadLots());
            $$("[data-act]", box).forEach((btn) => {
                btn.onclick = () => {
                    if (btn.dataset.act === "history") {
                        filters.movements.packageId.value = ""; filters.movements.lotId.value = detail.lotId; setTab("movements");
                    }
                    if (btn.dataset.act === "adjust") adjustLot(detail);
                    if (btn.dataset.act === "qr-download") {
                        location.href = `../../api/lot-qr?lotId=${detail.lotId}&download=1`;
                    }
                    if (btn.dataset.act === "qr-print") {
                        const w = window.open(`../../api/lot-qr?lotId=${detail.lotId}`, "_blank");
                        if (w) w.onload = () => w.print();
                    }
                    if (btn.dataset.act === "qr-create") {
                        api("lot-qr", { form: { action: "generateMissing" } })
                            .then((r) => { toast(r.cloud ? `Đã tạo QR cho ${r.created} lô` : "Chưa cấu hình CLOUDINARY_URL", r.cloud ? "ok" : "error"); loadLots(); })
                            .catch((err) => toast(err.message, "error"));
                    }
                };
            });
        }

        // ---------- UC124: điều chỉnh tồn theo lô (tạo phiếu chờ duyệt)
        function adjustLot(lot) {
            openModal({
                title: "Điều chỉnh tồn lô " + lot.lotCode,
                submitText: "Gửi yêu cầu điều chỉnh",
                body: `<div class="callout">Hệ thống tạo phiếu <b>Điều chỉnh lô</b> ở trạng thái <b>Chờ duyệt</b>.
                         Tồn chỉ thay đổi sau khi chủ cửa hàng xác nhận ở trang Kiểm kê.</div>
                       <div class="acc-field"><label>Tồn hệ thống hiện tại</label><input value="${num(lot.quantityOnHand)}" readonly></div>
                       <div class="acc-field"><label>Số lượng đúng thực tế <span class="req">*</span></label>
                         <input name="newQty" type="number" min="0" step="1" value="${lot.quantityOnHand}"></div>
                       <div class="acc-field"><label>Lý do <span class="req">*</span></label>
                         <textarea name="reason" maxlength="500" placeholder="VD: rách bao bì 2 gói, đếm thiếu..."></textarea></div>`,
                onSubmit: async (data) => {
                    const result = await api("kiem-ke", { form: { action: "adjust", lotId: lot.lotId, ...data } });
                    toast(result.message);
                },
            });
        }

        // ---------- UC111: lịch sử biến động
        async function loadMovements() {
            const rowsData = await api("kho", { params: { view: "movements", ...formData(filters.movements) } });
            const head = `<th>Thời gian</th><th>Lô</th><th>SKU</th><th>Loại</th><th class="num">Thay đổi</th>
                <th class="num">Tồn lô sau</th><th>Chứng từ</th><th>Người thực hiện</th>`;
            const rows = rowsData.map((m) => {
                const doc = m.invoiceNo ? `Hóa đơn ${esc(m.invoiceNo)}`
                    : m.disposalId ? `Phiếu xuất hủy #${m.disposalId}`
                    : m.stockCountId ? `<a href="kiem-ke.html?id=${m.stockCountId}">Phiếu kiểm #${m.stockCountId}</a>` : "";
                return `<tr><td>${dateTime(m.occurredAt)}</td><td>${esc(m.lotCode)}</td><td>${esc(m.sku)}</td>
                    <td>${badge(m.movementType, MOVE_TEXT)}</td><td class="num">${signed(m.quantityChange)}</td>
                    <td class="num">${num(m.balanceAfter)}</td><td>${doc}</td><td>${esc(m.performedByName || "")}</td></tr>`;
            }).join("");
            box.innerHTML = table(head, rows, "Chưa có biến động tồn nào phù hợp.")
                + (rowsData.length >= 500 ? '<div class="staff-empty">Chỉ hiện 500 dòng mới nhất — hãy lọc theo ngày, gói hoặc lô.</div>' : "");
        }

        // ---------- UC112, UC114: lô hết hạn chờ xuất + danh sách phiếu xuất hủy
        async function loadDisposals() {
            const [expired, list] = await Promise.all([
                api("kho", { params: { view: "expired" } }),
                api("kho", { params: { view: "disposals", ...formData(filters.disposals) } }),
            ]);
            if (!state.canManage) {
                try { state.canManage = (await api("kho", { params: { view: "settings" } })).canManage; } catch (e) { /* giữ nguyên */ }
            }
            renderExpired(expired);

            let lines = null;
            if (state.open) {
                try { lines = await api("kho", { params: { view: "disposal", id: state.open } }); } catch (err) { toast(err.message, "error"); }
            }
            const head = `<th></th><th>Mã phiếu</th><th>Ngày xuất</th><th>Lý do</th><th class="num">Số lô</th>
                <th class="num">Số gói</th><th class="num">Giá trị lỗ</th><th>Người lập</th><th>Ghi chú</th>`;
            const rows = list.map((d) => {
                const open = state.open === d.disposalId && lines;
                return `<tr class="row-main ${open ? "open" : ""}" data-id="${d.disposalId}">
                    <td class="caret"><span>▶</span></td><td>#${d.disposalId}</td><td>${dateTime(d.disposedAt)}</td>
                    <td>${esc(d.reasonName)}</td><td class="num">${num(d.lineCount)}</td><td class="num">${num(d.quantity)}</td>
                    <td class="num">${money(d.lossAmount)}</td><td>${esc(d.createdByName || "")}</td><td>${esc(d.note || "")}</td>
                  </tr>
                  ${open ? `<tr class="row-detail"><td colspan="9">${detailBox("Các lô trong phiếu", table(
                        "<th>Mã lô</th><th>SKU</th><th>Hạn sử dụng</th><th class='num'>Số lượng</th><th class='num'>Giá nhập</th><th class='num'>Giá trị lỗ</th>",
                        lines.map((l) => `<tr><td>${esc(l.lotCode)}</td><td>${esc(l.sku)}</td><td>${day(l.expiryDate)}</td>
                            <td class="num">${num(l.quantity)}</td><td class="num">${money(l.unitCost)}</td><td class="num">${money(l.lossAmount)}</td></tr>`).join(""),
                        "Phiếu không có dòng nào."))}</td></tr>` : ""}`;
            }).join("");
            box.innerHTML = `<div class="section-label" style="margin:12px 16px 0">Danh sách phiếu xuất hủy</div>`
                + table(head, rows, "Chưa có phiếu xuất hủy nào.");
            bindRows(() => loadDisposals());
        }

        function renderExpired(expired) {
            const holder = $("#expiredBox");
            if (!expired.length) {
                holder.innerHTML = '<div class="expired-box"><div class="expired-head"><h2>Không có lô hết hạn nào còn tồn</h2></div></div>';
                return;
            }
            const manage = state.canManage;
            const totalLoss = expired.reduce((sum, l) => sum + Number(l.lossAmount || 0), 0);
            holder.innerHTML = `<div class="expired-box">
                <div class="expired-head"><h2>Lô hết hạn chờ xuất hủy: ${expired.length} lô · ${money(totalLoss)}</h2>
                  ${manage ? `<button type="button" class="btn-big danger" id="btnDispose" disabled>${ICON.trash} Xuất hủy các lô đã chọn</button>`
                           : '<span class="acc-hint">Chỉ chủ cửa hàng được xuất hủy</span>'}</div>
                ${table(`${manage ? '<th class="pick"><input type="checkbox" id="pickAll" title="Chọn tất cả"></th>' : ""}
                    <th>Mã lô</th><th>SKU</th><th>Hạn sử dụng</th><th class="num">Quá hạn</th><th class="num">Tồn</th>
                    <th class="num">Giá nhập</th><th class="num">Giá trị lỗ</th>`,
                    expired.map((l) => `<tr>${manage ? `<td class="pick"><input type="checkbox" value="${l.lotId}"></td>` : ""}
                        <td>${esc(l.lotCode)}</td><td>${esc(l.sku)}</td><td>${day(l.expiryDate)}</td>
                        <td class="num qty-minus">${num(l.daysOver)} ngày</td><td class="num"><b>${num(l.quantityOnHand)}</b></td>
                        <td class="num">${money(l.unitCost)}</td><td class="num">${money(l.lossAmount)}</td></tr>`).join(""), "")}
              </div>`;
            if (!manage) return;
            const picks = $$("td.pick input", holder);
            const btn = $("#btnDispose");
            const refresh = () => {
                const n = picks.filter((p) => p.checked).length;
                btn.disabled = n === 0;
                $("#pickAll").checked = n === picks.length;
            };
            picks.forEach((p) => { p.onchange = refresh; });
            $("#pickAll").onchange = (e) => { picks.forEach((p) => { p.checked = e.target.checked; }); refresh(); };
            btn.onclick = () => {
                const chosen = expired.filter((l) => picks.find((p) => p.checked && Number(p.value) === l.lotId));
                const qty = chosen.reduce((s, l) => s + Number(l.quantityOnHand), 0);
                const loss = chosen.reduce((s, l) => s + Number(l.lossAmount || 0), 0);
                openModal({
                    title: "Xuất hủy lô hết hạn",
                    submitText: "Xuất hủy",
                    danger: true,
                    body: `<div class="callout">Tạo 1 phiếu xuất hủy (lý do: Hết hạn sử dụng) cho <b>${chosen.length}</b> lô,
                             trừ hết <b>${num(qty)}</b> gói khỏi tồn kho. Giá trị lỗ: <b>${money(loss)}</b>.<br>
                             Sổ kho chỉ được ghi thêm, không xóa được, nên hãy kiểm tra kỹ.</div>
                           <div class="acc-field"><label>Ghi chú</label><textarea name="note" maxlength="500"></textarea></div>`,
                    onSubmit: async (data) => {
                        const result = await api("kho", { form: { action: "dispose", lotIds: chosen.map((l) => l.lotId).join(","), ...data } });
                        toast(result.message);
                        state.open = null;
                        load();
                        window.refreshNotifyCount && window.refreshNotifyCount();
                    },
                });
            };
        }

        // mở tab ban đầu
        setTab(startTab);
    }

    // ================================================================== TRANG KIỂM KÊ (UC117..UC124)

    function initCounts() {
        const params = new URLSearchParams(location.search);
        const box = $("#tableBox");
        const filter = $("#countFilter");
        let me = { canManage: false };
        let current = null;   // phiếu đang mở

        bindFilter(filter, loadList);

        // ---------- UC120: danh sách phiếu
        async function loadList() {
            box.innerHTML = '<div class="staff-empty">Đang tải...</div>';
            let data;
            try { data = await api("kiem-ke", { params: { view: "list", ...formData(filter) } }); } catch (err) {
                box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
                return;
            }
            me = { canManage: data.canManage, userId: data.userId };
            const head = `<th>Mã phiếu</th><th>Loại</th><th>Trạng thái</th><th>Ngày tạo</th><th>Người tạo</th>
                <th class="num">Số lô</th><th class="num">Dòng lệch</th><th class="num">Tổng lệch</th><th>Người duyệt</th><th>Ghi chú</th>`;
            const rows = data.rows.map((c) => `<tr class="row-main" data-id="${c.stockCountId}">
                <td><b>#${c.stockCountId}</b></td><td>${badge(c.countType, COUNT_TYPE)}</td><td>${badge(c.status, COUNT_STATUS)}</td>
                <td>${dateTime(c.createdAt)}</td><td>${esc(c.createdByName)}</td><td class="num">${num(c.lineCount)}</td>
                <td class="num">${num(c.diffLineCount)}</td><td class="num">${signed(c.diffQty)}</td>
                <td>${c.reviewedByName ? esc(c.reviewedByName) + `<span class="sub-line">${dateTime(c.reviewedAt)}</span>` : "—"}</td>
                <td>${esc(c.note || "")}</td></tr>`).join("");
            box.innerHTML = table(head, rows, "Chưa có phiếu kiểm kê nào.");
            $$("tr.row-main", box).forEach((tr) => { tr.onclick = () => openCount(Number(tr.dataset.id)); });
        }

        // ---------- UC117: tạo phiếu kiểm kê
        async function createCount(packageId) {
            let packages = [];
            try { packages = (await api("kho", { params: { view: "packages" } })).rows; } catch (err) { toast(err.message, "error"); return; }
            openModal({
                title: "Tạo phiếu kiểm kê",
                submitText: "Tạo phiếu",
                body: `<div class="callout">Hệ thống chụp lại tồn hệ thống của từng lô còn hàng. Số lượng thực tế mặc định
                         bằng tồn hệ thống — bạn chỉ cần sửa các lô bị lệch rồi gửi báo cáo.</div>
                       <div class="acc-field"><label>Kiểm gói</label>
                         <select name="packageId"><option value="">Tất cả gói còn hàng</option>
                           ${packages.filter((p) => Number(p.lotCount) > 0).map((p) => `<option value="${p.packageId}" ${String(p.packageId) === String(packageId) ? "selected" : ""}>
                             ${esc(p.sku)} — ${esc(p.productName)} · ${esc(p.variantName)} (${num(p.lotCount)} lô)</option>`).join("")}</select>
                         <span class="acc-hint" id="previewHint"></span></div>
                       <div class="acc-field"><label>Ghi chú</label><textarea name="note" maxlength="500" placeholder="VD: kiểm kê cuối tháng 11"></textarea></div>`,
                onOpen: (form) => {
                    const preview = async () => {
                        try {
                            const lots = await api("kiem-ke", { params: { view: "preview", packageId: form.packageId.value } });
                            const qty = lots.reduce((s, l) => s + Number(l.quantityOnHand), 0);
                            $("#previewHint").textContent = `Sẽ kiểm ${lots.length} lô, tổng tồn hệ thống ${num(qty)} gói.`;
                        } catch (err) { $("#previewHint").textContent = err.message; }
                    };
                    form.packageId.onchange = preview;
                    preview();
                },
                onSubmit: async (data) => {
                    const result = await api("kiem-ke", { form: { action: "create", ...data } });
                    toast(result.message);
                    openCount(result.stockCountId);
                },
            });
        }
        $("#btnCreate").onclick = () => createCount("");

        // ---------- UC121 chi tiết + UC118 nhập SL thực tế
        async function openCount(id) {
            let data;
            try { data = await api("kiem-ke", { params: { view: "detail", id } }); } catch (err) { toast(err.message, "error"); return; }
            current = data;
            history.replaceState(null, "", "?id=" + id);
            $("#listView").hidden = true;
            $("#editView").hidden = false;
            $(".staff-side").style.visibility = "hidden";
            renderCount();
            window.scrollTo(0, 0);
        }

        function closeCount() {
            current = null;
            history.replaceState(null, "", location.pathname);
            $("#editView").hidden = true;
            $("#listView").hidden = false;
            $(".staff-side").style.visibility = "";
            loadList();
        }
        $("#btnBack").onclick = closeCount;

        function renderCount() {
            const c = current;
            const draft = c.status === "DRAFT";
            const pending = c.status === "PENDING";
            $("#countTitle").textContent = `${COUNT_TYPE[c.countType] || "Phiếu"} #${c.stockCountId}`;
            const items = [
                ["Mã phiếu", "#" + c.stockCountId], ["Trạng thái", badge(c.status, COUNT_STATUS)],
                ["Loại phiếu", badge(c.countType, COUNT_TYPE)], ["Người tạo", `${esc(c.createdByName)} · ${dateTime(c.createdAt)}`],
                ["Người duyệt", c.reviewedByName ? `${esc(c.reviewedByName)} · ${dateTime(c.reviewedAt)}` : "—"],
                ["Ghi chú", esc(c.note || "")],
            ];
            if (c.rejectReason) items.push(["Lý do từ chối", `<span class="qty-minus">${esc(c.rejectReason)}</span>`]);
            $("#countHead").innerHTML = pairs(items);

            const head = `<th>Mã lô</th><th>SKU</th><th>Hạn sử dụng</th><th class="num">SL hệ thống</th>
                <th class="num">SL thực tế</th><th class="num">Chênh lệch</th><th class="num">Giá trị lệch</th><th>Ghi chú</th>
                ${pending ? '<th class="num">Tồn hiện tại</th>' : ""}`;
            const rows = c.lines.map((l) => `<tr data-line="${l.countLineId}" class="${l.diffQty ? "diff" : ""}">
                <td>${esc(l.lotCode)}</td><td>${esc(l.sku)}</td><td>${day(l.expiryDate)}</td>
                <td class="num">${num(l.systemQty)}</td>
                <td class="num">${draft ? `<input class="qty-input" type="number" min="0" step="1" value="${l.countedQty}">` : num(l.countedQty)}</td>
                <td class="num" data-diff>${signed(l.diffQty)}</td>
                <td class="num" data-value>${money(l.diffValue)}</td>
                <td>${draft ? `<input class="note-input" maxlength="500" value="${esc(l.note || "")}" placeholder="Lý do lệch">` : esc(l.note || "")}</td>
                ${pending ? `<td class="num">${Number(l.currentQty) !== Number(l.systemQty)
                    ? `<span class="qty-minus" title="Tồn đã thay đổi từ lúc kiểm">${num(l.currentQty)}</span>` : num(l.currentQty)}</td>` : ""}
              </tr>`).join("");
            $("#lineBox").innerHTML = table(head, rows, "Phiếu không có dòng nào.");

            const bar = $("#countBar");
            const buttons = draft
                ? button("save", "light", ICON.save, "Lưu nháp") + button("submit", "primary", ICON.send, "Gửi báo cáo chênh lệch")
                : pending && c.canManage
                    ? button("reject", "warn", ICON.close, "Từ chối") + button("approve", "success", ICON.check, "Xác nhận điều chỉnh")
                    : pending ? '<span class="acc-hint">Đang chờ chủ cửa hàng duyệt</span>' : "";
            bar.innerHTML = `<div class="sum" id="diffSum"></div><div class="title-actions">${buttons}</div>`;
            updateSum();

            if (draft) {
                $$("#lineBox tr[data-line]").forEach((tr) => {
                    const input = $(".qty-input", tr);
                    input.addEventListener("input", () => {
                        const line = c.lines.find((l) => String(l.countLineId) === tr.dataset.line);
                        const counted = input.value === "" ? line.systemQty : Number(input.value);
                        const diff = counted - line.systemQty;
                        line.countedQty = counted;
                        line.diffQty = diff;
                        line.diffValue = diff * Number(line.unitCost || 0);
                        $("[data-diff]", tr).innerHTML = signed(diff);
                        $("[data-value]", tr).innerHTML = money(line.diffValue);
                        tr.classList.toggle("diff", diff !== 0);
                        updateSum();
                    });
                });
            }
            $$("[data-act]", bar).forEach((btn) => {
                btn.onclick = () => ({ save: saveDraft, submit: submitCount, approve: approveCount, reject: rejectCount })[btn.dataset.act](btn);
            });
        }

        function updateSum() {
            const lines = current.lines;
            const diffLines = lines.filter((l) => Number(l.diffQty) !== 0);
            const qty = diffLines.reduce((s, l) => s + Number(l.diffQty), 0);
            const value = diffLines.reduce((s, l) => s + Number(l.diffValue || 0), 0);
            $("#diffSum").innerHTML = `<b>${lines.length}</b> lô · <b>${diffLines.length}</b> dòng lệch · tổng lệch ${signed(qty)} gói · giá trị ${money(value)}`;
        }

        /** UC118: gửi toàn bộ SL thực tế + ghi chú của phiếu nháp. */
        async function saveLines() {
            const body = new URLSearchParams({ action: "save", id: current.stockCountId });
            $$("#lineBox tr[data-line]").forEach((tr) => {
                body.append("lineId", tr.dataset.line);
                body.append("countedQty", $(".qty-input", tr).value.trim());
                body.append("lineNote", $(".note-input", tr).value.trim());
            });
            return api("kiem-ke", { form: body });
        }

        async function saveDraft(btn) {
            btn.disabled = true;
            try {
                const result = await saveLines();
                toast(result.message);
                openCount(current.stockCountId);
            } catch (err) { toast(err.message, "error"); btn.disabled = false; }
        }

        // ---------- UC119: gửi báo cáo chênh lệch
        function submitCount() {
            const diffLines = current.lines.filter((l) => Number(l.diffQty) !== 0).length;
            openModal({
                title: "Gửi báo cáo chênh lệch",
                submitText: "Gửi báo cáo",
                body: `<div class="callout">Phiếu #${current.stockCountId} có <b>${diffLines}</b> dòng lệch.
                         Sau khi gửi, phiếu chuyển sang <b>Chờ duyệt</b> và không sửa được nữa.</div>`,
                onSubmit: async () => {
                    await saveLines();
                    const result = await api("kiem-ke", { form: { action: "submit", id: current.stockCountId } });
                    toast(result.message);
                    openCount(current.stockCountId);
                },
            });
        }

        // ---------- UC122: xác nhận điều chỉnh
        function approveCount() {
            const diffLines = current.lines.filter((l) => Number(l.diffQty) !== 0);
            const changed = current.lines.filter((l) => Number(l.currentQty) !== Number(l.systemQty)).length;
            openModal({
                title: "Xác nhận điều chỉnh tồn",
                submitText: "Xác nhận",
                body: `<div class="callout">Ghi sổ <b>Điều chỉnh</b> cho <b>${diffLines.length}</b> lô theo chênh lệch của phiếu.
                         Sổ kho chỉ được ghi thêm, không xóa được.
                         ${changed ? `<br><span class="qty-minus">Có ${changed} lô đã thay đổi tồn từ lúc kiểm — chênh lệch vẫn được cộng/trừ vào tồn hiện tại.</span>` : ""}</div>`,
                onSubmit: async () => {
                    const result = await api("kiem-ke", { form: { action: "approve", id: current.stockCountId } });
                    toast(result.message);
                    openCount(current.stockCountId);
                    window.refreshNotifyCount && window.refreshNotifyCount();
                },
            });
        }

        // ---------- UC123: từ chối
        function rejectCount() {
            openModal({
                title: "Từ chối phiếu #" + current.stockCountId,
                submitText: "Từ chối",
                danger: true,
                body: `<div class="acc-field"><label>Lý do từ chối <span class="req">*</span></label>
                         <textarea name="reason" maxlength="500" placeholder="VD: đếm lại khu kệ B"></textarea></div>`,
                onSubmit: async (data) => {
                    const result = await api("kiem-ke", { form: { action: "reject", id: current.stockCountId, ...data } });
                    toast(result.message);
                    openCount(current.stockCountId);
                },
            });
        }

        loadList();
        if (params.get("id")) openCount(Number(params.get("id")));
        else if (params.has("create")) createCount(params.get("create"));
    }

    // ================================================================== TRANG THÔNG BÁO (UC127..UC129)

    function initNotifications() {
        const box = $("#tableBox");
        const filter = $("#notifyFilter");
        const state = { page: 1, size: 20, open: null };

        bindFilter(filter, () => { state.page = 1; state.open = null; load(); });

        async function load() {
            let data;
            try {
                data = await api("thong-bao", { params: { view: "list", ...formData(filter), page: state.page, size: state.size } });
            } catch (err) {
                box.innerHTML = `<div class="staff-empty">${esc(err.message)}</div>`;
                $("#btnReadAll").hidden = true;
                $("#btnScan").hidden = true;
                return;
            }
            $("#unreadText").textContent = data.unread ? `(${data.unread} chưa đọc)` : "";
            let detail = null;
            if (state.open) {
                try { detail = await api("thong-bao", { params: { view: "detail", id: state.open } }); } catch (err) { toast(err.message, "error"); }
            }
            const head = "<th></th><th>Loại</th><th>Nội dung</th><th>Thời gian</th>";
            const rows = data.rows.map((n) => {
                const open = state.open === n.notificationId && detail;
                return `<tr class="row-main ${n.isRead ? "" : "unread"} ${open ? "open" : ""}" data-id="${n.notificationId}">
                    <td class="caret"><span class="dot ${n.isRead ? "read" : ""}" title="${n.isRead ? "Đã đọc" : "Chưa đọc"}"></span></td>
                    <td>${badge(n.notificationType, NOTIFY_TYPE)}</td><td>${esc(n.message)}</td><td>${dateTime(n.createdAt)}</td>
                  </tr>
                  ${open ? `<tr class="row-detail"><td colspan="4">${notifyDetail(detail)}</td></tr>` : ""}`;
            }).join("");
            box.innerHTML = table(head, rows, "Không có thông báo nào.");
            box.appendChild(pager(data));
            $$("tr.row-main", box).forEach((tr) => {
                tr.onclick = async () => {
                    const id = Number(tr.dataset.id);
                    state.open = state.open === id ? null : id;
                    if (state.open && tr.classList.contains("unread")) {
                        // UC129: mở chi tiết là đánh dấu đã đọc
                        try { await api("thong-bao", { form: { action: "read", id } }); } catch (err) { toast(err.message, "error"); }
                        window.refreshNotifyCount && window.refreshNotifyCount();
                    }
                    load();
                };
            });
        }

        /** UC128: thông tin lô / gói tại thời điểm xem. */
        function notifyDetail(d) {
            const items = [["Loại", badge(d.notificationType, NOTIFY_TYPE)], ["Thời gian", dateTime(d.createdAt)]];
            let link = "";
            if (d.lotId) {
                items.push(["Mã lô", esc(d.lotCode)], ["Hạn sử dụng", `${day(d.expiryDate)} (${daysLeft(d.daysLeft)})`],
                    ["Tồn lô hiện tại", `<b>${num(d.lotQty)}</b>`], ["Gói", esc(d.sku || "")]);
                link = d.notificationType === "EXPIRED" && Number(d.lotQty) > 0
                    ? `<a class="btn-big danger" href="kho-hang.html?tab=disposals">${ICON.trash} Xuất hủy lô hết hạn</a>`
                    : `<a class="btn-big primary" href="kho-hang.html?tab=lots&keyword=${encodeURIComponent(d.lotCode || "")}">${ICON.box} Xem lô trong kho</a>`;
            } else {
                items.push(["Gói", esc(d.sku || "")], ["Ngưỡng tồn thấp", num(d.minStockLevel)],
                    ["Tồn gói hiện tại", `<b>${num(d.packageQty)}</b>`]);
                link = `<a class="btn-big primary" href="kho-hang.html?keyword=${encodeURIComponent(d.sku || "")}">${ICON.box} Xem gói trong kho</a>`;
            }
            items.push(["Đã đọc lúc", dateTime(d.readAt)]);
            return detailBox("Chi tiết thông báo", pairs(items), link);
        }

        function pager(result) {
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
            $$("button", wrap).forEach((b) => { b.onclick = () => { state.page = Number(b.dataset.go); state.open = null; load(); }; });
            $("select", wrap).onchange = (e) => { state.size = Number(e.target.value); state.page = 1; load(); };
            return wrap;
        }

        $("#btnReadAll").onclick = async () => {
            try {
                const result = await api("thong-bao", { form: { action: "readAll" } });
                toast(result.message);
                load();
                window.refreshNotifyCount && window.refreshNotifyCount();
            } catch (err) { toast(err.message, "error"); }
        };

        // Chạy ngay UC113 + UC125 + UC126 (bình thường job nền tự chạy mỗi giờ)
        $("#btnScan").onclick = async (e) => {
            const btn = e.currentTarget;
            btn.disabled = true;
            try {
                const result = await api("thong-bao", { form: { action: "scan" } });
                toast(result.message);
                state.page = 1;
                load();
                window.refreshNotifyCount && window.refreshNotifyCount();
            } catch (err) { toast(err.message, "error"); }
            btn.disabled = false;
        };

        load();
    }

    // ------------------------------------------------------------------ khởi động

    document.addEventListener("keydown", (e) => { if (e.key === "Escape") $("#modalRoot").innerHTML = ""; });
    const page = document.body.dataset.page;
    if (page === "stock") initStock();
    if (page === "counts") initCounts();
    if (page === "notifications") initNotifications();
})();
