/* =====================================================================
   TRANG HÓA ĐƠN (longdtta001782)
   147 in lại · 148–152 danh sách + lọc · 153 chi tiết · 154 hủy đơn đã xác nhận
   156/158 hoàn kho đúng lô · 157 trừ tiền (khoản hoàn tiền mặt)
   API: ../../api/hoa-don (SalesInvoiceController)
        ../../api/ban-hang/don?action=overview (thông tin cửa hàng in trên hóa đơn)
   ===================================================================== */

const API_INVOICE = "../../api/hoa-don";
const API_OVERVIEW = "../../api/ban-hang/don?action=overview";

const STATUS_TEXT = {
    PENDING: ["Chờ thanh toán", "status-pending"],
    PAID: ["Đã xác nhận", "status-paid"],
    CANCELLED: ["Đã hủy", "status-cancel"]
};

let page = 1;
let totalPages = 1;
let selectedId = null;
let detail = null;        // {order:{invoice,lines,payments,allocations}, extra:{...}, canCancel}
let storeInfo = {};       // system_settings invoice.* (storeName, address, phone, footerNote, vatRate)

/* ---------- Tiện ích ---------- */
function money(value) {
    return Number(value).toLocaleString("vi-VN") + "đ";
}

function number(value) {
    return Number(value).toLocaleString("vi-VN");
}

function weightText(gram) {
    return gram >= 1000 ? (gram / 1000) + "kg" : gram + "g";
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

function dateText(iso) {               // "2026-12-08" -> 08/12/2026
    const [y, m, d] = iso.split("-");
    return d + "/" + m + "/" + y;
}

function todayIso() {
    const d = new Date();
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
}

/* ---------- Gọi API ---------- */
async function getJson(url) {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || ("Lỗi " + response.status));
    return data;
}

async function postInvoice(data) {
    const response = await fetch(API_INVOICE, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams(data)
    });
    const json = await response.json();
    if (!response.ok) throw new Error(json.error || ("Lỗi " + response.status));
    return json;
}

/* =====================================================================
   [148–152] DANH SÁCH + LỌC
   ===================================================================== */
async function loadMethods() {
    try {
        const methods = await getJson(API_INVOICE + "?action=methods");
        document.getElementById("fMethod").innerHTML = '<option value="">Tất cả</option>' +
            methods.map(m => `<option value="${m.id}">${esc(m.name)}</option>`).join("");
    } catch (error) {
        console.error("Không tải được phương thức thanh toán:", error);
    }
}

async function loadList() {
    const from = document.getElementById("fFrom").value;
    const to = document.getElementById("fTo").value;
    if (from && to && from > to) {
        document.getElementById("listInfo").textContent = "Từ ngày phải trước Đến ngày";
        return;
    }
    const params = new URLSearchParams({
        action: "list",
        keyword: document.getElementById("fKeyword").value.trim(),
        from: from,
        to: to,
        methodId: document.getElementById("fMethod").value,
        status: document.getElementById("fStatus").value,
        page: page
    });

    let data;
    try {
        data = await getJson(API_INVOICE + "?" + params);
    } catch (error) {
        document.getElementById("listInfo").textContent = "Lỗi: " + error.message;
        document.getElementById("invRows").innerHTML = "";
        return;
    }

    totalPages = Math.max(1, Math.ceil(data.total / data.pageSize));
    let range = "";
    if (from || to) range = " · " + (from ? dateText(from) : "…") + " – " + (to ? dateText(to) : "…");
    document.getElementById("listInfo").innerHTML = `<b>${data.total}</b> hóa đơn${range}`;
    document.getElementById("pageInfo").textContent = "Trang " + data.page + " / " + totalPages;
    document.getElementById("btnPrev").disabled = data.page <= 1;
    document.getElementById("btnNext").disabled = data.page >= totalPages;

    document.getElementById("invRows").innerHTML = data.rows.length === 0
        ? `<tr><td colspan="6" class="muted">Không có hóa đơn phù hợp.</td></tr>`
        : data.rows.map(rowHtml).join("");
}

