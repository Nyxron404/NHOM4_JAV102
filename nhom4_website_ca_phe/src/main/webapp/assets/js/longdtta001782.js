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

            // [155] Sửa đơn: chỉ đơn PAID; nhân viên chỉ đơn mình lập trong 10 phút
    const edit = detail.edit || {};
    document.getElementById("dEditBox").style.display = inv.status === "PAID" ? "flex" : "none";
    document.getElementById("btnEdit").disabled = !edit.canEdit;
    document.getElementById("dEditHint").textContent = edit.canEdit
        ? (edit.editSecondsLeft != null ? "Còn " + Math.ceil(edit.editSecondsLeft / 60) + " phút để sửa" : "")
        : (edit.editReason || "");

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

/* =====================================================================
   [155] SỬA ĐƠN ĐÃ XÁC NHẬN (bán theo mã lô)
   Chủ (SALES_INVOICE_EDIT): mọi đơn. Nhân viên: đơn mình lập, trong 10 phút sau khi xác nhận.
   Giảm túi ở lô nào -> trả về đúng lô đó; tăng / thêm lô -> trừ đúng lô đó.
   Quét QR được bán vượt tồn (chủ duyệt sau); gõ tay / bấm + thì không.
   Tổng tăng -> thu tiền mặt ngay; tổng giảm -> hoàn tiền mặt.
   ===================================================================== */

let draft = [];            // [{lotId, lotCode, expiryDate, variantName, weightG, wholeBean, unitPrice, oldQty, qty, scannedAdded, stock}]
let editTimer = null;
let editScanTimes = [];

function openEdit() {
    const { order, edit } = detail;
    if (!edit || !edit.canEdit) return;
    const priceByLine = {};
    order.lines.forEach(l => { priceByLine[l.salesLineId] = l; });

    draft = order.allocations.map(a => {
        const line = priceByLine[a.salesLineId] || {};
        return {
            lotId: a.lotId, lotCode: a.lotCode, expiryDate: a.expiryDate,
            packageId: line.packageId, variantName: a.variantName, weightG: a.weightG, wholeBean: a.wholeBean,
            unitPrice: Number(line.unitPrice), oldQty: a.quantity, qty: a.quantity, scannedAdded: 0,
            stock: a.lotStock
        };
    });

    document.getElementById("eTitle").textContent = order.invoice.invoiceNo;
    document.getElementById("eReason").value = "";
    document.getElementById("eCash").value = "";
    document.getElementById("eScan").value = "";
    showEditMessage("");
    renderDraft();
    startEditTimer(edit.editSecondsLeft);
    location.hash = "#modal-sua";
    document.getElementById("eScan").focus();
}

function startEditTimer(seconds) {
    clearInterval(editTimer);
    const box = document.getElementById("eTimer");
    if (seconds === null || seconds === undefined) {
        box.textContent = "";
        return;
    }
    let left = seconds;
    const tick = () => {
        if (left <= 0) {
            clearInterval(editTimer);
            box.textContent = "Đã hết thời gian sửa đơn";
            document.getElementById("btnSaveEdit").disabled = true;
            return;
        }
        box.textContent = "Còn " + Math.floor(left / 60) + ":" + String(left % 60).padStart(2, "0") + " để sửa đơn";
        left--;
    };
    document.getElementById("btnSaveEdit").disabled = false;
    tick();
    editTimer = setInterval(tick, 1000);
}

function showEditMessage(text, ok) {
    const box = document.getElementById("eMsg");
    box.textContent = text || "";
    box.style.display = text ? "block" : "none";
    box.classList.toggle("msg-warn", !!ok);
}

