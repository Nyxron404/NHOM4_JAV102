// =====================================================================
// Danh mục phân loại & Nhà cung cấp - Lê Nguyễn Minh Hiếu (hieulnmth08470)
// Trang: pages/hieulnmth08470.html   |   API: Servlet /api/danh-muc/<ten-danh-muc> (trả JSON)
//
// Cấu trúc file:
//   1. Tiện ích (gọi API, chống chèn HTML, thông báo)
//   2. CATALOGS: khai báo từng danh mục (ô nhập, cột bảng, bộ lọc)
//   3. Màn hình danh sách + form thêm/sửa/xóa (dùng chung cho mọi danh mục)
//   4. Màn hình gán độ xay cho phương pháp pha
//   5. Điều hướng theo # trên địa chỉ
// =====================================================================

const API = "../../api/danh-muc/";

// ===================================================== 1. TIỆN ÍCH

const $ = (selector) => document.querySelector(selector);

/** Chống chèn HTML: mọi dữ liệu từ CSDL đều đi qua hàm này trước khi đưa vào innerHTML. */
function esc(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/**
 * Gọi Servlet. GET: params nằm trên URL. POST: gửi dạng form (Servlet đọc bằng req.getParameter).
 * Trả về { status, body } - body là JSON Servlet trả về.
 */
async function api(name, params = {}, method = "GET") {
    const query = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) {
        if (v !== null && v !== undefined && v !== "") query.append(k, v);
    }
    let url = API + name;
    const options = { method, headers: {} };
    if (method === "GET") {
        if ([...query].length) url += "?" + query;
    } else {
        options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
        options.body = query;
    }
    try {
        const res = await fetch(url, options);
        const body = await res.json().catch(() => ({ ok: false, message: "Máy chủ trả về dữ liệu không hợp lệ" }));
        return { status: res.status, body };
    } catch (e) {
        return { status: 0, body: { ok: false, message: "Không kết nối được máy chủ" } };
    }
}

/** Gửi nguyên 1 thẻ <form> (kể cả ô ẩn) lên Servlet. */
function formParams(form, extra = {}) {
    const params = {};
    for (const [k, v] of new FormData(form)) params[k] = v;
    return Object.assign(params, extra);
}

let flash = null;   // thông báo chờ hiện sau khi đổi màn hình

