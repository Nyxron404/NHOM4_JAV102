/* =====================================================================
   TRANG CẤU HÌNH BÁN HÀNG (longdtta001784) - chỉ chủ cửa hàng
   159 lưu / 164 xem thông tin hóa đơn · 163 bật tắt phương thức · 162 thanh toán ngoại lệ
   API: ../../api/cau-hinh-ban-hang (SalesConfigController)
   ===================================================================== */

const API_CONFIG = "../../api/cau-hinh-ban-hang";
let config = null;       // {settings:[{key,label,value,updatedByName,updatedAt}], methods:[...], exceptions:[...]}

const EXCEPTION_TEXT = {
    PAID_NOT_CONFIRMED: "Đã nhận tiền, đơn chưa xác nhận (trả thiếu hoặc hết tồn lúc tự xác nhận)",
    EXPIRED_QR: "QR quá 15 phút vẫn chờ",
    PAID_BUT_CLOSED: "VNPay báo đã trả, nhưng giao dịch đã bị hủy (khách quét QR cũ)"
};

/* ---------- Tiện ích ---------- */
function money(value) {
    return Number(value).toLocaleString("vi-VN") + "đ";
}

function esc(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

function dateTimeText(iso) {
    const d = new Date(iso);
    return d.toLocaleDateString("vi-VN") + " " +
        d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

function showMessage(id, text, ok) {
    const box = document.getElementById(id);
    box.textContent = text || "";
    box.style.display = text ? "block" : "none";
    box.classList.toggle("msg-ok", !!ok);
}

/* ---------- Gọi API ---------- */
async function loadConfig() {
    const response = await fetch(API_CONFIG);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || ("Lỗi " + response.status));
    return data;
}

async function postConfig(data) {
    const response = await fetch(API_CONFIG, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams(data)
    });
    const json = await response.json();
    if (!response.ok) throw new Error(json.error || ("Lỗi " + response.status));
    return json;
}

async function reload() {
    try {
        config = await loadConfig();
    } catch (error) {
        showMessage("pageMsg", error.message);
        return;
    }
    showMessage("pageMsg", "");
    document.getElementById("configBox").style.display = "";
    document.getElementById("exceptionBox").style.display = "";
    renderSettings();
    renderMethods();
    renderExceptions();
}

/* =====================================================================
   [164] XEM + [159] LƯU THÔNG TIN HÓA ĐƠN
   ===================================================================== */
function renderSettings() {
    document.getElementById("settingFields").innerHTML = config.settings.map(s => `
        <div class="field">
          <label for="set_${s.key}">${esc(s.label)}${s.key === "store_name" ? " *" : ""}</label>
          <input type="${s.key === "vat_rate" ? "number" : "text"}" id="set_${s.key}" data-key="${s.key}"
                 value="${esc(s.value)}" maxlength="200" ${s.key === "vat_rate" ? 'min="0" max="100" step="0.1"' : ""} />
        </div>`).join("");

    // Lần sửa gần nhất
    const last = config.settings.filter(s => s.updatedAt)
        .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))[0];
    document.getElementById("settingUpdated").textContent = last
        ? "Sửa lần cuối: " + dateTimeText(last.updatedAt) + (last.updatedByName ? " · " + last.updatedByName : "")
        : "";
    renderPreview();
}

function settingValue(key) {
    const input = document.getElementById("set_" + key);
    return input ? input.value.trim() : "";
}

// Xem trước đầu hóa đơn theo nội dung đang gõ
function renderPreview() {
    document.getElementById("pvStore").textContent = settingValue("store_name").toUpperCase();
    document.getElementById("pvAddress").textContent = settingValue("address");
    document.getElementById("pvPhone").textContent = settingValue("phone") ? "ĐT: " + settingValue("phone") : "";
    document.getElementById("pvTax").textContent = settingValue("tax_code") ? "MST: " + settingValue("tax_code") : "";
    document.getElementById("pvFooter").textContent = settingValue("footer_note") || "Cảm ơn quý khách!";

    const rate = Number(settingValue("vat_rate") || 0);
    const total = 108000;
    const beforeTax = Math.round(total / (1 + rate / 100));
    document.getElementById("pvVat").innerHTML = rate > 0
        ? `<div class="kv"><span>Tiền hàng chưa thuế</span><span>${money(beforeTax)}</span></div>
           <div class="kv"><span>Thuế GTGT ${rate}%</span><span>${money(total - beforeTax)}</span></div>`
        : "";
}

async function saveSettings() {
    const data = { action: "saveSettings" };
    config.settings.forEach(s => { data[s.key] = settingValue(s.key); });
    if (!data.store_name) {
        showMessage("settingMsg", "Nhập tên cửa hàng");
        return;
    }
    const button = document.getElementById("btnSaveSettings");
    button.disabled = true;
    try {
        config = await postConfig(data);
        renderSettings();
        showMessage("settingMsg", config.message, true);
    } catch (error) {
        showMessage("settingMsg", error.message);
    } finally {
        button.disabled = false;
    }
}