function rowHtml(r) {
    const [text, css] = STATUS_TEXT[r.status];
    let sub = "";
    if (r.status === "CANCELLED" && r.restocked) sub += '<span class="sub">Đã hoàn kho</span> ';
    if (r.refunded) sub += '<span class="sub">Đã hoàn tiền</span>';
    return `
        <tr data-id="${r.salesInvoiceId}" class="${r.salesInvoiceId === selectedId ? "row-selected" : ""}">
          <td><a href="#hoa-don" class="link">${esc(r.invoiceNo)}</a></td>
          <td>${dateTimeText(r.createdAt)}</td>
          <td>${esc(r.cashierName)}</td>
          <td class="num">${money(r.totalAmount)}</td>
          <td>${r.paymentMethods ? esc(r.paymentMethods) : '<span class="muted">—</span>'}</td>
          <td><span class="status ${css}">${text}</span> ${sub}</td>
        </tr>`;
}

/* =====================================================================
   [153] CHI TIẾT
   ===================================================================== */
async function loadDetail(id) {
    selectedId = id;
    document.querySelectorAll("#invRows tr").forEach(tr =>
        tr.classList.toggle("row-selected", Number(tr.dataset.id) === id));
    try {
        detail = await getJson(API_INVOICE + "?id=" + id);
        renderDetail();
    } catch (error) {
        showDetailMessage(error.message);
    }
}

function showDetailMessage(text) {
    const box = document.getElementById("detailMsg");
    box.textContent = text;
    box.style.display = text ? "block" : "none";
}

function renderDetail() {
    const { order, extra, canCancel } = detail;
    const inv = order.invoice;
    const [text, css] = STATUS_TEXT[inv.status];

    document.getElementById("detailEmpty").style.display = "none";
    document.getElementById("detailBox").style.display = "block";
    showDetailMessage("");

    document.getElementById("dNo").textContent = inv.invoiceNo;
    document.getElementById("dMeta").textContent =
        dateTimeText(inv.createdAt) + " · Thu ngân: " + (inv.cashierName || "");
    const status = document.getElementById("dStatus");
    status.textContent = text;
    status.className = "status " + css;

    // Thông tin hủy
    const cancelBox = document.getElementById("dCancelInfo");
    if (inv.status === "CANCELLED") {
        cancelBox.style.display = "block";
        cancelBox.innerHTML = `
            <div class="kv"><span>Hủy lúc</span><span>${extra.cancelledAt ? dateTimeText(extra.cancelledAt) : ""}</span></div>
            <div class="kv"><span>Người hủy</span><span>${esc(extra.cancelledByName)}</span></div>
            <div class="kv"><span>Lý do</span><span>${esc(extra.cancelReason)}</span></div>
            <div class="kv"><span>Hoàn kho</span><span>${extra.restocked ? "Đã hoàn về lô" : "Không"}</span></div>`;
    } else {
        cancelBox.style.display = "none";
    }

    // Dòng hàng
    document.getElementById("dLines").innerHTML = order.lines.map(l => `
        <tr>
          <td>${esc(l.variantName)}<div class="sub">${weightText(l.weightG)} · ${l.wholeBean ? "Nguyên hạt" : "Xay sẵn"} · ${esc(l.sku)}</div></td>
          <td class="num">${l.quantity}</td>
          <td class="num">${money(l.unitPrice)}</td>
          <td class="num">${money(l.lineAmount)}</td>
        </tr>`).join("") + `
        <tr class="row-total"><td colspan="3">Tổng tiền</td><td class="num">${money(inv.totalAmount)}</td></tr>`;

    // Thanh toán: chỉ giao dịch thành công (thu và hoàn)
    const paid = order.payments.filter(p => p.status === "SUCCESS");
    document.getElementById("dPayments").innerHTML = paid.length === 0
        ? '<div class="muted">Chưa thu tiền.</div>'
        : paid.map(p => `
            <div class="kv"><span>${p.paymentKind === "REFUND" ? "Hoàn tiền · " : ""}${esc(p.methodName)}
              ${p.paidAt ? " · " + dateTimeText(p.paidAt) : ""}</span>
              <b>${p.paymentKind === "REFUND" ? "−" : ""}${money(p.amount)}</b></div>`).join("") +
          `<div class="kv"><span>Đã thu thực tế</span><b>${money(inv.paidAmount)}</b></div>`;

    // Lô đã trừ (đơn chờ thanh toán chưa trừ kho)
    document.getElementById("dLotsBox").style.display = order.allocations.length ? "block" : "none";
        document.getElementById("dLotsTitle").textContent =
        inv.status === "PENDING" ? "Lô đã chọn (chưa trừ kho)" : "Lô đã trừ";
    document.getElementById("dLots").innerHTML = order.allocations.map(a => `
        <tr>
          <td>${esc(a.lotCode)} ${a.nearExpiry && inv.status === "PAID" ? '<span class="tag-near">Cận hạn</span>' : ""}
            <div class="sub">${esc(a.variantName)} ${weightText(a.weightG)}</div></td>
          <td>${dateText(a.expiryDate)}</td>
          <td class="num">${a.quantity}</td>
        </tr>`).join("");

    // In lại: chỉ đơn đã có hóa đơn (đã xác nhận hoặc đã hủy sau xác nhận)
    const printable = inv.status !== "PENDING";
    document.getElementById("dPrintBox").style.display = printable ? "flex" : "none";
    document.getElementById("dReprintCount").textContent = "Đã in lại " + extra.reprintCount + " lần";

    // Hủy đơn đã xác nhận: chỉ chủ cửa hàng, chỉ đơn PAID
    const owner = document.getElementById("ownerBox");
    owner.style.display = canCancel && inv.status === "PAID" ? "block" : "none";
    if (owner.style.display === "block") {
        document.getElementById("cReason").value = "";
        document.getElementById("cRestock").checked = true;
        document.getElementById("cRefund").checked = false;
        document.getElementById("cRestockText").textContent =
            "Hoàn kho về đúng lô đã trừ (" + extra.allocatedQty + " túi)";
        const online = paid.some(p => p.paymentKind === "CHARGE" && p.methodCode !== "CASH");
        document.getElementById("cRefundText").textContent =
            "Trừ tiền: trả lại khách " + money(inv.paidAmount) + " tiền mặt" +
            (online ? " (đơn có trả VNPay, vẫn hoàn bằng tiền mặt tại quầy)" : "");
        document.getElementById("cRefund").disabled = Number(inv.paidAmount) <= 0;
    }
}

