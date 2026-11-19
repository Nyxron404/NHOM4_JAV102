/* =====================================================================
   TRANG BÁN HÀNG (longdtta00178) — Bước 1: tải thẻ cà phê + gói bán từ DB
   API: ../api/ban-hang/san-pham   (SalesProductController)
   ===================================================================== */

const API_PRODUCTS = "../../api/ban-hang/san-pham";

// Trạng thái bộ lọc hiện tại
const filter = {
    q: "",
    species: "",       // "" = tất cả
    form: "",          // "" | "WB" | "GROUND"
    regionId: "",
    roastLevelId: "",
    inStock: false
};

/* ---------- Tiện ích ---------- */
function money(value) {
    return Number(value).toLocaleString("vi-VN") + "đ";
}

function weightText(gram) {
    return gram >= 1000 ? (gram / 1000) + "kg" : gram + "g";
}

// Chống chèn mã HTML từ dữ liệu DB
function esc(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/* ---------- Gọi API ---------- */
async function getJson(url) {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || ("Lỗi " + response.status));
    return data;
}

async function loadFilterOptions() {
    const data = await getJson(API_PRODUCTS + "?action=filters");

    fillSelect("regionSelect", data.regions, "Mọi vùng trồng");
    fillSelect("roastSelect", data.roastLevels, "Mọi mức rang");

    // Chip loài: "Tất cả" + từng loài trong DB
    const box = document.getElementById("speciesChips");
    box.innerHTML = '<span class="filter-label">Loài</span>'
        + '<a href="#ban-hang" class="chip active" data-species="">Tất cả</a>'
        + data.species.map(s =>
            `<a href="#ban-hang" class="chip" data-species="${esc(s.name)}">${esc(s.name)}</a>`).join("");
}

function fillSelect(id, items, firstText) {
    const select = document.getElementById(id);
    select.innerHTML = `<option value="">${firstText}</option>`
        + items.map(i => `<option value="${i.id}">${esc(i.name)}</option>`).join("");
}

async function loadProducts() {
    const grid = document.getElementById("productGrid");
    grid.innerHTML = '<div class="empty">Đang tải sản phẩm…</div>';

    const params = new URLSearchParams();
    if (filter.q) params.set("q", filter.q);
    if (filter.species) params.set("species", filter.species);
    if (filter.form) params.set("form", filter.form);
    if (filter.regionId) params.set("regionId", filter.regionId);
    if (filter.roastLevelId) params.set("roastLevelId", filter.roastLevelId);
    if (filter.inStock) params.set("inStock", "true");

    try {
        const cards = await getJson(API_PRODUCTS + "?" + params.toString());
        renderCards(cards);
    } catch (error) {
        grid.innerHTML = `<div class="empty empty-error">${esc(error.message)}</div>`;
        document.getElementById("productCount").innerHTML = "";
    }
}

/* ---------- Vẽ giao diện ---------- */
function renderCards(cards) {
    const grid = document.getElementById("productGrid");
    const packageCount = cards.reduce((sum, c) => sum + c.packages.length, 0);
    document.getElementById("productCount").innerHTML =
        `<b>${cards.length}</b> loại cà phê · ${packageCount} gói bán`;

    if (cards.length === 0) {
        grid.innerHTML = '<div class="empty">Không có cà phê phù hợp bộ lọc.</div>';
        return;
    }
    grid.innerHTML = cards.map(cardHtml).join("");
}

function cardHtml(card) {
    const species = card.speciesName || "";
    const meta = [card.regionName, card.processName, card.roastName].filter(Boolean).map(esc).join(" · ");
    const notes = (card.flavorNotes || "").split(",").map(n => n.trim()).filter(Boolean)
        .map(n => `<span class="note">${esc(n)}</span>`).join("");
    const image = card.imageUrl
        ? `<img class="card-img" src="${esc(card.imageUrl)}" alt="${esc(card.variantName)}" />`
        : '<div class="card-img"></div>';

    return `
      <div class="card">
        <div class="card-top">
          ${image}
          <div class="card-info">
            <div class="card-line">${esc(card.productName)}</div>
            <div class="card-name">${esc(card.variantName)}</div>
            <div class="card-meta">
              ${species ? `<span class="badge badge-${esc(species.toLowerCase())}">${esc(species)}</span>` : ""} ${meta}
            </div>
            <div class="card-notes">${notes}</div>
          </div>
        </div>
        <div class="pkg-list">
          ${card.packages.map(packageHtml).join("")}
        </div>
      </div>`;
}

function packageHtml(p) {
    const form = p.wholeBean ? "Nguyên hạt" : "Xay sẵn";
    let rowClass = "pkg";
    let stockText = "Còn " + p.sellableQty;

    if (p.salePrice === null) {
        rowClass += " pkg-off";
        stockText = "Chưa có giá";
    } else if (p.sellableQty <= 0) {
        rowClass += " pkg-off";
        stockText = "Hết hàng";
    } else if (p.lowStock) {
        rowClass += " pkg-low";
        stockText = "Sắp hết · " + p.sellableQty;
    }

    // Ô chọn lô: lô còn hạn, còn hàng, hạn gần nhất đứng đầu (chọn sẵn); tìm theo mã lô thì chọn sẵn lô đó
    const lots = p.lots || [];
        // Lô đang tìm không bán được (hết hàng / hết hạn): không chọn sẵn lô khác, bắt người bán tự chọn
    const selected = p.matchedLotNote ? null : (p.matchedLotId || (lots[0] && lots[0].lotId));
    const lotBox = lots.length === 0
        ? '<span class="lot-none">Không có lô bán được</span>'
        : `<select class="lot-select" title="Chọn đúng lô của túi giao cho khách">
                     ${p.matchedLotNote ? '<option value="" selected disabled>— Chọn lô khác —</option>' : ""}
             ${lots.map(l => `<option value="${l.lotId}" title="${esc(l.lotCode)}" ${l.lotId === selected ? "selected" : ""}>
               Lô ${esc(shortLot(l.lotCode, p.sku))} · HSD ${dateShort(l.expiryDate)} · còn ${l.qty}${l.nearExpiry ? " · cận hạn" : ""}
             </option>`).join("")}
           </select>`;

    return `
          <div class="${rowClass}">
            <div class="pkg-name">${weightText(p.weightG)} · ${form}</div>
            <div class="pkg-price">${p.salePrice === null ? "—" : money(p.salePrice)}</div>
            <div class="pkg-sku" title="${esc(p.grindName)}">${esc(p.sku)}</div>
            <div class="pkg-stock">${stockText}</div>
                        ${p.matchedLotNote ? `<div class="pkg-note">⚠ ${esc(p.matchedLotNote)}</div>` : ""}
            <div class="pkg-actions">
              ${lotBox}
              <button class="pkg-add" title="Thêm 1 túi của lô đang chọn"
                      ${p.canSell && lots.length ? "" : "disabled"}>+</button>
            </div>
          </div>`;
}

