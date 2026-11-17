/* =====================================================================
   TRANG BÁN HÀNG (longdtta00178) — Bước 1: tải thẻ cà phê + gói bán từ DB
   API: ../api/ban-hang/san-pham   (SalesProductController)
   ===================================================================== */

const API_PRODUCTS = "../api/ban-hang/san-pham";

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

    // Nút + : bước sau (thêm vào đơn - UC 132). Tạm thời chỉ báo mã gói.
    document.getElementById("productGrid").addEventListener("click", e => {
        const button = e.target.closest(".pkg-add");
        if (!button || button.disabled) return;
        console.log("Thêm gói vào đơn, package_id =", button.dataset.packageId);
    });
}

/* ---------- Khởi động ---------- */
document.addEventListener("DOMContentLoaded", async () => {
    bindFilters();
    try {
        await loadFilterOptions();
    } catch (error) {
        console.error("Không tải được bộ lọc:", error);
    }
    loadProducts();
});