/* =====================================================================
   [154 + 156/158 + 157] HỦY ĐƠN ĐÃ XÁC NHẬN
   ===================================================================== */
async function cancelPaidOrder() {
    const inv = detail.order.invoice;
    const reason = document.getElementById("cReason").value.trim();
    const restock = document.getElementById("cRestock").checked;
    const refund = document.getElementById("cRefund").checked;
    if (!reason) {
        showDetailMessage("Nhập lý do hủy");
        return;
    }
    const question = "Hủy hóa đơn " + inv.invoiceNo + "?\n"
        + (restock ? "• Hoàn kho về đúng lô\n" : "• KHÔNG hoàn kho\n")
        + (refund ? "• Trả lại khách " + money(inv.paidAmount) + " tiền mặt\n" : "• KHÔNG trừ tiền (vẫn tính vào thu)\n")
        + "Thao tác này không hoàn tác được.";
    if (!confirm(question)) return;

    const button = document.getElementById("btnCancelPaid");
    button.disabled = true;
    try {
        detail = await postInvoice({
            action: "cancel", id: inv.salesInvoiceId, reason: reason,
            restock: restock, refund: refund
        });
        renderDetail();
        loadList();
    } catch (error) {
        showDetailMessage(error.message);
    } finally {
        button.disabled = false;
    }
}

/* =====================================================================
   [147] IN LẠI HÓA ĐƠN (giá đã gồm VAT, tách dòng thuế theo invoice.vat_rate)
   ===================================================================== */
async function loadStoreInfo() {
    try {
        storeInfo = await getJson(API_OVERVIEW);
    } catch (error) {
        console.error("Không tải được thông tin cửa hàng:", error);
    }
}

/** Tổng đã gồm VAT -> tiền chưa thuế + thuế (làm tròn đồng) */
function vatHtml(total) {
    const rate = Number(storeInfo.vatRate || 0);
    if (!rate) return "";
    const beforeTax = Math.round(Number(total) / (1 + rate / 100));
    return `<div class="kv"><span>Tiền hàng chưa thuế</span><span>${money(beforeTax)}</span></div>
            <div class="kv"><span>Thuế GTGT ${rate}%</span><span>${money(Number(total) - beforeTax)}</span></div>`;
}