function showAlert(type, message) {
    const box = $("#alert");
    box.className = "hl-alert " + (type === "success" ? "hl-alert-ok" : "hl-alert-err");
    box.textContent = message;
    box.hidden = false;
    box.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function hideAlert() {
    $("#alert").hidden = true;
}

/** Danh sách lựa chọn cho thẻ <select>: [{value, text}] */
function optionsHtml(options, selected, placeholder) {
    let html = placeholder !== undefined ? `<option value="">${esc(placeholder)}</option>` : "";
    for (const o of options) {
        const sel = String(o.value) === String(selected ?? "") ? " selected" : "";
        html += `<option value="${esc(o.value)}"${sel}>${esc(o.text)}</option>`;
    }
    return html;
}

const muted = (text) => `<span class="hl-muted">${esc(text)}</span>`;
const dash = (value) => (value === null || value === undefined || value === "") ? muted("—") : esc(value);
const nameWithNote = (name, note) =>
    `<strong>${esc(name)}</strong>` + (note ? `<div class="hl-muted hl-clip">${esc(note)}</div>` : "");

// ===================================================== 2. KHAI BÁO DANH MỤC
//  fields : ô nhập trong form   (name trùng tên tham số Servlet đọc trong bind())
//  columns: cột bảng            (html(item, ctx) trả về nội dung ô; ctx = {data, index, used})
//  filters: ô lọc cạnh ô tìm kiếm

const provinceOptions = (data) => (data.provinces || []).map(p => ({ value: p.provinceId, text: p.provinceName }));
const speciesOptions = (data) => (data.speciesList || []).map(s => ({ value: s.speciesId, text: s.speciesName }));
const STATUS_OPTIONS = [{ value: "ACTIVE", text: "Đang hợp tác" }, { value: "INACTIVE", text: "Ngừng hợp tác" }];
const descriptionField = { name: "description", label: "Mô tả", type: "textarea", span2: true };

const CATALOGS = {
    "vung-trong": {
        title: "Vùng trồng",
        sub: "Nơi trồng cà phê (Cầu Đất, Khe Sanh...) - dùng khi khai báo biến thể sản phẩm.",
        label: "vùng trồng", idKey: "regionId", nameKey: "regionName",
        search: "Tìm tên vùng trồng (gõ không dấu cũng được)",
        filters: [{ name: "provinceId", all: "Tất cả tỉnh/thành", options: provinceOptions }],
        fields: [
            { name: "provinceId", label: "Tỉnh/thành", type: "select", required: true, options: provinceOptions, placeholder: "-- Chọn tỉnh/thành --" },
            { name: "regionName", label: "Tên vùng trồng", required: true, max: 200, placeholder: "VD: Cầu Đất" },
            { name: "altitudeMinM", label: "Độ cao thấp nhất (m)", type: "number", min: 0, max: 9000, placeholder: "VD: 1500" },
            { name: "altitudeMaxM", label: "Độ cao cao nhất (m)", type: "number", min: 0, max: 9000, placeholder: "VD: 1650" },
            { name: "soil", label: "Thổ nhưỡng", type: "textarea", rows: 2, placeholder: "VD: Đất đỏ bazan" },
            { name: "climate", label: "Khí hậu", type: "textarea", rows: 2, placeholder: "VD: Mát mẻ quanh năm, 18-22°C" },
            descriptionField,
        ],
        columns: [
            { title: "#", cls: "hl-num", html: (r, c) => c.index + 1 },
            { title: "Vùng trồng", html: r => nameWithNote(r.regionName, r.description) },
            { title: "Tỉnh/thành", html: (r, c) => esc((c.data.provinces || []).find(p => p.provinceId === r.provinceId)?.provinceName) },
            {
                title: "Độ cao (m)", html: r => {
                    const lo = r.altitudeMinM, hi = r.altitudeMaxM;
                    if (lo != null && hi != null) return `${lo} – ${hi}`;
                    if (lo != null) return `từ ${lo}`;
                    if (hi != null) return `đến ${hi}`;
                    return muted("—");
                }
            },
            { title: "Thổ nhưỡng / Khí hậu", cls: "hl-muted", html: r => [r.soil, r.climate].filter(Boolean).map(esc).join(" · ") },
            { title: "Biến thể", cls: "hl-num", html: (r, c) => c.used },
        ],
        blocked: "Đang được dùng bởi biến thể sản phẩm, không thể xóa",
    },

    "loai-ca-phe": {
        title: "Loài cà phê",
        sub: "Arabica, Robusta, Liberica, Excelsa... Mỗi loài gồm nhiều giống.",
        label: "loài cà phê", idKey: "speciesId", nameKey: "speciesName",
        search: "Tìm tên loài",
        fields: [
            { name: "speciesName", label: "Tên loài", required: true, max: 200, placeholder: "VD: Arabica" },
            descriptionField,
        ],
        columns: [
            { title: "#", cls: "hl-num", html: (r, c) => c.index + 1 },
            { title: "Tên loài", html: r => `<strong>${esc(r.speciesName)}</strong>` },
            { title: "Mô tả", cls: "hl-muted hl-clip", html: r => esc(r.description) },
            {
                title: "Số giống", cls: "hl-num",
                html: (r, c) => c.used > 0 ? `<a class="hl-link" href="#giong-ca-phe?speciesId=${r.speciesId}">${c.used}</a>` : "0"
            },
        ],
        blocked: "Còn giống thuộc loài này, không thể xóa",
    },

    "giong-ca-phe": {
        title: "Giống cà phê",
        sub: "Typica, Bourbon, Catimor, TR4... Mỗi giống thuộc một loài.",
        label: "giống cà phê", idKey: "varietyId", nameKey: "varietyName",
        search: "Tìm tên giống",
        filters: [{ name: "speciesId", all: "Tất cả loài", options: speciesOptions }],
        fields: [
            { name: "speciesId", label: "Loài", type: "select", required: true, options: speciesOptions, placeholder: "-- Chọn loài --", fromFilter: true },
            { name: "varietyName", label: "Tên giống", required: true, max: 200, placeholder: "VD: Bourbon" },
            descriptionField,
        ],
        columns: [
            { title: "#", cls: "hl-num", html: (r, c) => c.index + 1 },
            { title: "Tên giống", html: r => `<strong>${esc(r.varietyName)}</strong>` },
            { title: "Loài", html: (r, c) => `<span class="hl-badge">${esc((c.data.speciesList || []).find(s => s.speciesId === r.speciesId)?.speciesName)}</span>` },
            { title: "Mô tả", cls: "hl-muted hl-clip", html: r => esc(r.description) },
            { title: "Biến thể", cls: "hl-num", html: (r, c) => c.used },
        ],
        blocked: "Đang được dùng bởi biến thể sản phẩm, không thể xóa",
    },

    "so-che": {
        title: "Phương pháp sơ chế",
        sub: "Washed, Natural, Honey, Anaerobic...",
        label: "phương pháp sơ chế", idKey: "processId", nameKey: "processName",
        search: "Tìm tên phương pháp",
        fields: [
            { name: "processName", label: "Tên phương pháp", required: true, max: 200, placeholder: "VD: Honey" },
            descriptionField,
        ],
        columns: [
            { title: "#", cls: "hl-num", html: (r, c) => c.index + 1 },
            { title: "Tên phương pháp", html: r => `<strong>${esc(r.processName)}</strong>` },
            { title: "Mô tả", cls: "hl-muted hl-clip", html: r => esc(r.description) },
            { title: "Biến thể", cls: "hl-num", html: (r, c) => c.used },
        ],
        blocked: "Đang được dùng bởi biến thể sản phẩm, không thể xóa",
    },

    "muc-rang": {
        title: "Mức rang",
        sub: "Sắp theo thứ tự từ rang nhạt đến rang đậm. Thứ tự không được trùng nhau.",
        label: "mức rang", idKey: "roastLevelId", nameKey: "roastName",
        search: "Tìm tên mức rang",
        fields: [
            { name: "roastName", label: "Tên mức rang", required: true, max: 200, placeholder: "VD: Rang vừa (Medium)" },
            { name: "sortOrder", label: "Thứ tự (1 = nhạt nhất)", type: "number", min: 1, max: 32767, hint: "Để trống sẽ tự xếp cuối danh sách." },
            descriptionField,
        ],
        columns: [
            { title: "Thứ tự", cls: "hl-num", html: r => `<span class="hl-order">${esc(r.sortOrder)}</span>` },
            { title: "Tên mức rang", html: r => `<strong>${esc(r.roastName)}</strong>` },
            { title: "Mô tả", cls: "hl-muted hl-clip", html: r => esc(r.description) },
            { title: "Biến thể", cls: "hl-num", html: (r, c) => c.used },
        ],
        blocked: "Đang được dùng bởi biến thể sản phẩm, không thể xóa",
    },

    "phuong-phap-pha": {
        title: "Phương pháp pha",
        sub: "Phin, Pour-over V60, Espresso, French Press... kèm thông số pha và độ xay phù hợp.",
        label: "phương pháp pha", idKey: "brewMethodId", nameKey: "methodName",
        search: "Tìm tên phương pháp pha",
        deleteNote: " Các độ xay đã gán cũng bị bỏ.",
        fields: [
            { name: "methodName", label: "Tên phương pháp", required: true, max: 200, placeholder: "VD: Pour-over V60", span2: true },
            { name: "coffeeWaterRatio", label: "Tỉ lệ cà phê : nước", max: 20, placeholder: "VD: 1:15" },
            { name: "waterTempC", label: "Nhiệt độ nước (°C)", max: 20, placeholder: "VD: 92-96" },
            { name: "brewTime", label: "Thời gian pha", max: 30, placeholder: "VD: 2:30 - 3:00 phút" },
            { name: "guide", label: "Hướng dẫn pha", type: "textarea", rows: 4, span2: true, placeholder: "Các bước pha..." },
        ],
        columns: [
            { title: "#", cls: "hl-num", html: (r, c) => c.index + 1 },
            { title: "Phương pháp", html: r => nameWithNote(r.methodName, r.guide) },
            { title: "Tỉ lệ", html: r => dash(r.coffeeWaterRatio) },
            { title: "Nhiệt độ", html: r => dash(r.waterTempC) },
            { title: "Thời gian", html: r => dash(r.brewTime) },
            {
                title: `Độ xay phù hợp <span class="hl-muted">(★ khuyến nghị)</span>`, raw: true,
                html: (r, c) => {
                    const names = (c.data.grindNames || {})[r.brewMethodId] || [];
                    if (!names.length) return muted("Chưa gán");
                    return names.map(n => `<span class="hl-badge ${n.startsWith("★") ? "hl-badge-rec" : ""}">${esc(n)}</span>`).join("");
                }
            },
            { title: "Biến thể", cls: "hl-num", html: (r, c) => c.used },
        ],
        extraActions: r => `<a class="hl-btn hl-btn-sm" href="#phuong-phap-pha/gan-do-xay?id=${r.brewMethodId}">Gán độ xay</a>`,
        blocked: "Đang được gợi ý cho biến thể sản phẩm, không thể xóa",
    },

    "do-xay": {
        title: "Độ xay",
        sub: "Dạng sản phẩm khi bán: Nguyên hạt, xay thô, xay vừa, xay mịn... Sắp từ thô đến mịn.",
        label: "độ xay", idKey: "grindSizeId", nameKey: "grindName",
        search: "Tìm theo tên hoặc mã",
        deleteNote: " Phần gán cho phương pháp pha cũng bị bỏ.",
        fields: [
            { name: "grindCode", label: "Mã", required: true, max: 40, upper: true, placeholder: "VD: MEDIUM_FINE", hint: "Chữ IN HOA không dấu, số, _ - ." },
            { name: "grindName", label: "Tên độ xay", required: true, max: 200, placeholder: "VD: Xay vừa mịn" },
            { name: "sortOrder", label: "Thứ tự (1 = thô nhất)", type: "number", min: 1, max: 32767, hint: "Để trống sẽ tự xếp cuối danh sách." },
            { name: "isWholeBean", label: "Đây là dạng Nguyên hạt (không xay)", type: "checkbox" },
            descriptionField,
        ],
        columns: [
            { title: "Thứ tự", cls: "hl-num", html: r => `<span class="hl-order">${esc(r.sortOrder)}</span>` },
            { title: "Mã", html: r => `<span class="hl-code">${esc(r.grindCode)}</span>` },
            { title: "Tên độ xay", html: r => `<strong>${esc(r.grindName)}</strong>` + (r.isWholeBean ? ` <span class="hl-badge hl-badge-rec">Nguyên hạt</span>` : "") },
            { title: "Mô tả", cls: "hl-muted hl-clip", html: r => esc(r.description) },
            { title: "Gói bán", cls: "hl-num", html: (r, c) => c.used },
        ],
        blocked: "Đang được dùng bởi gói bán, không thể xóa",
    },

    "nha-cung-cap": {
        title: "Nhà cung cấp",
        sub: "NCC đã có hóa đơn nhập không xóa được (giữ lịch sử) - dùng “Ngừng hợp tác”.",
        label: "nhà cung cấp", idKey: "supplierId", nameKey: "supplierName",
        search: "Tìm tên, mã, MST, số điện thoại",
        filters: [{ name: "status", all: "Tất cả trạng thái", options: () => STATUS_OPTIONS }],
        fields: [
            { name: "supplierCode", label: "Mã NCC", required: true, max: 40, upper: true, placeholder: "VD: NCC_CAUDAT", hint: "Chữ IN HOA không dấu, số, _ - ." },
            { name: "supplierName", label: "Tên nhà cung cấp", required: true, max: 200, placeholder: "VD: Công ty TNHH Cà phê Cầu Đất" },
            { name: "taxCode", label: "Mã số thuế", max: 14, placeholder: "0312345678 hoặc 0312345678-001" },
            { name: "phone", label: "Số điện thoại", max: 20, placeholder: "VD: 0912345678" },
            { name: "email", label: "Email", type: "email", max: 255, placeholder: "VD: lienhe@ncc.vn" },
            { name: "status", label: "Trạng thái", type: "select", options: () => STATUS_OPTIONS, default: "ACTIVE" },
            { name: "address", label: "Địa chỉ", max: 255, span2: true },
        ],
        columns: [
            { title: "Mã", html: r => `<span class="hl-code">${esc(r.supplierCode)}</span>` },
            { title: "Nhà cung cấp", html: r => nameWithNote(r.supplierName, r.address) },
            { title: "MST", html: r => dash(r.taxCode) },
            {
                title: "Liên hệ", html: r => (r.phone || r.email)
                    ? (r.phone ? `<div>${esc(r.phone)}</div>` : "") + (r.email ? `<div class="hl-muted">${esc(r.email)}</div>` : "")
                    : muted("—")
            },
            {
                title: "Trạng thái", html: r => r.status === "ACTIVE"
                    ? `<span class="hl-status on">Đang hợp tác</span>` : `<span class="hl-status off">Ngừng hợp tác</span>`
            },
            { title: "Hóa đơn nhập", cls: "hl-num", html: (r, c) => c.used },
        ],
        rowClass: r => r.status === "INACTIVE" ? "is-inactive" : "",
        extraActions: r => `<button type="button" class="hl-btn hl-btn-sm" data-act="status" data-id="${r.supplierId}"
                data-status="${r.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"}">${r.status === "ACTIVE" ? "Ngừng hợp tác" : "Hợp tác lại"}</button>`,
        blocked: "Đã có hóa đơn nhập, hãy dùng Ngừng hợp tác",
    },
};

// ===================================================== 3. DANH SÁCH + FORM THÊM/SỬA/XÓA

const state = {
    key: null,        // danh mục đang mở, VD "vung-trong"
    filters: {},      // {q, provinceId, ...} lấy từ địa chỉ #vung-trong?q=...
    data: {},         // JSON danh sách vừa tải
    editingId: null,  // id đang sửa (null = đang thêm mới / form đóng)
};

async function openList(key, filters) {
    const cat = CATALOGS[key];
    if (state.key !== key) closeForm();
    state.key = key;
    state.filters = filters;

    $("#list-title").textContent = cat.title;
    $("#list-sub").textContent = cat.sub;
    $("#btn-add").textContent = "+ Thêm " + cat.label;
    $("#search-q").placeholder = cat.search;
    $("#search-q").value = filters.q || "";
    $("#clear-filter").hidden = !Object.values(filters).some(Boolean);
    $("#list-head").innerHTML = "<tr>" + cat.columns.map(c =>
        `<th class="${c.cls && c.cls.includes("hl-num") ? "hl-num" : ""}">${c.title}</th>`).join("")
        + `<th class="hl-act">Thao tác</th></tr>`;

    await loadList();
}

async function loadList() {
    const cat = CATALOGS[state.key];
    const body = $("#list-body");
    body.innerHTML = `<tr><td colspan="${cat.columns.length + 1}" class="hl-empty">Đang tải...</td></tr>`;

    const { status, body: data } = await api(state.key, state.filters);
    if (status !== 200) {
        body.innerHTML = `<tr><td colspan="${cat.columns.length + 1}" class="hl-empty">${esc(data.message)}</td></tr>`;
        showAlert("error", data.message);
        return;
    }
    state.data = data;

    // Ô lọc (tỉnh, loài, trạng thái): lựa chọn lấy từ dữ liệu vừa tải
    $("#search-filters").innerHTML = (cat.filters || []).map(f =>
        `<select name="${f.name}">${optionsHtml(f.options(data), state.filters[f.name], f.all)}</select>`).join("");

    refreshFormSelects();
    renderRows();
}

/** Form đang mở thì nạp lại các ô chọn (tỉnh, loài...) theo dữ liệu mới, giữ nguyên lựa chọn cũ. */
function refreshFormSelects() {
    const cat = CATALOGS[state.key];
    for (const f of (cat.fields || [])) {
        if (f.type !== "select") continue;
        const select = document.getElementById("f-" + f.name);
        if (!select) continue;
        const opts = f.options(state.data);
        select.innerHTML = optionsHtml(opts, select.value, f.placeholder);
        const hint = [...select.parentElement.querySelectorAll(".hl-hint")]
            .find(h => h.textContent === "Chưa có dữ liệu để chọn.");
        if (opts.length && hint) hint.remove();
    }
}

function renderRows() {
    const cat = CATALOGS[state.key];
    const items = state.data.items || [];
    const usage = state.data.usage || {};

    $("#list-body").innerHTML = items.map((r, index) => {
        const id = r[cat.idKey];
        const used = usage[id] || 0;
        const ctx = { data: state.data, index, used };
        const classes = [cat.rowClass ? cat.rowClass(r) : "", state.editingId === id ? "is-editing" : ""].join(" ");
        const cells = cat.columns.map(c => `<td class="${c.cls || ""}">${c.html(r, ctx)}</td>`).join("");
        const actions = (cat.extraActions ? cat.extraActions(r) : "")
            + `<button type="button" class="hl-btn hl-btn-sm" data-act="edit" data-id="${id}">Sửa</button>`
            + `<button type="button" class="hl-btn hl-btn-sm hl-btn-danger" data-act="delete" data-id="${id}"
                 ${used > 0 ? "disabled" : ""} title="${used > 0 ? esc(cat.blocked) : "Xóa"}">Xóa</button>`;
        return `<tr class="${classes}">${cells}<td class="hl-act">${actions}</td></tr>`;
    }).join("");

    if (!items.length) {
        const filtered = Object.values(state.filters).some(Boolean);
        $("#list-body").innerHTML = `<tr><td colspan="${cat.columns.length + 1}" class="hl-empty">${filtered
            ? "Không tìm thấy " + esc(cat.label) + " phù hợp."
            : "Chưa có " + esc(cat.label) + " nào. Bấm “+ Thêm " + esc(cat.label) + "” để bắt đầu."}</td></tr>`;
    }
    $("#list-count").textContent = items.length + " " + cat.label;
}

/** Tạo các ô nhập theo khai báo fields, điền sẵn giá trị của item. */
function renderForm(item) {
    const cat = CATALOGS[state.key];
    $("#form-fields").innerHTML = cat.fields.map(f => {
        let value = item[f.name];
        if ((value === null || value === undefined) && f.default !== undefined) value = f.default;
        if ((value === null || value === undefined) && f.fromFilter) value = state.filters[f.name];
        const req = f.required ? " <b>*</b>" : "";
        const common = `name="${f.name}" id="f-${f.name}"`;
        let input;
        if (f.type === "select") {
            const opts = f.options(state.data);
            input = `<select ${common}>${optionsHtml(opts, value, f.placeholder)}</select>`;
            if (!opts.length) input += `<small class="hl-hint">Chưa có dữ liệu để chọn.</small>`;
        } else if (f.type === "textarea") {
            input = `<textarea ${common} rows="${f.rows || 3}" placeholder="${esc(f.placeholder)}">${esc(value)}</textarea>`;
        } else if (f.type === "checkbox") {
            return `<label class="hl-field hl-check"><input type="checkbox" ${common} ${value ? "checked" : ""} />
                    <span>${esc(f.label)}</span></label>`;
        } else {
            const type = f.type || "text";
            const limits = (f.max && type !== "number" ? ` maxlength="${f.max}"` : "")
                + (type === "number" ? ` min="${f.min}" max="${f.max}"` : "");
            input = `<input ${common} type="${type}"${limits} value="${esc(value)}" placeholder="${esc(f.placeholder)}"
                     class="${f.upper ? "hl-upper" : ""}" />`;
        }
        return `<label class="hl-field ${f.span2 ? "hl-span-2" : ""}" data-field="${f.name}">
                    <span>${esc(f.label)}${req}</span>
                    ${input}
                    ${f.hint ? `<small class="hl-hint">${esc(f.hint)}</small>` : ""}
                    <small class="hl-err"></small>
                </label>`;
    }).join("");

    $("#form-fields").querySelectorAll("input.hl-upper").forEach(input =>
        input.addEventListener("input", () => {
            const pos = input.selectionStart;
            input.value = input.value.toUpperCase();
            input.setSelectionRange(pos, pos);
        }));
}

async function openForm(id) {
    const cat = CATALOGS[state.key];
    hideAlert();
    // Thêm mới: hỏi Servlet giá trị mặc định (VD thứ tự kế tiếp). Sửa: tải bản ghi mới nhất.
    const { status, body } = await api(state.key, id ? { id } : { action: "new" });
    if (status !== 200) {
        showAlert("error", body.message);
        if (status === 404) loadList();
        return;
    }
    state.editingId = id;
    $("#form-title").textContent = (id ? "Sửa " : "Thêm ") + cat.label;
    renderForm(body.item);
    clearErrors();
    $("#form-panel").hidden = false;
    renderRows();
    $("#form-panel").scrollIntoView({ behavior: "smooth", block: "start" });
    const first = $("#form-fields input:not([type=checkbox]), #form-fields select");
    if (first) first.focus({ preventScroll: true });
}

function closeForm() {
    state.editingId = null;
    $("#form-panel").hidden = true;
    $("#form-fields").innerHTML = "";
}

function clearErrors() {
    $("#form-global").hidden = true;
    document.querySelectorAll("#form-fields .hl-field").forEach(f => {
        f.classList.remove("has-error");
        const err = f.querySelector(".hl-err");
        if (err) err.textContent = "";
    });
}

/** Hiện lỗi Servlet trả về (400): mỗi lỗi nằm dưới đúng ô nhập. */
function showErrors(errors) {
    clearErrors();
    let global = errors._global;
    for (const [field, message] of Object.entries(errors)) {
        const box = document.querySelector(`#form-fields [data-field="${field}"]`);
        if (box) {
            box.classList.add("has-error");
            box.querySelector(".hl-err").textContent = message;
        } else if (field !== "_global") {
            global = global || message;
        }
    }
    $("#form-global").textContent = global || "Vui lòng kiểm tra lại các ô được đánh dấu đỏ.";
    $("#form-global").hidden = false;
    const firstBad = document.querySelector("#form-fields .has-error input, #form-fields .has-error select");
    if (firstBad) firstBad.focus();
}

async function saveForm(event) {
    event.preventDefault();
    const extra = { action: "save" };
    if (state.editingId) extra.id = state.editingId;
    const { status, body } = await api(state.key, formParams($("#item-form"), extra), "POST");
    if (status === 200) {
        closeForm();
        showAlert("success", body.message);
        loadList();
    } else if (status === 400 && body.errors) {
        showErrors(body.errors);
    } else {
        showAlert("error", body.message);
        if (status === 404) { closeForm(); loadList(); }
    }
}

async function deleteItem(id) {
    const cat = CATALOGS[state.key];
    const item = (state.data.items || []).find(r => r[cat.idKey] === id);
    const name = item ? item[cat.nameKey] : "";
    if (!confirm(`Xóa ${cat.label} "${name}"?${cat.deleteNote || ""}`)) return;

    const { status, body } = await api(state.key, { action: "delete", id }, "POST");
    if (status === 200 && state.editingId === id) closeForm();
    showAlert(status === 200 ? "success" : "error", body.message);
    loadList();
}

async function changeSupplierStatus(id, newStatus) {
    const { status, body } = await api("nha-cung-cap", { action: "status", id, status: newStatus }, "POST");
    showAlert(status === 200 ? "success" : "error", body.message);
    loadList();
}

// Bấm nút trong bảng (Sửa / Xóa / Ngừng hợp tác) - 1 hàm nghe cho cả bảng
$("#list-body").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-act]");
    if (!btn) return;
    const id = Number(btn.dataset.id);
    if (btn.dataset.act === "edit") openForm(id);
    else if (btn.dataset.act === "delete") deleteItem(id);
    else if (btn.dataset.act === "status") changeSupplierStatus(id, btn.dataset.status);
});