// Mã lô = "L" + ngày rang + "-" + SKU -> bỏ phần SKU (đã hiện ở trên) cho gọn: L261108-CD-MOKA-WS-250-WB -> L261108
function shortLot(lotCode, sku) {
    return lotCode.endsWith("-" + sku) ? lotCode.slice(0, -(sku.length + 1)) : lotCode;
}

// "2027-11-08" -> 08/11/27
function dateShort(iso) {
    const [y, m, d] = String(iso).split("-");
    return d + "/" + m + "/" + y.slice(2);
}

/* ---------- Sự kiện bộ lọc ---------- */
function setActiveChip(container, clicked) {
    container.querySelectorAll(".chip").forEach(c => c.classList.remove("active"));
    clicked.classList.add("active");
}

function bindFilters() {
    // Ô tìm kiếm: chờ người dùng ngừng gõ 300ms mới tải
    let timer = null;
    document.getElementById("searchInput").addEventListener("input", e => {
        clearTimeout(timer);
        timer = setTimeout(() => { filter.q = e.target.value.trim(); loadProducts(); }, 300);
    });

    document.getElementById("speciesChips").addEventListener("click", e => {
        const chip = e.target.closest(".chip");
        if (!chip) return;
        e.preventDefault();
        setActiveChip(e.currentTarget, chip);
        filter.species = chip.dataset.species;
        loadProducts();
    });

    document.getElementById("formChips").addEventListener("click", e => {
        const chip = e.target.closest(".chip");
        if (!chip) return;
        e.preventDefault();
        setActiveChip(e.currentTarget, chip);
        filter.form = chip.dataset.form;
        loadProducts();
    });

    document.getElementById("regionSelect").addEventListener("change", e => {
        filter.regionId = e.target.value; loadProducts();
    });
    document.getElementById("roastSelect").addEventListener("change", e => {
        filter.roastLevelId = e.target.value; loadProducts();
    });
    document.getElementById("inStockCheck").addEventListener("change", e => {
        filter.inStock = e.target.checked; loadProducts();
    });

        // Nút + : thêm gói vào đơn (UC 130 + 132)
    document.getElementById("productGrid").addEventListener("click", e => {
                const button = e.target.closest(".pkg-add");
        if (!button || button.disabled) return;
                const select = button.closest(".pkg").querySelector(".lot-select");
        if (!select || !select.value) {
            showOrderMessage("Chọn lô trước khi bấm +");
            return;
        }
        addToOrder({ lotId: Number(select.value) });
    });
}

/* =====================================================================
   ĐƠN ĐANG LẬP (UC 130 tạo đơn, 132 thêm gói / đổi SL, 134 xóa gói)
   API: ../api/ban-hang/don   (SalesOrderController)
   Mã đơn đang lập lưu ở localStorage để F5 trang không mất đơn.
   ===================================================================== */

const API_ORDER = "../../api/ban-hang/don";
const ORDER_KEY = "pos.currentInvoiceId";
let currentOrder = null;          // {invoice:{...}, lines:[...]} hoặc null = chưa có đơn

const STATUS_TEXT = {
    PENDING: ["Chờ thanh toán", "status-pending"],
    PAID: ["Đã xác nhận", "status-paid"],
    CANCELLED: ["Đã hủy", "status-cancel"]
};

/* ---------- Gọi API ---------- */
// Gửi POST dạng form (servlet đọc bằng req.getParameter)
async function postOrder(data) {
    const response = await fetch(API_ORDER, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams(data)
    });
    const json = await response.json();
    if (!response.ok) throw new Error(json.error || ("Lỗi " + response.status));
    return json;
}

function saveOrder(order) {
    currentOrder = order;
    if (order && order.invoice.status === "PENDING") {
        localStorage.setItem(ORDER_KEY, order.invoice.salesInvoiceId);
    } else {
        localStorage.removeItem(ORDER_KEY);
    }
    renderOrder();
    updatePayUI();
}

// Mở trang: lấy lại đơn đang lập (nếu có)
async function loadCurrentOrder() {
    const id = localStorage.getItem(ORDER_KEY);
    if (!id) { saveOrder(null); return; }
    try {
        const order = await getJson(API_ORDER + "?id=" + id);
        saveOrder(order.invoice.status === "PENDING" ? order : null);   // đơn đã đóng -> bắt đầu đơn mới
    } catch (error) {
        saveOrder(null);
    }
}

// Bấm + ở gói: chưa có đơn thì tạo đơn trước (130), rồi thêm gói (132)
/* Các thao tác sửa đơn chạy lần lượt (xếp hàng).
   Bấm + nhanh nhiều lần: lần sau đợi lần trước xong -> chỉ tạo 1 đơn, số lượng cộng dồn đúng. */