/* ---------- Vẽ bản nháp ---------- */
function renderDraft() {
    document.getElementById("eLines").innerHTML = draft.length === 0
        ? '<div class="order-empty">Đơn chưa có gói nào</div>'
        : draft.map((d, i) => {
            const diff = d.qty - d.oldQty;
            const over = diff > 0 ? diff - Math.max(d.stock, 0) : 0;
            return `
            <div class="lot-row ${d.qty === 0 ? "lot-removed" : ""}">
              <div class="lot-info">
                <span class="line-name">${esc(d.variantName)} · ${weightText(d.weightG)} ${d.wholeBean ? "nguyên hạt" : "xay sẵn"}</span>
                <span class="lot-code">${esc(d.lotCode)} · HSD ${dateText(d.expiryDate)}</span>
                <span class="sub">
                  ${d.oldQty ? "Trước: " + d.oldQty : "Lô mới"} · ${money(d.unitPrice)}/túi
                  ${diff < 0 ? ` · <b>trả ${-diff} túi về lô</b>` : ""}
                  ${diff > 0 ? ` · <b>trừ thêm ${diff} túi</b>` : ""}
                  ${d.scannedAdded ? " · 📷 quét " + d.scannedAdded : ""}
                </span>
                ${over > 0 ? `<span class="sub text-warn">Vượt tồn ${over} túi${over > d.scannedAdded ? " → phải quét QR mới được bán" : " → bán âm, chủ duyệt sau"}</span>` : ""}
              </div>
              <div class="qty">
                <button data-edit="minus" data-i="${i}" ${d.qty <= 0 ? "disabled" : ""}>−</button>
                <input class="qty-input edit-qty" type="number" min="0" value="${d.qty}" data-i="${i}" />
                <button data-edit="plus" data-i="${i}">+</button>
              </div>
            </div>`;
        }).join("");

    const before = Number(detail.order.invoice.totalAmount);
    const after = draft.reduce((sum, d) => sum + d.qty * d.unitPrice, 0);
    const paid = Number(detail.order.invoice.paidAmount);
    const diff = after - paid;
    document.getElementById("eBefore").textContent = money(before);
    document.getElementById("eAfter").textContent = money(after);
    document.getElementById("ePaid").textContent = money(paid);
    document.getElementById("eDiffLabel").textContent = diff > 0 ? "Thu thêm (tiền mặt)" : diff < 0 ? "Hoàn lại khách (tiền mặt)" : "Chênh lệch";
    document.getElementById("eDiff").textContent = money(Math.abs(diff));
    document.getElementById("eCashBox").style.display = diff > 0 ? "" : "none";
}

/* ---------- Đổi số túi ---------- */
function setDraftQty(i, qty) {
    const d = draft[i];
    d.qty = Math.max(0, qty);
    d.scannedAdded = Math.min(d.scannedAdded, Math.max(0, d.qty - d.oldQty));
    renderDraft();
}

// Quét / gõ mã lô: mode "add" thêm 1 túi, "return" trả 1 túi
async function handleEditCode(code, scanned) {
    code = (code || "").trim();
    if (!code) return;
    const mode = document.getElementById("eMode").value;
    let i = draft.findIndex(d => d.lotCode.toUpperCase() === code.toUpperCase());

    if (mode === "return") {
        if (i < 0 || draft[i].qty === 0) {
            showEditMessage("Lô " + code + " không có trong đơn");
            return;
        }
        setDraftQty(i, draft[i].qty - 1);
        showEditMessage("");
        return;
    }

    if (i < 0) {
        let lot;
        try {
            lot = await getJson(API_INVOICE + "?action=lot&code=" + encodeURIComponent(code));
        } catch (error) {
            showEditMessage(error.message);
            return;
        }
        if (lot.expired) { showEditMessage("Lô " + lot.lotCode + " đã hết hạn, không bán"); return; }
        if (!lot.active) { showEditMessage("Gói " + lot.sku + " đã ngừng bán"); return; }
        // Gói đã có trong đơn (lô khác) thì giữ giá cũ của dòng, gói mới lấy giá hiện hành
        const same = draft.find(d => d.packageId === lot.packageId);
        const price = same ? same.unitPrice : Number(lot.salePrice);
        if (!same && lot.salePrice === null) { showEditMessage("Gói " + lot.sku + " chưa có giá bán"); return; }
        draft.push({
            lotId: lot.lotId, lotCode: lot.lotCode, expiryDate: lot.expiryDate, packageId: lot.packageId,
            variantName: lot.variantName, weightG: lot.weightG, wholeBean: lot.wholeBean,
            unitPrice: price, oldQty: 0, qty: 0, scannedAdded: 0, stock: lot.stock
        });
        i = draft.length - 1;
    }
    const d = draft[i];
    d.qty += 1;
    if (scanned && d.qty > d.oldQty) d.scannedAdded += 1;
    showEditMessage("");
    renderDraft();
}