$("#btn-add").addEventListener("click", () => openForm(null));
$("#btn-cancel").addEventListener("click", () => { closeForm(); renderRows(); });
$("#item-form").addEventListener("submit", saveForm);

// Tìm kiếm: ghi bộ lọc lên địa chỉ (#vung-trong?q=...) để F5 / gửi link vẫn giữ nguyên
$("#search-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const params = new URLSearchParams();
    for (const [k, v] of new FormData(e.target)) if (v.trim()) params.append(k, v.trim());
    const hash = "#" + state.key + ([...params].length ? "?" + params : "");
    if (location.hash === hash) loadList(); else location.hash = hash;
});

$("#clear-filter").addEventListener("click", (e) => {
    e.preventDefault();
    location.hash = "#" + state.key;
});

// ===================================================== 4. GÁN ĐỘ XAY CHO PHƯƠNG PHÁP PHA (UC 46)

let grindMethodId = null;

async function openGrind(id) {
    const { status, body } = await api("phuong-phap-pha", { action: "grind", id });
    if (status !== 200) {
        flash = { type: "error", message: body.message };
        location.hash = "#phuong-phap-pha";
        return;
    }
    grindMethodId = body.method.brewMethodId;
    $("#grind-title").textContent = `Gán độ xay cho “${body.method.methodName}”`;
    $("#grind-params").textContent = [body.method.coffeeWaterRatio, body.method.waterTempC, body.method.brewTime]
        .filter(Boolean).join(" · ");

    const choices = [["", "Không dùng"], ["OK", "Phù hợp"], ["REC", "Khuyến nghị"]];
    $("#grind-body").innerHTML = body.grindSizes.length ? body.grindSizes.map(g => {
        const sel = body.selected[g.grindSizeId] || "";
        return `<tr>
            <td class="hl-num">${esc(g.sortOrder)}</td>
            <td><strong>${esc(g.grindName)}</strong> <span class="hl-code">${esc(g.grindCode)}</span>
                ${g.description ? `<div class="hl-muted hl-clip">${esc(g.description)}</div>` : ""}</td>
            ${choices.map(([v, text]) => `<td class="hl-choice"><input type="radio" name="grind_${g.grindSizeId}"
                value="${v}" ${sel === v ? "checked" : ""} aria-label="${text}" /></td>`).join("")}
        </tr>`;
    }).join("") : `<tr><td colspan="5" class="hl-empty">Chưa có độ xay nào. Hãy <a class="hl-link" href="#do-xay">thêm độ xay</a> trước.</td></tr>`;
}