let orderQueue = Promise.resolve();

function runInOrder(task) {
    const run = orderQueue.then(task, task);
    orderQueue = run.catch(() => {});
    return run;
}

/* Thêm 1 túi theo lô (bán theo mã lô):
   {lotId}                     chọn ở ô thả xuống
   {lotCode, scanned: true}    quét QR (máy quét / camera) -> được bán vượt tồn, chủ duyệt sau
   {lotCode, scanned: false}   gõ tay mã lô -> không được vượt tồn */
function addToOrder(lot) {
    return runInOrder(() => addToOrderNow(lot));
}

async function addToOrderNow(lot) {
    try {
        if (!currentOrder) {
            saveOrder(await postOrder({ action: "create" }));
        }
        const data = { action: "addLot", invoiceId: currentOrder.invoice.salesInvoiceId };
        if (lot.lotId) data.lotId = lot.lotId;
        if (lot.lotCode) data.lotCode = lot.lotCode;
        data.scanned = lot.scanned === true;
        const order = await postOrder(data);
        saveOrder(order);
        showOrderMessage(order.warning || "", !!order.warning);
    } catch (error) {
        showOrderMessage(error.message);
    }
}

function changeLotQty(lineId, lotId, quantity) {
    return runInOrder(() => changeLotQtyNow(lineId, lotId, quantity));
}

async function changeLotQtyNow(lineId, lotId, quantity) {
    try {
        saveOrder(await postOrder({
            action: "updateLotQty",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            lineId: lineId,
            lotId: lotId,
            quantity: quantity
        }));
        showOrderMessage("");
    } catch (error) {
        showOrderMessage(error.message);
        renderOrder();                       // trả ô số lượng về giá trị cũ
    }
}

function removeLine(lineId) {
    return runInOrder(() => removeLineNow(lineId));
}

async function removeLineNow(lineId) {
    try {
        saveOrder(await postOrder({
            action: "removeLine",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            lineId: lineId
        }));
        showOrderMessage("");
    } catch (error) {
        showOrderMessage(error.message);
    }
}

/* ---------- Vẽ đơn ---------- */
// warn = true: cảnh báo màu cam (VD quét lô vượt tồn), mặc định: lỗi màu đỏ
function showOrderMessage(text, warn) {
    const box = document.getElementById("orderMsg");
    box.textContent = text;
    box.style.display = text ? "block" : "none";
    box.classList.toggle("msg-warn", !!warn);
}

