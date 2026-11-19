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

    const nearTag = p.nearExpiry && p.canSell
        ? ' <span class="tag-near" title="Có lô sắp hết hạn, hệ thống sẽ trừ lô này trước">Lô cận hạn</span>'
        : "";

    return `
          <div class="${rowClass}">
            <div class="pkg-name">${weightText(p.weightG)} · ${form}${nearTag}
              <div class="pkg-sku" title="${esc(p.grindName)}">${esc(p.sku)}</div>
            </div>
            <div class="pkg-price">${p.salePrice === null ? "—" : money(p.salePrice)}</div>
            <div class="pkg-stock">${stockText}</div>
            <button class="pkg-add" title="Thêm vào đơn" data-package-id="${p.packageId}"
                    ${p.canSell ? "" : "disabled"}>+</button>
          </div>`;
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
        addToOrder(button.dataset.packageId);
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
async function addToOrder(packageId) {
    try {
        if (!currentOrder) {
            saveOrder(await postOrder({ action: "create" }));
        }
        saveOrder(await postOrder({
            action: "addPackage",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            packageId: packageId,
            quantity: 1
        }));
        showOrderMessage("");
    } catch (error) {
        showOrderMessage(error.message);
    }
}

async function changeQuantity(lineId, quantity) {
    try {
        saveOrder(await postOrder({
            action: "updateQty",
            invoiceId: currentOrder.invoice.salesInvoiceId,
            lineId: lineId,
            quantity: quantity
        }));
        showOrderMessage("");
    } catch (error) {
        showOrderMessage(error.message);
        renderOrder();                       // trả ô số lượng về giá trị cũ
    }
}

async function removeLine(lineId) {
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
function showOrderMessage(text) {
    const box = document.getElementById("orderMsg");
    box.textContent = text;
    box.style.display = text ? "block" : "none";
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
        document.getElementById("orderMeta").textContent = "Bấm + ở gói cà phê để bắt đầu";
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
    const overStock = l.quantity > l.maxQty;       // tồn giảm sau khi thêm vào đơn
    return `
      <div class="line">
        <div class="line-top">
          <div>
            <div class="line-name">${esc(l.variantName)}</div>
            <div class="sub">${weightText(l.weightG)} · ${form} · ${money(l.unitPrice)}</div>
          </div>
          <button class="icon-btn" title="Bỏ khỏi đơn" data-action="remove" data-line-id="${l.salesLineId}">✕</button>
        </div>
        <div class="line-bottom">
          <div class="qty">
            <button data-action="minus" data-line-id="${l.salesLineId}" ${l.quantity <= 1 ? "disabled" : ""}>−</button>
            <input class="qty-input" type="number" min="1" max="${l.maxQty}" value="${l.quantity}"
                   data-line-id="${l.salesLineId}" />
            <button data-action="plus" data-line-id="${l.salesLineId}" ${l.quantity >= l.maxQty ? "disabled" : ""}>+</button>
          </div>
          <span class="sub ${overStock ? "text-warn" : ""}">tối đa ${l.maxQty}</span>
          <span class="line-amount">${money(l.lineAmount)}</span>
        </div>
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

    // Nút −, +, ✕
    linesBox.addEventListener("click", e => {
        const button = e.target.closest("button[data-action]");
        if (!button || button.disabled || !currentOrder) return;
        const lineId = Number(button.dataset.lineId);
        const line = currentOrder.lines.find(l => l.salesLineId === lineId);
        if (!line) return;

        if (button.dataset.action === "minus") changeQuantity(lineId, line.quantity - 1);
        if (button.dataset.action === "plus") changeQuantity(lineId, line.quantity + 1);
        if (button.dataset.action === "remove") removeLine(lineId);
    });

    // Gõ số lượng trực tiếp rồi Enter / rời ô
    linesBox.addEventListener("change", e => {
        if (!e.target.classList.contains("qty-input")) return;
        const quantity = parseInt(e.target.value, 10);
        if (!quantity || quantity < 1) { renderOrder(); return; }
        changeQuantity(Number(e.target.dataset.lineId), quantity);
    });

    // Đơn mới: để đơn hiện tại ở trạng thái chờ (xem lại ở tab Hóa đơn), bắt đầu đơn trống
    document.getElementById("btnNewOrder").addEventListener("click", () => {
        saveOrder(null);
        showOrderMessage("");
    });
}

/* ---------- Khởi động ---------- */
document.addEventListener("DOMContentLoaded", async () => {
    bindFilters();
    bindOrderEvents();
    loadCurrentOrder();
    try {
        await loadFilterOptions();
    } catch (error) {
        console.error("Không tải được bộ lọc:", error);
    }
    loadProducts();
});