function showReprint() {
    const { order, extra } = detail;
    const inv = order.invoice;

    document.getElementById("billStore").textContent = (storeInfo.storeName || "").toUpperCase();
    document.getElementById("billAddress").textContent = storeInfo.address || "";
        document.getElementById("billPhone").textContent = [
        storeInfo.phone ? "ĐT: " + storeInfo.phone : "",
        storeInfo.taxCode ? "MST: " + storeInfo.taxCode : ""
    ].filter(Boolean).join(" · ");
    document.getElementById("billReprint").textContent = "(BẢN IN LẠI lần " + (extra.reprintCount + 1) + ")"
        + (inv.status === "CANCELLED" ? " - ĐÃ HỦY" : "");
    document.getElementById("billNo").textContent = "Số: " + inv.invoiceNo;
    document.getElementById("billMeta").textContent =
        dateTimeText(inv.createdAt) + " · Thu ngân: " + (inv.cashierName || "");

    document.getElementById("billItems").innerHTML = order.lines.map(l => `
        <div class="bill-item">
          <div>${esc(l.variantName)} ${weightText(l.weightG)} ${l.wholeBean ? "nguyên hạt" : "xay sẵn"}</div>
          <div class="kv"><span>${l.quantity} x ${number(l.unitPrice)}</span><span>${number(l.lineAmount)}</span></div>
        </div>`).join("");
    document.getElementById("billVat").innerHTML = vatHtml(inv.totalAmount);
    document.getElementById("billTotal").textContent = money(inv.totalAmount);

    document.getElementById("billPayments").innerHTML = order.payments
        .filter(p => p.status === "SUCCESS")
        .map(p => `<div class="kv"><span>${p.paymentKind === "REFUND" ? "Hoàn tiền " : ""}${esc(p.methodName)}</span>
                     <span>${p.paymentKind === "REFUND" ? "−" : ""}${money(p.amount)}</span></div>`)
        .join("");

    document.getElementById("billFooter").innerHTML =
        esc(storeInfo.footerNote || "Cảm ơn quý khách!") + "<br />Quét mã QR trên túi để xem thông tin cà phê.";

    location.hash = "#modal-bill";
}

async function printReprint() {
    try {
        const result = await postInvoice({ action: "reprint", id: detail.order.invoice.salesInvoiceId });
        detail.extra.reprintCount = result.reprintCount;
        document.getElementById("billReprint").textContent = "(BẢN IN LẠI lần " + result.reprintCount + ")"
            + (detail.order.invoice.status === "CANCELLED" ? " - ĐÃ HỦY" : "");
        document.getElementById("dReprintCount").textContent = "Đã in lại " + result.reprintCount + " lần";
    } catch (error) {
        alert(error.message);
        return;
    }
    window.print();
}

/* ---------- Sự kiện ---------- */
function bindEvents() {
    document.getElementById("btnFilter").addEventListener("click", () => { page = 1; loadList(); });
    document.getElementById("fKeyword").addEventListener("keydown", e => {
        if (e.key === "Enter") { page = 1; loadList(); }
    });
    document.getElementById("btnClearFilter").addEventListener("click", () => {
        document.getElementById("fKeyword").value = "";
        document.getElementById("fFrom").value = "";
        document.getElementById("fTo").value = "";
        document.getElementById("fMethod").value = "";
        document.getElementById("fStatus").value = "";
        page = 1;
        loadList();
    });
    document.getElementById("btnPrev").addEventListener("click", () => { if (page > 1) { page--; loadList(); } });
    document.getElementById("btnNext").addEventListener("click", () => { if (page < totalPages) { page++; loadList(); } });

    // Bấm 1 dòng -> xem chi tiết
    document.getElementById("invRows").addEventListener("click", e => {
        const row = e.target.closest("tr[data-id]");
        if (row) loadDetail(Number(row.dataset.id));
    });

    document.getElementById("btnReprint").addEventListener("click", showReprint);
    document.getElementById("btnDoPrint").addEventListener("click", printReprint);
    document.getElementById("btnCancelPaid").addEventListener("click", cancelPaidOrder);
}

/* ---------- Khởi động: mặc định xem hóa đơn hôm nay ---------- */
document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("fFrom").value = todayIso();
    document.getElementById("fTo").value = todayIso();
    bindEvents();
    loadMethods();
    loadStoreInfo();
    loadList();
});