function dateTimeText(iso) {
    const d = new Date(iso);
    return d.toLocaleDateString("vi-VN") + " " +
        d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

function renderOrder() {
    const linesBox = document.getElementById("orderLines");

    if (!currentOrder) {
        document.getElementById("orderNo").textContent = "Đơn mới";
        document.getElementById("orderMeta").textContent = "Quét mã lô hoặc bấm + ở gói để bắt đầu";
        document.getElementById("orderStatus").innerHTML = "";
        linesBox.innerHTML = '<div class="order-empty">Chưa có gói nào trong đơn</div>';
        setSummary(0, 0, 0, 0, 0);
        return;
    }

    const inv = currentOrder.invoice;
    const lines = currentOrder.lines;
    const [statusText, statusClass] = STATUS_TEXT[inv.status] || [inv.status, ""];

    document.getElementById("orderNo").textContent = inv.invoiceNo;
    document.getElementById("orderMeta").textContent =
        dateTimeText(inv.createdAt) + " · Thu ngân: " + (inv.cashierName || "");
    document.getElementById("orderStatus").innerHTML =
        `<span class="status ${statusClass}">${statusText}</span>`;

    linesBox.innerHTML = lines.length === 0
        ? '<div class="order-empty">Chưa có gói nào trong đơn</div>'
        : lines.map(lineHtml).join("");

    const totalQty = lines.reduce((sum, l) => sum + l.quantity, 0);
    setSummary(totalQty, lines.length, inv.totalAmount, inv.paidAmount, inv.dueAmount);
}

function lineHtml(l) {
    const form = l.wholeBean ? "Nguyên hạt" : "Xay sẵn";
    const allocations = (currentOrder.allocations || []).filter(a => a.salesLineId === l.salesLineId);
    const allocated = allocations.reduce((sum, a) => sum + a.quantity, 0);

    const lotRows = allocations.map(a => {
        const over = a.quantity - Math.max(a.lotStock, 0);          // số túi vượt tồn hệ thống
        return `
        <div class="lot-row">
          <div class="lot-info">
            <span class="lot-code">${esc(a.lotCode)}</span>
            <span class="sub">HSD ${dateShort(a.expiryDate)}${a.scannedQty ? " · 📷 quét " + a.scannedQty : ""}</span>
            ${over > 0 ? `<span class="sub text-warn">Vượt tồn ${over} túi → bán âm, chủ duyệt sau</span>` : ""}
          </div>
          <div class="qty">
            <button data-action="lot-minus" data-line-id="${l.salesLineId}" data-lot-id="${a.lotId}">−</button>
            <input class="qty-input lot-qty-input" type="number" min="0" value="${a.quantity}"
                   data-line-id="${l.salesLineId}" data-lot-id="${a.lotId}" />
            <button data-action="lot-plus" data-line-id="${l.salesLineId}" data-lot-id="${a.lotId}">+</button>
          </div>
        </div>`;
    }).join("");

    return `
      <div class="line">
        <div class="line-top">
          <div>
            <div class="line-name">${esc(l.variantName)}</div>
            <div class="sub">${weightText(l.weightG)} · ${form} · ${money(l.unitPrice)} · ${l.quantity} túi</div>
          </div>
          <div class="line-right">
            <span class="line-amount">${money(l.lineAmount)}</span>
            <button class="icon-btn" title="Bỏ khỏi đơn" data-action="remove" data-line-id="${l.salesLineId}">✕</button>
          </div>
        </div>
        ${lotRows}
        ${allocated < l.quantity
            ? '<div class="sub text-warn">Gói này chưa chọn lô (đơn tạo trước khi bán theo lô). Bỏ gói rồi thêm lại theo lô.</div>'
            : ""}
      </div>`;
}

function setSummary(qty, packageCount, total, paid, due) {
    document.getElementById("sumQty").textContent = qty + " túi · " + packageCount + " gói";
    document.getElementById("sumTotal").textContent = money(total);
    document.getElementById("sumPaid").textContent = money(paid);
    document.getElementById("sumDue").textContent = money(due);
}

/* ---------- Sự kiện trong đơn ---------- */
function bindOrderEvents() {
    const linesBox = document.getElementById("orderLines");

    // Nút −, + của từng lô; ✕ bỏ cả gói
    linesBox.addEventListener("click", e => {
        const button = e.target.closest("button[data-action]");
        if (!button || button.disabled || !currentOrder) return;
        const lineId = Number(button.dataset.lineId);

        if (button.dataset.action === "remove") {
            removeLine(lineId);
            return;
        }
        const lotId = Number(button.dataset.lotId);
        const a = currentOrder.allocations.find(x => x.salesLineId === lineId && x.lotId === lotId);
        if (!a) return;
        if (button.dataset.action === "lot-minus") changeLotQty(lineId, lotId, a.quantity - 1);
        if (button.dataset.action === "lot-plus") changeLotQty(lineId, lotId, a.quantity + 1);
    });

    // Gõ số túi của lô rồi Enter / rời ô (0 = bỏ lô đó)
    linesBox.addEventListener("change", e => {
        if (!e.target.classList.contains("lot-qty-input")) return;
        const quantity = parseInt(e.target.value, 10);
        if (isNaN(quantity) || quantity < 0) { renderOrder(); return; }
        changeLotQty(Number(e.target.dataset.lineId), Number(e.target.dataset.lotId), quantity);
    });

    // Đơn mới: để đơn hiện tại ở trạng thái chờ (xem lại ở tab Hóa đơn), bắt đầu đơn trống
    document.getElementById("btnNewOrder").addEventListener("click", () => {
        saveOrder(null);
        showOrderMessage("");
        loadOverview();                  // cập nhật số đơn chờ
    });
}

/* =====================================================================
   THU TIỀN MẶT + XÁC NHẬN ĐƠN (138, 140, 142, 143) · HỦY ĐƠN (135)
   IN HÓA ĐƠN (145, 146) · CHỈ SỐ TRONG NGÀY
   ===================================================================== */

let storeInfo = {};          // thông tin in hóa đơn (system_settings invoice.*)
let lastConfirmed = null;    // đơn vừa xác nhận: {order, cashGiven, change} -> hộp thoại + in hóa đơn

/* ---------- Tiền ---------- */
// "500.000" -> 500000
function parseMoney(text) {
    const digits = String(text || "").replace(/[^0-9]/g, "");
    return digits ? Number(digits) : 0;
}

function dueAmount() {
    return currentOrder ? Number(currentOrder.invoice.dueAmount) : 0;
}

// Gợi ý tiền khách đưa: đủ tiền, làm tròn lên 50k / 100k / 500k
function cashSuggestions(due) {
    if (due <= 0) return [];
    const list = [due];
    [50000, 100000, 500000].forEach(step => {
        const value = Math.ceil(due / step) * step;
        if (!list.includes(value)) list.push(value);
    });
    return list.slice(0, 4);
}

/* ---------- Chỉ số trong ngày + thông tin cửa hàng ---------- */
async function loadOverview() {
    try {
        const data = await getJson(API_ORDER + "?action=overview");
        document.getElementById("statIncome").textContent = money(data.incomeToday);
        document.getElementById("statIncomeSub").textContent =
            "Tiền mặt " + money(data.cashToday) + " · VNPay " + money(data.onlineToday);
        document.getElementById("statConfirmed").textContent = data.confirmedToday;
        document.getElementById("statToday").textContent = "Hôm nay " + new Date().toLocaleDateString("vi-VN");
        document.getElementById("statPending").textContent = data.pendingCount;
                document.getElementById("pendingBadge").textContent = data.pendingCount;
        storeInfo = data;
        updatePayUI();
    } catch (error) {
        console.error("Không tải được chỉ số:", error);
    }
}

/* ---------- Khu thanh toán: bật/tắt nút, tiền thối ---------- */
function updatePayUI() {
    const hasLines = currentOrder && currentOrder.lines.length > 0;
    const due = dueAmount();
    const given = parseMoney(document.getElementById("cashGiven").value);

    // Nút gợi ý tiền
    document.getElementById("quickCash").innerHTML = cashSuggestions(due)
        .map((v, i) => `<button class="btn btn-sm" data-cash="${v}">${i === 0 ? "Đủ tiền" : money(v)}</button>`)
        .join("");

    // Tiền thối
    const change = given - due;
    const changeBox = document.getElementById("cashChange");
    if (due <= 0) {
        changeBox.textContent = "0đ";
    } else if (given === 0) {
        changeBox.textContent = "—";
    } else if (change < 0) {
        changeBox.textContent = "Còn thiếu " + money(-change);
    } else {
        changeBox.textContent = money(change);
    }
    changeBox.parentElement.classList.toggle("kv-short", given > 0 && change < 0);

    document.getElementById("btnCancelOrder").disabled = !currentOrder;
        const vnpayOff = storeInfo.vnpayActive === false;
    document.getElementById("btnVnpay").disabled = !hasLines || due <= 0 || vnpayOff;
    document.getElementById("btnVnpay").title = vnpayOff ? "VNPay đang tắt (trang Cấu hình)" : "";
    document.getElementById("btnConfirm").disabled = !hasLines || (due > 0 && given < due);
}

/* ---------- Xác nhận đơn ---------- */
async function confirmOrder() {
    if (!currentOrder) return;
    const due = dueAmount();
    const given = parseMoney(document.getElementById("cashGiven").value);
    const button = document.getElementById("btnConfirm");
    button.disabled = true;                      // chống bấm 2 lần
    try {
        const order = await postOrder({
            action: "confirm",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            cashGiven: due > 0 ? given : ""
        });
        lastConfirmed = { order: order, cashGiven: due > 0 ? given : 0, change: due > 0 ? given - due : 0 };

        document.getElementById("cashGiven").value = "";
        saveOrder(null);                         // đơn đã đóng -> khung đơn trở về trống
        showOrderMessage("");
        showConfirmModal();
        loadProducts();                          // tồn đã đổi
        loadOverview();                          // thu hôm nay đã đổi
    } catch (error) {
        showOrderMessage(error.message);
        updatePayUI();
    }
}

function showConfirmModal() {
    const { order, cashGiven, change } = lastConfirmed;
    document.getElementById("confirmTitle").textContent = "✅ Đã xác nhận đơn " + order.invoice.invoiceNo;
    document.getElementById("confirmTotal").textContent = money(order.invoice.totalAmount);
    document.getElementById("confirmCash").textContent = money(cashGiven);
    document.getElementById("confirmChange").textContent = money(change);
        document.getElementById("confirmMethod").textContent = order.payments
        .filter(p => p.status === "SUCCESS" && p.paymentKind === "CHARGE")
        .map(p => p.methodName).join(" + ");
    document.getElementById("confirmCashRow").style.display = cashGiven > 0 ? "" : "none";
    document.getElementById("confirmChangeRow").style.display = cashGiven > 0 ? "" : "none";

    const rows = order.allocations.map(a => `
        <tr>
          <td>${esc(a.variantName)} ${weightText(a.weightG)} ${a.wholeBean ? "" : "xay"}</td>
          <td>${esc(a.lotCode)} ${a.nearExpiry ? '<span class="tag-near">Cận hạn</span>' : ""}</td>
          <td>${new Date(a.expiryDate).toLocaleDateString("vi-VN")}</td>
          <td class="num">${a.quantity}</td>
        </tr>`).join("");
    document.getElementById("confirmLots").innerHTML = `
        <table class="table table-sm">
          <tr><th>Gói</th><th>Lô</th><th>Hạn dùng</th><th class="num">SL</th></tr>
          ${rows}
        </table>`;

    const hasNear = order.allocations.some(a => a.nearExpiry);
    document.getElementById("confirmHint").textContent = hasNear
        ? "Có lô cận hạn: lấy đúng túi thuộc lô cận hạn giao cho khách."
        : "Lấy đúng túi thuộc các lô trên giao cho khách.";

    location.hash = "#modal-xac-nhan";
}

/* ---------- Hủy đơn chưa xác nhận ---------- */
function openCancelModal() {
    if (!currentOrder) return;
    document.getElementById("cancelTitle").textContent = "Hủy đơn " + currentOrder.invoice.invoiceNo + "?";
    document.getElementById("cancelNote").value = "";
    document.getElementById("cancelMsg").style.display = "none";
    location.hash = "#modal-huy";
}

async function cancelOrder() {
    if (!currentOrder) return;
    const reason = document.getElementById("cancelReason").value;
    const note = document.getElementById("cancelNote").value.trim();
    try {
        await postOrder({
            action: "cancel",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            reason: note ? reason + " - " + note : reason
        });
        saveOrder(null);
        location.hash = "#ban-hang";
        loadOverview();
    } catch (error) {
        const box = document.getElementById("cancelMsg");
        box.textContent = error.message;
        box.style.display = "block";
    }
}

/** Giá đã gồm VAT -> tách tiền chưa thuế + thuế theo invoice.vat_rate */
function vatHtml(total) {
    const rate = Number(storeInfo.vatRate || 0);
    if (!rate) return "";
    const beforeTax = Math.round(Number(total) / (1 + rate / 100));
    return `<div class="kv"><span>Tiền hàng chưa thuế</span><span>${money(beforeTax)}</span></div>
            <div class="kv"><span>Thuế GTGT ${rate}%</span><span>${money(Number(total) - beforeTax)}</span></div>`;
}

/* ---------- In hóa đơn ---------- */
function showBill() {
    if (!lastConfirmed) return;
    const { order, cashGiven, change } = lastConfirmed;
    const inv = order.invoice;

    document.getElementById("billStore").textContent = (storeInfo.storeName || "").toUpperCase();
    document.getElementById("billAddress").textContent = storeInfo.address || "";
        document.getElementById("billPhone").textContent = [
        storeInfo.phone ? "ĐT: " + storeInfo.phone : "",
        storeInfo.taxCode ? "MST: " + storeInfo.taxCode : ""
    ].filter(Boolean).join(" · ");
    document.getElementById("billNo").textContent = "Số: " + inv.invoiceNo;
    document.getElementById("billMeta").textContent =
        dateTimeText(new Date().toISOString()) + " · Thu ngân: " + (inv.cashierName || "");

    document.getElementById("billItems").innerHTML = order.lines.map(l => `
        <div class="bill-item">
          <div>${esc(l.variantName)} ${weightText(l.weightG)} ${l.wholeBean ? "nguyên hạt" : "xay sẵn"}</div>
          <div class="kv"><span>${l.quantity} x ${Number(l.unitPrice).toLocaleString("vi-VN")}</span>
            <span>${Number(l.lineAmount).toLocaleString("vi-VN")}</span></div>
        </div>`).join("");
            document.getElementById("billVat").innerHTML = vatHtml(inv.totalAmount);
    document.getElementById("billTotal").textContent = money(inv.totalAmount);

    // Các khoản đã thu; tiền mặt thì in số khách đưa + tiền thối
    document.getElementById("billPayments").innerHTML = order.payments
        .filter(p => p.status === "SUCCESS" && p.paymentKind === "CHARGE")
        .map(p => p.methodCode === "CASH" && cashGiven > 0
            ? `<div class="kv"><span>Tiền mặt</span><span>${money(cashGiven)}</span></div>
               <div class="kv"><span>Tiền thối</span><span>${money(change)}</span></div>`
            : `<div class="kv"><span>${esc(p.methodName)}</span><span>${money(p.amount)}</span></div>`)
        .join("");

    document.getElementById("billFooter").innerHTML =
        esc(storeInfo.footerNote || "Cảm ơn quý khách!") + "<br />Quét mã QR trên túi để xem thông tin cà phê.";

    location.hash = "#modal-bill";
}

async function printBill() {
    if (!lastConfirmed) return;
    try {
        await postOrder({ action: "printLog", invoiceId: lastConfirmed.order.invoice.salesInvoiceId });
    } catch (error) {
        console.error("Không ghi được nhật ký xuất hóa đơn:", error);
    }
    window.print();
}

/* ---------- Sự kiện ---------- */
function bindPayEvents() {
    const cashInput = document.getElementById("cashGiven");

    // Gõ tiền: tự thêm dấu chấm ngăn cách, tính lại tiền thối
    cashInput.addEventListener("input", () => {
        const value = parseMoney(cashInput.value);
        cashInput.value = value ? value.toLocaleString("vi-VN") : "";
        updatePayUI();
    });

    // Nút gợi ý tiền
    document.getElementById("quickCash").addEventListener("click", e => {
        const button = e.target.closest("button[data-cash]");
        if (!button) return;
        cashInput.value = Number(button.dataset.cash).toLocaleString("vi-VN");
        updatePayUI();
    });

    document.getElementById("btnConfirm").addEventListener("click", confirmOrder);
    document.getElementById("btnCancelOrder").addEventListener("click", openCancelModal);
    document.getElementById("btnDoCancel").addEventListener("click", cancelOrder);
    document.getElementById("btnShowBill").addEventListener("click", showBill);
    document.getElementById("btnDoPrint").addEventListener("click", printBill);
}

/* =====================================================================
   THANH TOÁN VNPAY (133 tạo QR, 160 theo dõi trạng thái)
   Khách trả đủ -> máy chủ tự xác nhận đơn (139, 140, 142); màn này hỏi trạng thái mỗi 3 giây.
   ===================================================================== */

let vnpPayment = null;       // {paymentId, payUrl, amount, txnRef, expireMinutes, mock}
let vnpPollTimer = null;
let vnpCountdownTimer = null;

async function openVnpay() {
    if (!currentOrder || currentOrder.lines.length === 0) return;
    try {
        vnpPayment = await postOrder({ action: "vnpayCreate", invoiceId: currentOrder.invoice.salesInvoiceId });
    } catch (error) {
        showOrderMessage(error.message);
        return;
    }
    showOrderMessage("");

    document.getElementById("vnpTitle").textContent = "Thanh toán VNPay · " + vnpPayment.invoiceNo;
    document.getElementById("vnpAmount").textContent = money(vnpPayment.amount);
    document.getElementById("vnpTxn").textContent = "Mã giao dịch: " + vnpPayment.txnRef;
    document.getElementById("vnpOpen").href = vnpPayment.payUrl;
    // Ảnh QR chứa link thanh toán (dịch vụ tạo QR miễn phí, cần mạng)
    document.getElementById("vnpQr").src = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data="
        + encodeURIComponent(vnpPayment.payUrl);
    document.getElementById("vnpHint").textContent = vnpPayment.mock
        ? "Đang dùng CỔNG GIẢ LẬP (chưa cấu hình mã VNPay). Bấm \"Mở trang thanh toán\" rồi chọn Thành công / Thất bại."
        : "Khách trả đủ tiền: đơn tự xác nhận và trừ kho. Sandbox: trả bằng thẻ test NCB.";
    setVnpStatus("⏳ Đang chờ khách thanh toán…", "");

    location.hash = "#modal-vnpay";
    startVnpayPolling();
}

function setVnpStatus(text, cssClass) {
    const box = document.getElementById("vnpStatus");
    box.textContent = text;
    box.className = "wait " + cssClass;
}

function startVnpayPolling() {
    stopVnpayPolling();
    let secondsLeft = vnpPayment.expireMinutes * 60;

    vnpCountdownTimer = setInterval(() => {
        secondsLeft--;
        const m = Math.floor(secondsLeft / 60), s = String(secondsLeft % 60).padStart(2, "0");
        if (secondsLeft <= 0) {
            stopVnpayPolling();
            setVnpStatus("⌛ QR đã hết hạn, bấm Tạo lại QR", "wait-fail");
            return;
        }
        if (document.getElementById("vnpStatus").className === "wait ") {
            document.getElementById("vnpStatus").textContent = "⏳ Đang chờ khách thanh toán… · hết hạn sau " + m + ":" + s;
        }
    }, 1000);

    vnpPollTimer = setInterval(checkVnpayStatus, 3000);
}

function stopVnpayPolling() {
    clearInterval(vnpPollTimer);
    clearInterval(vnpCountdownTimer);
    vnpPollTimer = null;
    vnpCountdownTimer = null;
}

async function checkVnpayStatus() {
    if (!vnpPayment) return;
    let status;
    try {
        status = await getJson(API_ORDER + "?action=paymentStatus&paymentId=" + vnpPayment.paymentId);
    } catch (error) {
        return;                                    // lỗi mạng tạm thời: lần sau hỏi lại
    }

    if (status.invoiceStatus === "PAID") {
        // Đã thu đủ và hệ thống đã xác nhận đơn
        stopVnpayPolling();
        const order = await getJson(API_ORDER + "?id=" + status.salesInvoiceId);
        lastConfirmed = { order: order, cashGiven: 0, change: 0 };
        saveOrder(null);
        showConfirmModal();
        loadProducts();
        loadOverview();
    } else if (status.paymentStatus === "SUCCESS") {
        // Đã nhận tiền nhưng chưa xác nhận được (VD hết tồn): giữ đơn để nhân viên xử lý
        stopVnpayPolling();
        setVnpStatus("✅ Đã nhận tiền VNPay nhưng chưa xác nhận được đơn. Kiểm tra số lượng rồi bấm Xác nhận đơn.", "wait-fail");
        saveOrder(await getJson(API_ORDER + "?id=" + status.salesInvoiceId));
    } else if (status.paymentStatus === "FAILED") {
        stopVnpayPolling();
        setVnpStatus("❌ Thanh toán không thành công (mã " + (status.responseCode || "?") + "). Tạo lại QR hoặc thu tiền mặt.", "wait-fail");
    } else if (status.paymentStatus === "CANCELLED") {
        stopVnpayPolling();
        setVnpStatus("QR này đã bị hủy (đã tạo QR mới hoặc đơn bị hủy).", "wait-fail");
    }
}

function bindVnpayEvents() {
    document.getElementById("btnVnpay").addEventListener("click", openVnpay);
    document.getElementById("btnVnpRenew").addEventListener("click", openVnpay);
    // Đóng hộp thoại QR -> ngừng hỏi trạng thái (giao dịch vẫn chờ, mở lại sẽ tạo QR mới)
    window.addEventListener("hashchange", () => {
        if (location.hash !== "#modal-vnpay") stopVnpayPolling();
    });
}

/* =====================================================================
   MỞ LẠI ĐƠN CHỜ THANH TOÁN
   Đơn để chờ (bấm "+ Đơn mới") vẫn nằm ở DB với trạng thái PENDING.
   Mở lại -> đơn thành đơn đang lập: thêm/bớt gói, thu tiền mặt / VNPay, xác nhận, hủy như bình thường.
   Danh sách lấy từ API trang hóa đơn: ../../api/hoa-don?action=list&status=PENDING
   ===================================================================== */

const API_PENDING = "../../api/hoa-don";
let pendingPage = 1;

async function openPendingList() {
    pendingPage = 1;
    document.getElementById("pendingSearch").value = "";
    location.hash = "#modal-don-cho";
    await loadPendingList(false);
    document.getElementById("pendingSearch").focus();
}

// append = true: bấm "Xem thêm" (nối thêm trang sau)
async function loadPendingList(append) {
    const params = new URLSearchParams({
        action: "list",
        status: "PENDING",
        keyword: document.getElementById("pendingSearch").value.trim(),
        page: pendingPage
    });
    const list = document.getElementById("pendingList");
    let data;
    try {
        data = await getJson(API_PENDING + "?" + params);
    } catch (error) {
        document.getElementById("pendingInfo").textContent = "Lỗi: " + error.message;
        return;
    }

    const currentId = currentOrder ? currentOrder.invoice.salesInvoiceId : null;
    const html = data.rows.map(r => `
        <div class="pending-item ${r.salesInvoiceId === currentId ? "pending-current" : ""}" data-id="${r.salesInvoiceId}">
          <div>
            <b>${esc(r.invoiceNo)}</b>
            <div class="sub">${dateTimeText(r.createdAt)} · ${esc(r.cashierName)}</div>
          </div>
          <div class="pending-right">
            <b>${money(r.totalAmount)}</b>
            ${r.salesInvoiceId === currentId
                ? '<span class="sub">Đang mở</span>'
                : '<button class="btn btn-sm btn-primary">Mở đơn</button>'}
          </div>
        </div>`).join("");
    list.innerHTML = append ? list.innerHTML + html : html;

    if (!append && data.rows.length === 0) {
        list.innerHTML = '<div class="order-empty">Không có đơn chờ thanh toán.</div>';
    }
    document.getElementById("pendingInfo").textContent = data.total + " đơn đang chờ · mới nhất trước";
    if (!document.getElementById("pendingSearch").value.trim()) {
        document.getElementById("pendingBadge").textContent = data.total;
    }
    document.getElementById("btnPendingMore").style.display =
        pendingPage * data.pageSize < data.total ? "" : "none";
}

// Mở 1 đơn chờ thành đơn đang lập
async function openPendingOrder(id) {
    try {
        const order = await getJson(API_ORDER + "?id=" + id);
        if (order.invoice.status !== "PENDING") {
            alert("Đơn " + order.invoice.invoiceNo + " đã được xác nhận hoặc hủy ở máy khác.");
            loadPendingList(false);
            return;
        }
        stopVnpayPolling();                                   // QR của đơn cũ (nếu đang mở) ngừng theo dõi
        document.getElementById("cashGiven").value = "";
        saveOrder(order);
        showOrderMessage("");
        location.hash = "#ban-hang";
    } catch (error) {
        alert("Không mở được đơn: " + error.message);
    }
}

function bindPendingEvents() {
    document.getElementById("btnPendingList").addEventListener("click", openPendingList);

    document.getElementById("pendingList").addEventListener("click", e => {
        const item = e.target.closest(".pending-item");
        if (item && !item.classList.contains("pending-current")) openPendingOrder(Number(item.dataset.id));
    });

    // Gõ tìm: đợi 300ms sau phím cuối mới gọi API
    let timer = null;
    document.getElementById("pendingSearch").addEventListener("input", () => {
        clearTimeout(timer);
        timer = setTimeout(() => { pendingPage = 1; loadPendingList(false); }, 300);
    });

    document.getElementById("btnPendingMore").addEventListener("click", () => {
        pendingPage++;
        loadPendingList(true);
    });
}

/* =====================================================================
   QUÉT MÃ LÔ: máy quét USB / camera
   Máy quét USB gõ cả mã rất nhanh rồi tự nhấn Enter -> đo tốc độ gõ để biết là quét hay gõ tay.
   Camera đọc QR rồi đưa mã vào đúng hàm xử lý như máy quét (scanned = true).
   Quét = có túi thật -> được bán vượt tồn (chủ duyệt sau). Gõ tay = không được vượt tồn.
   ===================================================================== */

const SCAN_MAX_MS_PER_CHAR = 35;      // máy quét: < 35ms / ký tự; người gõ thường > 80ms
let scanKeyTimes = [];

function handleLotCode(code, scanned) {
    code = (code || "").trim();
    if (!code) return;
    addToOrder({ lotCode: code, scanned: scanned });
}

function bindScanEvents() {
    const input = document.getElementById("scanInput");

    input.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            const times = scanKeyTimes;
            const avg = times.length > 1 ? (times[times.length - 1] - times[0]) / (times.length - 1) : 999;
            const scanned = input.value.trim().length >= 4 && avg < SCAN_MAX_MS_PER_CHAR;
            handleLotCode(input.value, scanned);
            input.value = "";
            scanKeyTimes = [];
            return;
        }
        if (e.key.length === 1) scanKeyTimes.push(performance.now());
    });
    input.addEventListener("input", () => {
        if (!input.value) scanKeyTimes = [];
    });

    // Máy quét gõ khi chưa bấm vào ô nào -> chuyển vào ô quét
    document.addEventListener("keydown", e => {
        if (e.key.length !== 1 || e.ctrlKey || e.altKey || e.metaKey) return;
        if (e.target.closest("input, select, textarea") || location.hash.startsWith("#modal")) return;
        input.focus();
        scanKeyTimes.push(performance.now());
    });

    document.getElementById("btnCamera").addEventListener("click", openCamera);
    window.addEventListener("hashchange", () => {
        if (location.hash !== "#modal-camera") stopCamera();
    });
}