/* ---------- Lưu ---------- */
async function saveEdit() {
    const reason = document.getElementById("eReason").value.trim();
    if (!reason) { showEditMessage("Nhập lý do sửa đơn"); return; }
    if (!draft.some(d => d.qty > 0)) { showEditMessage("Đơn không còn gói nào. Muốn bỏ hết thì dùng Hủy đơn."); return; }
    if (!draft.some(d => d.qty !== d.oldQty)) { showEditMessage("Chưa có thay đổi nào"); return; }
    const due = draft.reduce((sum, d) => sum + d.qty * d.unitPrice, 0) - Number(detail.order.invoice.paidAmount);
    const cash = Number(document.getElementById("eCash").value.replace(/\D/g, "") || 0);
    if (due > 0 && cash < due) { showEditMessage("Phải thu thêm " + money(due) + ", khách đưa chưa đủ"); return; }

    const button = document.getElementById("btnSaveEdit");
    button.disabled = true;
    const totalBefore = Number(detail.order.invoice.totalAmount);
    const paidBefore = Number(detail.order.invoice.paidAmount);
    try {
        detail = await postInvoice({
            action: "edit",
            id: detail.order.invoice.salesInvoiceId,
            lots: draft.map(d => d.lotId + ":" + d.qty + ":" + d.scannedAdded).join(";"),
            cashGiven: document.getElementById("eCash").value,
            reason: reason
        });
                clearInterval(editTimer);
        stopEditCamera();
        location.hash = "#hoa-don";
        renderDetail();
        loadList();
        const after = Number(detail.order.invoice.totalAmount);
        const diff = after - paidBefore;
        showDetailMessage("Đã sửa đơn: " + money(totalBefore) + " → " + money(after)
            + (diff > 0 ? " · đã thu thêm " + money(diff) : diff < 0 ? " · đã hoàn khách " + money(-diff) + " tiền mặt" : ""));
    } catch (error) {
        showEditMessage(error.message);
    } finally {
        button.disabled = false;
    }
}

/* ---------- Camera trong hộp thoại sửa đơn (giống máy quét, quét liên tục) ---------- */
let eCamStream = null;
let eCamTimer = null;
let eCamDetector = null;
// Chống đọc trùng: cùng 1 mã chỉ tính lại khi mã đã rời khung hình (có khung không thấy mã) và sau 1 giây
let eCamLast = { code: "", at: 0, gone: true };

const JSQR_URL = "https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js";

function setECamStatus(text, error) {
    const box = document.getElementById("eCamStatus");
    box.textContent = text;
    box.classList.toggle("wait-fail", !!error);
}

async function toggleEditCamera() {
    if (eCamStream) { stopEditCamera(); return; }
    document.getElementById("eCamBox").style.display = "block";
    document.getElementById("eCamBtn").textContent = "✕ Tắt camera";
    if (!window.isSecureContext || !navigator.mediaDevices) {
        setECamStatus("Trình duyệt chỉ cho mở camera trên localhost hoặc https.", true);
        return;
    }
    setECamStatus("Đang mở camera…");
    try {
        if ("BarcodeDetector" in window && !eCamDetector) {
            const formats = await BarcodeDetector.getSupportedFormats();
            if (formats.includes("qr_code")) {
                eCamDetector = new BarcodeDetector({ formats: formats.filter(f => ["qr_code", "code_128"].includes(f)) });
            }
        }
        if (!eCamDetector && !window.jsQR) await loadScriptOnce(JSQR_URL);

        eCamStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
        const video = document.getElementById("eCamVideo");
        video.srcObject = eCamStream;
        await video.play();
        setECamStatus("Đưa mã QR trên nhãn lô vào khung hình (" +
            (document.getElementById("eMode").value === "return" ? "Trả túi" : "Thêm túi") + ")");
        eCamTimer = setInterval(scanEditFrame, 250);
    } catch (error) {
        setECamStatus("Không mở được camera: " + error.message, true);
        stopEditCamera(true);
    }
}