$("#grind-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const { status, body } = await api("phuong-phap-pha",
        formParams(e.target, { action: "saveGrind", id: grindMethodId }), "POST");
    flash = { type: status === 200 ? "success" : "error", message: body.message };
    location.hash = "#phuong-phap-pha";
});

// ===================================================== 5. ĐIỀU HƯỚNG THEO # TRÊN ĐỊA CHỈ

function route() {
    const [rawPath, query = ""] = location.hash.slice(1).split("?");
    const path = decodeURIComponent(rawPath);
    const params = Object.fromEntries(new URLSearchParams(query));   // tự giải mã %xx và dấu +

    hideAlert();
    if (flash) { showAlert(flash.type, flash.message); flash = null; }

    let tab = "tong-quan", view = "view-home";
    if (path === "phuong-phap-pha/gan-do-xay") {
        tab = "phuong-phap-pha"; view = "view-grind";
        openGrind(params.id);
    } else if (CATALOGS[path]) {
        tab = path; view = "view-list";
        openList(path, params);
    }
    for (const id of ["view-home", "view-list", "view-grind"]) $("#" + id).hidden = id !== view;
    document.querySelectorAll(".hl-tab").forEach(a => a.classList.toggle("active", a.dataset.tab === tab));
    document.title = (tab === "tong-quan" ? "Tổng quan" : CATALOGS[tab].title) + " - Danh mục";
}

window.addEventListener("hashchange", route);
route();

// Menu tài khoản trên header chung (shared.js gọi toggleMenu() nhưng không định nghĩa)
function toggleMenu() {
    const menu = document.getElementById("accountMenu");
    if (menu) menu.classList.toggle("show");
}
document.addEventListener("click", (e) => {
    const menu = document.getElementById("accountMenu");
    if (menu && !e.target.closest(".account")) menu.classList.remove("show");
});