/* ---------- Camera ---------- */
let camStream = null;
let camTimer = null;
let camDetector = null;          // BarcodeDetector (Chrome / Edge hỗ trợ sẵn) hoặc null -> dùng jsQR

const JSQR_URL = "https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js";

function setCamStatus(text, error) {
    const box = document.getElementById("camStatus");
    box.textContent = text;
    box.classList.toggle("wait-fail", !!error);
}

async function openCamera() {
    location.hash = "#modal-camera";
    if (!window.isSecureContext || !navigator.mediaDevices) {
        setCamStatus("Trình duyệt chỉ cho mở camera trên localhost hoặc https.", true);
        return;
    }
    setCamStatus("Đang mở camera…");
    try {
        if ("BarcodeDetector" in window) {
            const formats = await BarcodeDetector.getSupportedFormats();
            camDetector = formats.includes("qr_code")
                ? new BarcodeDetector({ formats: formats.filter(f => ["qr_code", "code_128"].includes(f)) })
                : null;
        }
        if (!camDetector && !window.jsQR) await loadScript(JSQR_URL);

        camStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
        const video = document.getElementById("camVideo");
        video.srcObject = camStream;
        await video.play();
        setCamStatus("Đưa mã QR trên nhãn lô vào khung hình");
        camTimer = setInterval(scanFrame, 250);
    } catch (error) {
        setCamStatus("Không mở được camera: " + error.message, true);
        stopCamera();
    }
}