async function scanEditFrame() {
    const video = document.getElementById("eCamVideo");
    if (!eCamStream || video.readyState < 2) return;
    let code = null;
    try {
        if (eCamDetector) {
            const found = await eCamDetector.detect(video);
            if (found.length) code = found[0].rawValue;
        } else if (window.jsQR) {
            const canvas = document.getElementById("eCamCanvas");
            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;
            const ctx = canvas.getContext("2d", { willReadFrequently: true });
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const found = window.jsQR(ctx.getImageData(0, 0, canvas.width, canvas.height).data,
                canvas.width, canvas.height);
            if (found) code = found.data;
        }
    } catch (error) {
        return;
    }
    if (!code) {
        eCamLast.gone = true;                   // mã đã rời khung hình
        return;
    }
    const now = Date.now();
    if (code === eCamLast.code && (!eCamLast.gone || now - eCamLast.at < 1000)) return;
    eCamLast = { code: code, at: now, gone: false };
    setECamStatus("Đã đọc " + code);
    handleEditCode(code, true);                 // giống máy quét
}

// keepBox = true: giữ khung để hiện lỗi
function stopEditCamera(keepBox) {
    clearInterval(eCamTimer);
    eCamTimer = null;
    if (eCamStream) eCamStream.getTracks().forEach(t => t.stop());
    eCamStream = null;
    document.getElementById("eCamBtn").textContent = "📷 Camera";
    if (!keepBox) document.getElementById("eCamBox").style.display = "none";
}

function loadScriptOnce(url) {
    return new Promise((resolve, reject) => {
        const s = document.createElement("script");
        s.src = url;
        s.onload = resolve;
        s.onerror = () => reject(new Error("không tải được thư viện đọc QR (cần mạng)"));
        document.head.appendChild(s);
    });
}

function bindEditEvents() {
    document.getElementById("btnEdit").addEventListener("click", openEdit);
    document.getElementById("btnSaveEdit").addEventListener("click", saveEdit);

    const lines = document.getElementById("eLines");
    lines.addEventListener("click", e => {
        const b = e.target.closest("button[data-edit]");
        if (!b || b.disabled) return;
        const i = Number(b.dataset.i);
        setDraftQty(i, draft[i].qty + (b.dataset.edit === "plus" ? 1 : -1));
    });
    lines.addEventListener("change", e => {
        if (!e.target.classList.contains("edit-qty")) return;
        const q = parseInt(e.target.value, 10);
        setDraftQty(Number(e.target.dataset.i), isNaN(q) ? draft[Number(e.target.dataset.i)].qty : q);
    });

    // Ô quét: máy quét gõ rất nhanh (< 35ms/ký tự) -> coi là quét
    const scan = document.getElementById("eScan");
    scan.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            const t = editScanTimes;
            const avg = t.length > 1 ? (t[t.length - 1] - t[0]) / (t.length - 1) : 999;
            handleEditCode(scan.value, scan.value.trim().length >= 4 && avg < 35);
            scan.value = "";
            editScanTimes = [];
            return;
        }
        if (e.key.length === 1) editScanTimes.push(performance.now());
    });

        document.getElementById("eCamBtn").addEventListener("click", toggleEditCamera);
    // Đổi chế độ Thêm / Trả khi camera đang mở -> cập nhật dòng hướng dẫn
    document.getElementById("eMode").addEventListener("change", () => {
        if (eCamStream) setECamStatus("Đưa mã QR trên nhãn lô vào khung hình ("
            + (document.getElementById("eMode").value === "return" ? "Trả túi" : "Thêm túi") + ")");
    });

    window.addEventListener("hashchange", () => {
        if (location.hash !== "#modal-sua") {
            clearInterval(editTimer);
            stopEditCamera();                      // đóng hộp thoại -> tắt camera
        }
    });
}

/* ---------- Khởi động: mặc định xem hóa đơn hôm nay ---------- */
document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("fFrom").value = todayIso();
    document.getElementById("fTo").value = todayIso();
    bindEvents();
        bindEditEvents();
    loadMethods();
    loadStoreInfo();
    loadList();
});