/* =====================================================================
   [163] BẬT / TẮT PHƯƠNG THỨC THANH TOÁN
   ===================================================================== */
function renderMethods() {
    document.getElementById("methodList").innerHTML = config.methods.map(m => `
        <div class="method-row">
          <div>
            <b>${m.code === "CASH" ? "💵" : "📱"} ${esc(m.name)}</b>
            <div class="sub">${m.online ? "Thanh toán online" : "Tại quầy"} · ${esc(m.code)}</div>
          </div>
          <label class="switch" title="${m.code === "CASH" ? "Tiền mặt luôn bật" : "Bật / tắt"}">
            <input type="checkbox" data-method-id="${m.id}" ${m.active ? "checked" : ""}
                   ${m.code === "CASH" ? "disabled" : ""} />
            <span>${m.active ? "Đang bật" : "Đang tắt"}</span>
          </label>
        </div>`).join("");
}

async function toggleMethod(input) {
    const method = config.methods.find(m => m.id === Number(input.dataset.methodId));
    const active = input.checked;
    if (!active && !confirm("Tắt " + method.name + "? Màn bán hàng sẽ không tạo được giao dịch " + method.name + " mới.")) {
        input.checked = true;
        return;
    }
    input.disabled = true;
    try {
        config = await postConfig({ action: "toggleMethod", id: method.id, active: active });
        renderMethods();
    } catch (error) {
        alert(error.message);
        input.checked = !active;
        input.disabled = false;
    }
}

/* =====================================================================
   [162] THANH TOÁN NGOẠI LỆ
   ===================================================================== */
function renderExceptions() {
    const list = config.exceptions;
    document.getElementById("exceptionCount").textContent = list.length;
    document.getElementById("exceptionRows").innerHTML = list.length === 0
        ? '<tr><td colspan="5" class="muted">Không có giao dịch cần xử lý.</td></tr>'
        : list.map(exceptionRow).join("");
}

function exceptionRow(e) {
    const openLink = `<a class="btn btn-sm" href="longdtta00178.html?open=${e.salesInvoiceId}">Mở đơn</a>`;
    let action;
    if (e.kind === "PAID_NOT_CONFIRMED") {
        action = openLink + '<div class="sub">Thu thêm hoặc giảm số lượng rồi xác nhận</div>';
    } else if (e.kind === "EXPIRED_QR") {
        action = `<button class="btn btn-sm" data-close="${e.paymentId}">Đóng giao dịch</button>`;
    } else if (e.invoiceStatus === "PENDING") {
        action = `<button class="btn btn-sm btn-primary" data-accept="${e.paymentId}">Ghi nhận đã thu</button>`;
    } else {
        action = '<span class="sub">Đơn đã ' + (e.invoiceStatus === "PAID" ? "xác nhận" : "hủy")
            + ": hoàn " + money(e.amount) + " cho khách ngoài hệ thống</span>";
    }
    return `
        <tr>
          <td><b>${esc(e.invoiceNo)}</b><div class="sub">${e.invoiceStatus === "PENDING" ? "Chờ thanh toán"
              : e.invoiceStatus === "PAID" ? "Đã xác nhận" : "Đã hủy"}</div></td>
          <td>${esc(e.methodName)}<div class="sub">${esc(e.txnRef || "#" + e.paymentId)} · ${dateTimeText(e.createdAt)}</div></td>
          <td class="num">${money(e.amount)}</td>
          <td>${EXCEPTION_TEXT[e.kind]}</td>
          <td>${action}</td>
        </tr>`;
}

async function handleException(button) {
    const close = button.dataset.close;
    const accept = button.dataset.accept;
    const question = close
        ? "Đóng giao dịch này? QR cũ sẽ không còn dùng được."
        : "Ghi nhận khoản VNPay này là đã thu? Sau đó mở đơn ở màn bán hàng để xác nhận.";
    if (!confirm(question)) return;
    button.disabled = true;
    try {
        config = await postConfig(close
            ? { action: "closePayment", paymentId: close }
            : { action: "acceptPayment", paymentId: accept });
        renderExceptions();
    } catch (error) {
        alert(error.message);
        button.disabled = false;
    }
}

/* ---------- Sự kiện ---------- */
function bindEvents() {
    document.getElementById("settingFields").addEventListener("input", renderPreview);
    document.getElementById("btnSaveSettings").addEventListener("click", saveSettings);
    document.getElementById("btnResetSettings").addEventListener("click", () => {
        renderSettings();
        showMessage("settingMsg", "");
    });
    document.getElementById("methodList").addEventListener("change", e => {
        if (e.target.dataset.methodId) toggleMethod(e.target);
    });
    document.getElementById("exceptionRows").addEventListener("click", e => {
        const button = e.target.closest("button[data-close], button[data-accept]");
        if (button) handleException(button);
    });
    document.getElementById("btnReload").addEventListener("click", reload);
}

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    reload();
});