async function scanFrame() {
    const video = document.getElementById("camVideo");
    if (!camStream || video.readyState < 2) return;
    let code = null;
    try {
        if (camDetector) {
            const found = await camDetector.detect(video);
            if (found.length) code = found[0].rawValue;
        } else if (window.jsQR) {
            const canvas = document.getElementById("camCanvas");
            canvas.width = video.videoWidth;
            canvas.height = video.videoHeight;
            const ctx = canvas.getContext("2d", { willReadFrequently: true });
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const found = window.jsQR(image.data, image.width, image.height);
            if (found) code = found.data;
        }
    } catch (error) {
        return;                                   // khung hình lỗi: bỏ qua, đọc khung sau
    }
    if (code) {
        stopCamera();
        location.hash = "#ban-hang";
        handleLotCode(code, true);                // giống máy quét
    }
}

function stopCamera() {
    clearInterval(camTimer);
    camTimer = null;
    if (camStream) camStream.getTracks().forEach(t => t.stop());
    camStream = null;
}

function loadScript(url) {
    return new Promise((resolve, reject) => {
        const s = document.createElement("script");
        s.src = url;
        s.onload = resolve;
        s.onerror = () => reject(new Error("không tải được thư viện đọc QR (cần mạng)"));
        document.head.appendChild(s);
    });
}

/* ---------- Khởi động ---------- */
document.addEventListener("DOMContentLoaded", async () => {
    bindFilters();
    bindOrderEvents();
    bindPayEvents();
        // Đến từ trang cấu hình (?open=id): mở đơn chờ đó
    const openId = new URLSearchParams(location.search).get("open");
    loadCurrentOrder().then(() => {
        if (openId) {
            history.replaceState(null, "", location.pathname);
            openPendingOrder(Number(openId));
        }
    });
    loadOverview();
    bindVnpayEvents();
        bindPendingEvents();
            bindScanEvents();
    try {
        await loadFilterOptions();
    } catch (error) {
        console.error("Không tải được bộ lọc:", error);
    }
    loadProducts();
});
