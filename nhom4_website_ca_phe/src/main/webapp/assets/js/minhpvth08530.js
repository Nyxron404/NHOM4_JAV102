/* =====================================================================
 * Trang quản lý sản phẩm — phần Phạm Văn Minh (UC51 – UC80)
 * Gọi API: ../api/products, ../api/variants, ../api/variant-images,
 *          ../api/packages, ../api/product-lookups
 * ===================================================================== */
(function () {
    "use strict";

    const API = "../api";
    const SCORE_FIELDS = [
        ["aromaScore", "Hương thơm"],
        ["acidityScore", "Độ chua"],
        ["sweetnessScore", "Độ ngọt"],
        ["bitternessScore", "Độ đắng"],
        ["bodyScore", "Độ đậm"],
        ["aftertasteScore", "Hậu vị"],
    ];
    const STATUS_TEXT = { ACTIVE: "Đang bán", INACTIVE: "Ngừng KD", DRAFT: "Nháp" };
    const ATTRS = [
        { field: "region", key: "regionId", list: "regions", label: "Vùng trồng", uc: "UC66" },
        { field: "variety", key: "varietyId", list: "varieties", label: "Giống cà phê", uc: "UC67" },
        { field: "process", key: "processId", list: "processes", label: "Phương pháp sơ chế", uc: "UC68" },
        { field: "roastLevel", key: "roastLevelId", list: "roastLevels", label: "Mức rang", uc: "UC69" },
    ];

    const state = {
        lookups: null,
        keyword: "",
        productStatus: "",
        products: [],
        productId: null,
        product: null,
        variants: [],
        variantStatus: "",
        variantId: null,
        variant: null,
        packages: [],
        tab: "info",
    };

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    // ------------------------------------------------------------------ tiện ích

    function esc(value) {
        return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
        }[ch]));
    }

    function money(value) {
        if (value === null || value === undefined || value === "") return "—";
        return new Intl.NumberFormat("vi-VN").format(Number(value)) + " ₫";
    }

    function dateTime(value) {
        if (!value) return "—";
        return new Date(value).toLocaleString("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric",
            hour: "2-digit", minute: "2-digit",
        });
    }

    function badge(status) {
        return `<span class="badge badge-${esc(status)}">${esc(STATUS_TEXT[status] || status)}</span>`;
    }

    function orDash(value, suffix = "") {
        return value === null || value === undefined || value === ""
            ? '<span class="missing">chưa có</span>'
            : esc(value) + suffix;
    }

    function debounce(fn, wait) {
        let timer;
        return (...args) => {
            clearTimeout(timer);
            timer = setTimeout(() => fn(...args), wait);
        };
    }

    async function api(path, { params, body, method, contentType } = {}) {
        let url = `${API}/${path}`;
        if (params) {
            const query = new URLSearchParams();
            Object.entries(params).forEach(([key, value]) => {
                if (value !== null && value !== undefined && value !== "") query.append(key, value);
            });
            const text = query.toString();
            if (text) url += (url.includes("?") ? "&" : "?") + text;
        }
        const options = { method: method || (body ? "POST" : "GET"), headers: {} };
        if (body instanceof URLSearchParams) {
            options.body = body;
            options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
        } else if (body) {
            options.body = body;
            options.headers["Content-Type"] = contentType;
        }
        let response;
        try {
            response = await fetch(url, options);
        } catch (e) {
            throw new Error("Không kết nối được máy chủ");
        }
        let data = null;
        try {
            data = await response.json();
        } catch (e) {
            data = null;
        }
        if (!response.ok) {
            throw new Error((data && data.error) || `Lỗi ${response.status}`);
        }
        return data;
    }

    function post(path, action, fields = {}) {
        const body = new URLSearchParams();
        body.append("action", action);
        Object.entries(fields).forEach(([key, value]) => {
            if (Array.isArray(value)) value.forEach((v) => body.append(key, v));
            else if (value !== null && value !== undefined) body.append(key, value);
        });
        return api(path, { body });
    }

    function toast(message, type = "ok") {
        const el = document.createElement("div");
        el.className = "toast" + (type === "error" ? " error" : "");
        el.textContent = message;
        $("#toastRoot").appendChild(el);
        setTimeout(() => el.remove(), 3600);
    }

    async function run(task, successMessage) {
        try {
            const result = await task();
            toast(successMessage || (result && result.message) || "Đã lưu");
            return result;
        } catch (e) {
            toast(e.message, "error");
            return undefined;
        }
    }

    function options(list, selected, placeholder) {
        const head = placeholder !== undefined ? `<option value="">${esc(placeholder)}</option>` : "";
        return head + (list || []).map((item) =>
            `<option value="${item.id}" ${String(item.id) === String(selected ?? "") ? "selected" : ""}>`
            + `${esc(item.name)}${item.detail ? " — " + esc(item.detail) : ""}</option>`).join("");
    }

    // ------------------------------------------------------------------ modal

    function openModal({ title, body, submitText = "Lưu", wide = false, onSubmit, danger = false }) {
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
            const errorBox = $(".form-error", form);
            errorBox.classList.remove("show");
            try {
                const result = await onSubmit(new FormData(form), form);
                close();
                if (result && result.message) toast(result.message);
            } catch (err) {
                errorBox.textContent = err.message;
                errorBox.classList.add("show");
                button.disabled = false;
            }
        });
        const first = $("input:not([type=hidden]), select, textarea", form);
        if (first) setTimeout(() => first.focus(), 30);
        return form;
    }

    function confirmBox(title, message, submitText, onConfirm) {
        openModal({
            title,
            body: `<p style="font-size:14px;line-height:1.6">${message}</p>`,
            submitText,
            danger: true,
            onSubmit: () => onConfirm(),
        });
    }

    function formToObject(formData) {
        const data = {};
        for (const [key, value] of formData.entries()) {
            if (key in data) data[key] = [].concat(data[key], value);
            else data[key] = typeof value === "string" ? value.trim() : value;
        }
        return data;
    }

    // ------------------------------------------------------------------ sản phẩm (UC51–UC58)

    async function loadProducts() {
        const list = $("#productList");
        try {
            state.products = await api("products", {
                params: { keyword: state.keyword, status: state.productStatus },
            });
        } catch (e) {
            list.innerHTML = `<div class="empty">${esc(e.message)}</div>`;
            return;
        }
        $("#productCount").textContent = `${state.products.length} sản phẩm`;
        if (!state.products.length) {
            list.innerHTML = `<div class="empty"><div class="big">☕</div>Không có sản phẩm phù hợp</div>`;
            return;
        }
        list.innerHTML = state.products.map((p) => `
            <div class="product-item ${p.productId === state.productId ? "selected" : ""}" data-id="${p.productId}">
              <div class="row"><span class="name">${esc(p.productName)}</span>${badge(p.status)}</div>
              <div class="code">${esc(p.productCode)}</div>
              <div class="counts">${p.variantCount} biến thể · ${p.activeVariantCount} đang bán</div>
            </div>`).join("");
        $$(".product-item", list).forEach((el) => {
            el.onclick = () => selectProduct(Number(el.dataset.id));
        });
    }

    async function selectProduct(productId, keepVariant = false) {
        state.productId = productId;
        if (!keepVariant) {
            state.variantId = null;
            state.variant = null;
            state.tab = "info";
        }
        $$(".product-item").forEach((el) =>
            el.classList.toggle("selected", Number(el.dataset.id) === productId));
        updateHash();
        await loadProductDetail();
    }

    async function loadProductDetail() {
        if (!state.productId) {
            renderMain();
            return;
        }
        try {
            const data = await api("products", { params: { id: state.productId } });
            state.product = data.product;
            state.variants = data.variants;
        } catch (e) {
            state.productId = null;
            state.product = null;
            toast(e.message, "error");
        }
        if (state.variantId && !state.variants.some((v) => v.variantId === state.variantId)) {
            state.variantId = null;
            state.variant = null;
        }
        if (state.variantId) await loadVariant(false);
        renderMain();
    }

    function productForm(product) {
        const editing = !!product;
        openModal({
            title: editing ? "Sửa sản phẩm" : "Thêm sản phẩm",
            submitText: editing ? "Lưu thay đổi" : "Thêm sản phẩm",
            body: `
              <div class="form-grid">
                <div class="field"><label>Mã sản phẩm <span class="req">*</span></label>
                  <input name="productCode" maxlength="40" required value="${esc(product?.productCode)}" placeholder="VD: CAFE-ROBUSTA">
                  <span class="hint">Chữ in hoa không dấu, số, _ - . (tự viết hoa)</span></div>
                <div class="field"><label>Tên sản phẩm <span class="req">*</span></label>
                  <input name="productName" maxlength="200" required value="${esc(product?.productName)}" placeholder="VD: Cà phê Robusta"></div>
                <div class="field full"><label>Mô tả</label>
                  <textarea name="description" placeholder="Giới thiệu ngắn hiển thị trên trang QR">${esc(product?.description)}</textarea></div>
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                if (editing) fields.id = product.productId;
                const result = await post("products", editing ? "update" : "create", fields);
                await loadProducts();
                await selectProduct(editing ? product.productId : result.id, editing);
                return result;
            },
        });
    }

    function deleteProduct() {
        const p = state.product;
        confirmBox("Xóa sản phẩm", `Xóa sản phẩm <b>${esc(p.productName)}</b>? Chỉ xóa được khi sản phẩm chưa có biến thể.`,
            "Xóa sản phẩm", async () => {
                const result = await post("products", "delete", { id: p.productId });
                state.productId = null;
                state.product = null;
                updateHash();
                await loadProducts();
                renderMain();
                return result;
            });
    }

    function toggleProduct() {
        const p = state.product;
        if (p.status === "ACTIVE") {
            confirmBox("Ngừng kinh doanh sản phẩm",
                `Ngừng kinh doanh <b>${esc(p.productName)}</b>? Toàn bộ biến thể và gói bán của sản phẩm cũng ngừng bán. Tồn kho và lịch sử bán vẫn giữ nguyên.`,
                "Ngừng kinh doanh", async () => {
                    const result = await post("products", "deactivate", { id: p.productId });
                    await refreshAll();
                    return result;
                });
        } else {
            run(async () => {
                const result = await post("products", "activate", { id: p.productId });
                await refreshAll();
                return result;
            });
        }
    }

    async function refreshAll() {
        await loadProducts();
        await loadProductDetail();
    }

    // ------------------------------------------------------------------ biến thể (UC59–UC71)

    async function loadVariant(render = true) {
        try {
            const [variant, packages] = await Promise.all([
                api("variants", { params: { id: state.variantId } }),
                api("packages", { params: { variantId: state.variantId } }),
            ]);
            state.variant = variant;
            state.packages = packages;
        } catch (e) {
            toast(e.message, "error");
            state.variantId = null;
            state.variant = null;
        }
        if (render) renderMain();
    }

    async function selectVariant(variantId) {
        state.variantId = variantId;
        state.tab = state.variant && state.variant.variantId === variantId ? state.tab : "info";
        updateHash();
        await loadVariant();
        const panel = $("#variantPanel");
        if (panel) panel.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    async function afterVariantChange() {
        await loadProducts();
        await loadProductDetail();
    }

    function variantCreateForm() {
        const lk = state.lookups;
        openModal({
            title: "Thêm biến thể",
            submitText: "Thêm biến thể",
            wide: true,
            body: `
              <div class="callout">Chọn đủ 4 thuộc tính chuyên môn thì biến thể được mở bán ngay;
                thiếu thuộc tính nào thì lưu <b>nháp</b> để hoàn thiện sau.</div>
              <div class="form-grid">
                <div class="field"><label>Mã biến thể <span class="req">*</span></label>
                  <input name="variantCode" maxlength="40" placeholder="VD: ROB-KS-HONEY"></div>
                <div class="field"><label>Tên biến thể <span class="req">*</span></label>
                  <input name="variantName" maxlength="200" placeholder="VD: Robusta Khe Sanh Honey"></div>
                ${ATTRS.map((a) => `
                  <div class="field"><label>${a.label}</label>
                    <select name="${a.key}">${options(lk[a.list], null, "— Chưa chọn —")}</select></div>`).join("")}
                <div class="field"><label>Độ cao (m)</label><input name="altitudeM" type="number" min="0" max="5000" placeholder="VD: 1500"></div>
                <div class="field"><label>Mùa vụ</label><input name="harvestSeason" maxlength="20" placeholder="VD: 2025-2026"></div>
                <div class="field"><label>Thứ tự hiển thị</label><input name="displayOrder" type="number" min="0" value="${state.variants.length + 1}"></div>
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                fields.productId = state.productId;
                const result = await post("variants", "create", fields);
                state.variantId = result.id;
                state.tab = "info";
                updateHash();
                await afterVariantChange();
                return result;
            },
        });
    }

    function variantEditForm() {
        const v = state.variant;
        openModal({
            title: "Sửa biến thể",
            submitText: "Lưu thay đổi",
            body: `
              <div class="form-grid">
                <div class="field"><label>Mã biến thể <span class="req">*</span></label>
                  <input name="variantCode" maxlength="40" value="${esc(v.variantCode)}"></div>
                <div class="field"><label>Tên biến thể <span class="req">*</span></label>
                  <input name="variantName" maxlength="200" value="${esc(v.variantName)}"></div>
                <div class="field"><label>Độ cao (m)</label><input name="altitudeM" type="number" min="0" max="5000" value="${esc(v.altitudeM)}"></div>
                <div class="field"><label>Mùa vụ</label><input name="harvestSeason" maxlength="20" value="${esc(v.harvestSeason)}"></div>
                <div class="field"><label>Thứ tự hiển thị</label><input name="displayOrder" type="number" min="0" value="${esc(v.displayOrder)}"></div>
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                fields.id = v.variantId;
                const result = await post("variants", "update", fields);
                await afterVariantChange();
                return result;
            },
        });
    }

    function completeDraftForm() {
        const v = state.variant;
        const lk = state.lookups;
        openModal({
            title: "Hoàn thiện biến thể nháp",
            submitText: "Hoàn thiện",
            wide: true,
            body: `
              <div class="callout">Biến thể nháp thường do phiên nhập kho tự tạo. Bổ sung đủ thông tin
                bên dưới rồi bấm <b>Kích hoạt</b> để mở bán.</div>
              <div class="form-grid">
                <div class="field full"><label>Tên biến thể <span class="req">*</span></label>
                  <input name="variantName" maxlength="200" value="${esc(v.variantName)}"></div>
                ${ATTRS.map((a) => `
                  <div class="field"><label>${a.label} <span class="req">*</span></label>
                    <select name="${a.key}">${options(lk[a.list], v[a.key], "— Chọn —")}</select></div>`).join("")}
                <div class="field"><label>Độ cao (m)</label><input name="altitudeM" type="number" min="0" max="5000" value="${esc(v.altitudeM)}"></div>
                <div class="field"><label>Mùa vụ</label><input name="harvestSeason" maxlength="20" value="${esc(v.harvestSeason)}"></div>
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                const missing = ATTRS.filter((a) => !fields[a.key]).map((a) => a.label.toLowerCase());
                if (missing.length) throw new Error("Chưa chọn " + missing.join(", "));
                fields.id = v.variantId;
                const result = await post("variants", "complete", fields);
                await afterVariantChange();
                return result;
            },
        });
    }

    function activateVariant() {
        run(async () => {
            const result = await post("variants", "activate", { id: state.variantId });
            await afterVariantChange();
            return result;
        });
    }

    function deactivateVariant() {
        const v = state.variant;
        confirmBox("Ngừng kinh doanh biến thể",
            `Ngừng kinh doanh <b>${esc(v.variantName)}</b>? Tất cả gói bán của biến thể cũng ngừng bán.`,
            "Ngừng kinh doanh", async () => {
                const result = await post("variants", "deactivate", { id: v.variantId });
                await afterVariantChange();
                return result;
            });
    }

    function deleteVariant() {
        const v = state.variant;
        confirmBox("Xóa biến thể",
            `Xóa <b>${esc(v.variantName)}</b> cùng ảnh, nốt hương và gợi ý pha? Chỉ xóa được khi biến thể chưa có gói bán.`,
            "Xóa biến thể", async () => {
                const result = await post("variants", "delete", { id: v.variantId });
                state.variantId = null;
                state.variant = null;
                updateHash();
                await afterVariantChange();
                return result;
            });
    }

    async function assignAttribute(attr, valueId, select) {
        if (!valueId) return;
        select.disabled = true;
        await run(async () => {
            const result = await post("variants", "assign", {
                id: state.variantId, field: attr.field, valueId,
            });
            await afterVariantChange();
            return result;
        });
        select.disabled = false;
    }

    // ------------------------------------------------------------------ gói bán (UC74–UC80)

    function suggestSku(weight, grindId) {
        const grind = (state.lookups.grindSizes || []).find((g) => String(g.id) === String(grindId));
        const grindCode = grind ? (grind.detail ? "NH" : grind.name.normalize("NFD")
            .replace(/[̀-ͯ]/g, "").replace(/đ/gi, "D").split(/\s+/)
            .map((w) => w[0]).join("").toUpperCase()) : "";
        return [state.variant.variantCode, weight ? weight + "G" : "", grindCode].filter(Boolean).join("-");
    }

    function packageForm(pkg) {
        const editing = !!pkg;
        const form = openModal({
            title: editing ? `Sửa gói bán ${pkg.sku}` : "Thêm gói bán",
            submitText: editing ? "Lưu thay đổi" : "Thêm gói bán",
            body: `
              ${editing ? `<div class="callout">Gói đã phát sinh nhập/bán thì không đổi được trọng lượng và dạng hạt.</div>` : ""}
              <div class="form-grid">
                <div class="field"><label>Dạng hạt / độ xay <span class="req">*</span></label>
                  <select name="grindSizeId">${options(state.lookups.grindSizes, pkg?.grindSizeId, "— Chọn —")}</select></div>
                <div class="field"><label>Trọng lượng (gram) <span class="req">*</span></label>
                  <input name="weightG" type="number" min="1" value="${esc(pkg?.weightG ?? "")}" placeholder="VD: 250"></div>
                <div class="field"><label>SKU <span class="req">*</span></label>
                  <input name="sku" maxlength="40" value="${esc(pkg?.sku ?? "")}" placeholder="Tự gợi ý theo biến thể">
                  <span class="hint">Mã gói in trên tem, không trùng</span></div>
                <div class="field"><label>Tồn tối thiểu</label>
                  <input name="minStockLevel" type="number" min="0" value="${esc(pkg?.minStockLevel ?? 5)}">
                  <span class="hint">Dưới mức này sẽ báo tồn thấp</span></div>
                ${editing ? "" : `
                <div class="field full"><label>Giá bán (đồng) <span class="req">*</span></label>
                  <input name="salePrice" type="number" min="0" step="1000" placeholder="VD: 185000"></div>`}
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                let result;
                if (editing) {
                    fields.id = pkg.packageId;
                    result = await post("packages", "update", fields);
                } else {
                    fields.variantId = state.variantId;
                    result = await post("packages", "create", fields);
                    state.tab = "packages";
                }
                await afterVariantChange();
                return result;
            },
        });
        if (!editing) {
            const sku = $("[name=sku]", form);
            let touched = false;
            sku.addEventListener("input", () => { touched = true; });
            const update = () => {
                if (!touched) sku.value = suggestSku($("[name=weightG]", form).value, $("[name=grindSizeId]", form).value);
            };
            $("[name=weightG]", form).addEventListener("input", update);
            $("[name=grindSizeId]", form).addEventListener("change", update);
        }
    }

    function priceForm(pkg) {
        openModal({
            title: `Cập nhật giá bán — ${pkg.sku}`,
            submitText: "Cập nhật giá",
            body: `
              <div class="callout">Giá hiện hành: <b>${money(pkg.salePrice)}</b> (từ ${dateTime(pkg.priceEffectiveFrom)}).
                Giá cũ được giữ trong lịch sử, không bị ghi đè.</div>
              <div class="form-grid">
                <div class="field"><label>Giá mới (đồng) <span class="req">*</span></label>
                  <input name="salePrice" type="number" min="0" step="1000" value="${esc(pkg.salePrice ?? "")}"></div>
                <div class="field"><label>Ngày áp dụng</label>
                  <input name="effectiveFrom" type="datetime-local">
                  <span class="hint">Để trống = áp dụng ngay</span></div>
                <div class="field full"><label>Ghi chú</label>
                  <input name="note" maxlength="500" placeholder="VD: Tăng giá vụ mới"></div>
              </div>`,
            onSubmit: async (formData) => {
                const fields = formToObject(formData);
                fields.id = pkg.packageId;
                const result = await post("packages", "price", fields);
                await afterVariantChange();
                return result;
            },
        });
    }

    async function priceHistory(pkg) {
        let rows;
        try {
            rows = await api("packages", { params: { historyOf: pkg.packageId } });
        } catch (e) {
            toast(e.message, "error");
            return;
        }
        openModal({
            title: `Lịch sử giá bán — ${pkg.sku}`,
            wide: true,
            body: `
              <div class="table-wrap"><table class="grid">
                <thead><tr><th>Áp dụng từ</th><th class="num">Giá bán</th><th>Trạng thái</th><th>Người đổi</th><th>Ghi chú</th></tr></thead>
                <tbody>${rows.map((h) => `
                  <tr><td>${dateTime(h.effectiveFrom)}</td>
                    <td class="num"><b>${money(h.salePrice)}</b></td>
                    <td>${h.current ? '<span class="badge badge-ACTIVE">Đang áp dụng</span>'
                        : h.applied ? '<span class="badge badge-INACTIVE">Đã thay</span>'
                        : '<span class="badge badge-PENDING">Chờ áp dụng</span>'}</td>
                    <td>${esc(h.changedByName || "—")}</td>
                    <td>${esc(h.note || "")}</td></tr>`).join("")}
                </tbody></table></div>`,
        });
    }

    function togglePackage(pkg) {
        if (pkg.status === "ACTIVE") {
            confirmBox("Ngừng bán gói", `Ngừng bán gói <b>${esc(pkg.sku)}</b>? Quầy sẽ không chọn được gói này, tồn kho vẫn giữ.`,
                "Ngừng bán", async () => {
                    const result = await post("packages", "deactivate", { id: pkg.packageId });
                    await afterVariantChange();
                    return result;
                });
        } else {
            run(async () => {
                const result = await post("packages", "activate", { id: pkg.packageId });
                await afterVariantChange();
                return result;
            });
        }
    }

    function deletePackage(pkg) {
        confirmBox("Xóa gói bán", `Xóa gói <b>${esc(pkg.sku)}</b> và lịch sử giá của nó? Gói đã nhập/bán thì không xóa được.`,
            "Xóa gói", async () => {
                const result = await post("packages", "delete", { id: pkg.packageId });
                await afterVariantChange();
                return result;
            });
    }

    // ------------------------------------------------------------------ ảnh (UC72–UC73)

    async function uploadImages(files) {
        for (const file of files) {
            if (file.size > 5 * 1024 * 1024) {
                toast(`${file.name}: ảnh tối đa 5 MB`, "error");
                continue;
            }
            await run(() => api("variant-images", {
                params: { variantId: state.variantId },
                body: file,
                contentType: file.type || "application/octet-stream",
            }));
        }
        await loadVariant();
    }

    function deleteImage(image) {
        confirmBox("Xóa ảnh", "Xóa ảnh này khỏi biến thể?", "Xóa ảnh", async () => {
            const result = await post("variant-images", "delete", { id: image.imageId });
            await loadVariant();
            return result;
        });
    }

    // ------------------------------------------------------------------ render

    function renderMain() {
        const main = $("#mainPanel");
        if (!state.product) {
            main.innerHTML = `<div class="panel empty"><div class="big">☕</div>
                Chọn một sản phẩm bên trái để xem biến thể và gói bán,<br>hoặc bấm <b>+ Thêm sản phẩm</b>.</div>`;
            return;
        }
        main.innerHTML = renderProductHead() + renderVariantTable() + (state.variant ? renderVariantPanel() : "");
        bindMain();
    }

    function renderProductHead() {
        const p = state.product;
        return `
          <div class="panel product-head">
            <div>
              <h2>${esc(p.productName)} ${badge(p.status)}</h2>
              <div class="meta"><span class="chip-code">${esc(p.productCode)}</span>
                <span>Tạo lúc ${dateTime(p.createdAt)}</span>
                <span title="Mã trang QR của sản phẩm">QR: ${esc(p.qrToken.slice(0, 12))}…</span></div>
              ${p.description ? `<div class="desc">${esc(p.description)}</div>` : ""}
              <div class="stats">
                <div class="stat"><b>${p.variantCount}</b><span>biến thể</span></div>
                <div class="stat"><b>${p.activeVariantCount}</b><span>đang bán</span></div>
              </div>
            </div>
            <div class="actions">
              <button class="btn" data-act="edit-product">Sửa</button>
              ${p.status === "ACTIVE"
                ? '<button class="btn btn-danger" data-act="toggle-product">Ngừng kinh doanh</button>'
                : '<button class="btn btn-success" data-act="toggle-product">Kinh doanh lại</button>'}
              <button class="btn btn-danger" data-act="delete-product">Xóa</button>
            </div>
          </div>`;
    }

    function renderVariantTable() {
        const list = state.variantStatus
            ? state.variants.filter((v) => v.status === state.variantStatus) : state.variants;
        const seg = [["", "Tất cả"], ["DRAFT", "Nháp"], ["ACTIVE", "Đang bán"], ["INACTIVE", "Ngừng KD"]]
            .map(([value, label]) => `<button type="button" data-vstatus="${value}" class="${state.variantStatus === value ? "active" : ""}">${label}</button>`).join("");
        const rows = list.map((v) => `
            <tr class="clickable ${v.variantId === state.variantId ? "selected" : ""}" data-variant="${v.variantId}">
              <td><span class="chip-code">${esc(v.variantCode)}</span></td>
              <td><b>${esc(v.variantName)}</b></td>
              <td>${orDash(v.regionName)}</td>
              <td>${orDash(v.varietyName)}</td>
              <td>${orDash(v.processName)}</td>
              <td>${orDash(v.roastName)}</td>
              <td class="num">${v.packageCount}</td>
              <td>${badge(v.status)}</td>
            </tr>`).join("");
        return `
          <div class="panel">
            <div class="section-head">
              <div class="left"><h3>Biến thể</h3><div class="seg">${seg}</div></div>
              <button class="btn btn-primary btn-sm" data-act="add-variant" ${state.product.status !== "ACTIVE" ? "disabled title='Sản phẩm đã ngừng kinh doanh'" : ""}>+ Thêm biến thể</button>
            </div>
            ${list.length ? `
              <div class="table-wrap"><table class="grid">
                <thead><tr><th>Mã</th><th>Tên biến thể</th><th>Vùng trồng</th><th>Giống</th><th>Sơ chế</th><th>Mức rang</th><th class="num">Gói</th><th>Trạng thái</th></tr></thead>
                <tbody>${rows}</tbody></table></div>`
            : `<div class="empty">Chưa có biến thể${state.variantStatus ? " ở trạng thái này" : ""}.</div>`}
          </div>`;
    }

    function renderVariantPanel() {
        const v = state.variant;
        const tabs = [
            ["info", "Thông tin"],
            ["flavor", "Hương vị"],
            ["brew", "Cách pha", v.brewMethods.length],
            ["images", "Ảnh", v.images.length],
            ["packages", "Gói bán", state.packages.length],
        ].map(([key, label, count]) => `<button type="button" data-tab="${key}" class="${state.tab === key ? "active" : ""}">${label}${count !== undefined ? `<span class="count">${count}</span>` : ""}</button>`).join("");

        const missing = ATTRS.map((a) => `<span class="${v[a.key] ? "ok" : "no"}">${v[a.key] ? "✓" : "✗"} ${a.label}</span>`).join("");
        const complete = ATTRS.every((a) => v[a.key]);
        return `
          <div class="panel" id="variantPanel">
            <div class="section-head">
              <div>
                <div class="variant-title">${esc(v.variantName)} ${badge(v.status)}</div>
                <div class="meta muted" style="margin-top:6px"><span class="chip-code">${esc(v.variantCode)}</span>
                  · thuộc ${esc(v.productName)}</div>
              </div>
              <div class="actions">
                <button class="btn btn-sm" data-act="edit-variant">Sửa</button>
                ${v.status === "DRAFT" ? '<button class="btn btn-sm" data-act="complete-variant">Hoàn thiện nháp</button>' : ""}
                ${v.status !== "ACTIVE" ? `<button class="btn btn-sm btn-success" data-act="activate-variant" ${complete ? "" : "disabled title='Cần đủ 4 thuộc tính'"}>Kích hoạt</button>` : ""}
                ${v.status !== "INACTIVE" ? '<button class="btn btn-sm btn-danger" data-act="deactivate-variant">Ngừng kinh doanh</button>' : ""}
                <button class="btn btn-sm btn-danger" data-act="delete-variant">Xóa</button>
              </div>
            </div>
            ${v.status === "DRAFT" ? `<div class="draft-hint"><b>Biến thể nháp</b>${missing}</div>` : ""}
            <div class="tabs">${tabs}</div>
            <div id="tabBody">${renderTab()}</div>
          </div>`;
    }

    function renderTab() {
        switch (state.tab) {
            case "flavor": return renderFlavorTab();
            case "brew": return renderBrewTab();
            case "images": return renderImageTab();
            case "packages": return renderPackageTab();
            default: return renderInfoTab();
        }
    }

    function renderInfoTab() {
        const v = state.variant;
        return `
          <div class="attr-grid">
            ${ATTRS.map((a) => `
              <div class="attr-card">
                <label>${a.label}<span class="uc">chọn để gán</span></label>
                <select data-assign="${a.field}">${options(state.lookups[a.list], v[a.key], "— Chưa gán —")}</select>
              </div>`).join("")}
          </div>
          <div class="info-list">
            <div><span>Độ cao</span><b>${v.altitudeM != null ? esc(v.altitudeM) + " m" : "—"}</b></div>
            <div><span>Mùa vụ</span><b>${esc(v.harvestSeason || "—")}</b></div>
            <div><span>Thứ tự hiển thị</span><b>${esc(v.displayOrder)}</b></div>
            <div><span>Ngày tạo</span><b>${dateTime(v.createdAt)}</b></div>
          </div>`;
    }

    function renderFlavorTab() {
        const v = state.variant;
        const selected = new Set(v.flavorNotes.map((n) => n.flavorNoteId));
        const groups = {};
        (state.lookups.flavorNotes || []).forEach((n) => {
            (groups[n.detail] = groups[n.detail] || []).push(n);
        });
        return `
          <form id="flavorForm">
            <div class="flavor-layout">
              <div>
                <h4 class="muted" style="margin-bottom:12px">ĐIỂM CẢM QUAN (1 – 10)</h4>
                ${SCORE_FIELDS.map(([key, label]) => `
                  <div class="score-row ${v[key] == null ? "unset" : ""}">
                    <span>${label}</span>
                    <input type="range" min="1" max="10" step="1" name="${key}" value="${v[key] ?? 5}" data-set="${v[key] == null ? "0" : "1"}">
                    <span class="val">${v[key] ?? "—"}</span>
                  </div>`).join("")}
                <p class="muted">Kéo thanh trượt để chấm; "—" là chưa chấm.</p>
              </div>
              <div>
                <h4 class="muted" style="margin-bottom:12px">NỐT HƯƠNG</h4>
                ${Object.keys(groups).length ? Object.entries(groups).map(([cat, notes]) => `
                  <div class="note-group"><h4>${esc(cat)}</h4><div class="chips">
                    ${notes.map((n) => `<button type="button" class="chip ${selected.has(n.id) ? "on" : ""}" data-note="${n.id}">${esc(n.name)}</button>`).join("")}
                  </div></div>`).join("") : '<p class="muted">Chưa có nốt hương trong danh mục.</p>'}
              </div>
            </div>
            <div class="form-foot"><button type="submit" class="btn btn-primary">Lưu hồ sơ hương vị</button></div>
          </form>`;
    }

    function renderBrewTab() {
        const v = state.variant;
        const current = new Map(v.brewMethods.map((b) => [b.brewMethodId, b.note]));
        const methods = state.lookups.brewMethods || [];
        return `
          <form id="brewForm">
            ${methods.length ? methods.map((m) => `
              <div class="brew-row">
                <input type="checkbox" name="brewMethodId" value="${m.id}" ${current.has(m.id) ? "checked" : ""}>
                <div class="bname"><b>${esc(m.name)}</b><span>${esc(m.detail || "")}</span></div>
                <input type="text" name="note_${m.id}" maxlength="500" placeholder="Ghi chú gợi ý (liều, nhiệt độ, thời gian...)"
                       value="${esc(current.get(m.id) || "")}" ${current.has(m.id) ? "" : "disabled"}>
              </div>`).join("") : '<p class="muted">Chưa có phương pháp pha trong danh mục.</p>'}
            <div class="form-foot"><button type="submit" class="btn btn-primary">Lưu cách pha gợi ý</button></div>
          </form>`;
    }

    function renderImageTab() {
        const v = state.variant;
        return `
          <div class="image-grid">
            ${v.images.map((img, i) => `
              <div class="image-tile">
                <img src="${esc(img.imageUrl)}" alt="Ảnh ${i + 1} của ${esc(v.variantName)}" loading="lazy">
                <span class="order">${i === 0 ? "Ảnh bìa" : "#" + (i + 1)}</span>
                <button class="del" data-image="${img.imageId}" title="Xóa ảnh">×</button>
              </div>`).join("")}
            <label class="upload-tile">
              <span class="plus">+</span><span>Tải ảnh lên</span><span class="muted">JPG, PNG, WEBP ≤ 5 MB</span>
              <input type="file" id="imageInput" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden>
            </label>
          </div>`;
    }

    function renderPackageTab() {
        const canAdd = state.variant.status !== "INACTIVE";
        const rows = state.packages.map((p) => `
            <tr>
              <td><span class="chip-code">${esc(p.sku)}</span></td>
              <td class="nowrap">${esc(p.grindName)}</td>
              <td class="num">${esc(p.weightG)} g</td>
              <td class="num"><b>${money(p.salePrice)}</b></td>
              <td class="num nowrap">${p.lowStock ? '<span class="badge badge-LOW">thấp</span> ' : ""}<b>${esc(p.quantityOnHand)}</b><span class="muted"> / ${esc(p.minStockLevel)}</span></td>
              <td>${badge(p.status)}</td>
              <td><div class="row-actions">
                <button class="btn-link" data-pkg="price" data-id="${p.packageId}">Đổi giá</button>
                <button class="btn-link" data-pkg="history" data-id="${p.packageId}">Lịch sử</button>
                <button class="btn-link" data-pkg="edit" data-id="${p.packageId}">Sửa</button>
                <button class="btn-link ${p.status === "ACTIVE" ? "danger" : "success"}" data-pkg="toggle" data-id="${p.packageId}">${p.status === "ACTIVE" ? "Ngừng bán" : "Mở bán"}</button>
                <button class="btn-link danger" data-pkg="delete" data-id="${p.packageId}">Xóa</button>
              </div></td>
            </tr>`).join("");
        return `
          <div class="section-head">
            <span class="muted">Mỗi gói bán là một SKU: trọng lượng + dạng hạt. Giá đổi theo lịch sử, không ghi đè.</span>
            <button class="btn btn-primary btn-sm" data-act="add-package" ${canAdd ? "" : "disabled"}>+ Thêm gói bán</button>
          </div>
          ${state.packages.length ? `
            <div class="table-wrap"><table class="grid compact">
              <thead><tr><th>SKU</th><th>Dạng hạt</th><th class="num">Trọng lượng</th><th class="num">Giá hiện hành</th><th class="num" title="Tồn hiện tại / tồn tối thiểu">Tồn / tối thiểu</th><th>Trạng thái</th><th></th></tr></thead>
              <tbody>${rows}</tbody></table></div>`
            : '<div class="empty">Biến thể chưa có gói bán.</div>'}`;
    }

    // ------------------------------------------------------------------ sự kiện

    function bindMain() {
        const main = $("#mainPanel");
        const actions = {
            "edit-product": () => productForm(state.product),
            "toggle-product": toggleProduct,
            "delete-product": deleteProduct,
            "add-variant": variantCreateForm,
            "edit-variant": variantEditForm,
            "complete-variant": completeDraftForm,
            "activate-variant": activateVariant,
            "deactivate-variant": deactivateVariant,
            "delete-variant": deleteVariant,
            "add-package": () => packageForm(null),
        };
        $$("[data-act]", main).forEach((el) => {
            el.onclick = () => actions[el.dataset.act]();
        });
        $$("[data-vstatus]", main).forEach((el) => {
            el.onclick = () => {
                state.variantStatus = el.dataset.vstatus;
                renderMain();
            };
        });
        $$("tr[data-variant]", main).forEach((el) => {
            el.onclick = () => selectVariant(Number(el.dataset.variant));
        });
        $$("[data-tab]", main).forEach((el) => {
            el.onclick = () => {
                state.tab = el.dataset.tab;
                renderMain();
            };
        });
        bindTab();
    }

    function bindTab() {
        const body = $("#tabBody");
        if (!body) return;

        $$("select[data-assign]", body).forEach((select) => {
            select.onchange = () => {
                const attr = ATTRS.find((a) => a.field === select.dataset.assign);
                assignAttribute(attr, select.value, select);
            };
        });

        const flavorForm = $("#flavorForm", body);
        if (flavorForm) {
            $$("input[type=range]", flavorForm).forEach((range) => {
                range.oninput = () => {
                    range.dataset.set = "1";
                    range.closest(".score-row").classList.remove("unset");
                    range.nextElementSibling.textContent = range.value;
                };
            });
            $$(".chip", flavorForm).forEach((chip) => {
                chip.onclick = () => chip.classList.toggle("on");
            });
            flavorForm.onsubmit = (e) => {
                e.preventDefault();
                const fields = { id: state.variantId };
                $$("input[type=range]", flavorForm).forEach((range) => {
                    if (range.dataset.set === "1") fields[range.name] = range.value;
                });
                fields.flavorNoteId = $$(".chip.on", flavorForm).map((c) => c.dataset.note);
                run(async () => {
                    const result = await post("variants", "flavorProfile", fields);
                    await loadVariant();
                    return result;
                });
            };
        }

        const brewForm = $("#brewForm", body);
        if (brewForm) {
            $$("input[type=checkbox]", brewForm).forEach((box) => {
                box.onchange = () => {
                    const note = $(`[name=note_${box.value}]`, brewForm);
                    note.disabled = !box.checked;
                    if (box.checked) note.focus();
                };
            });
            brewForm.onsubmit = (e) => {
                e.preventDefault();
                const fields = formToObject(new FormData(brewForm));
                fields.id = state.variantId;
                fields.brewMethodId = [].concat(fields.brewMethodId || []);
                run(async () => {
                    const result = await post("variants", "brewMethods", fields);
                    await loadVariant();
                    return result;
                });
            };
        }

        const imageInput = $("#imageInput", body);
        if (imageInput) {
            imageInput.onchange = () => uploadImages(Array.from(imageInput.files));
        }
        $$("[data-image]", body).forEach((btn) => {
            btn.onclick = () => deleteImage(state.variant.images.find((i) => i.imageId === Number(btn.dataset.image)));
        });

        $$("[data-pkg]", body).forEach((btn) => {
            const pkg = state.packages.find((p) => p.packageId === Number(btn.dataset.id));
            btn.onclick = () => ({
                price: () => priceForm(pkg),
                history: () => priceHistory(pkg),
                edit: () => packageForm(pkg),
                toggle: () => togglePackage(pkg),
                delete: () => deletePackage(pkg),
            })[btn.dataset.pkg]();
        });
    }

    function updateHash() {
        const parts = [];
        if (state.productId) parts.push("p=" + state.productId);
        if (state.variantId) parts.push("v=" + state.variantId);
        history.replaceState(null, "", parts.length ? "#" + parts.join("&") : location.pathname + location.search);
    }

    async function init() {
        $("#btnAddProduct").onclick = () => productForm(null);
        $("#searchInput").addEventListener("input", debounce((e) => {
            state.keyword = e.target.value.trim();
            loadProducts();
        }, 250));
        $$("#productStatusFilter button").forEach((btn) => {
            btn.onclick = () => {
                $$("#productStatusFilter button").forEach((b) => b.classList.toggle("active", b === btn));
                state.productStatus = btn.dataset.status;
                loadProducts();
            };
        });
        document.addEventListener("keydown", (e) => {
            if (e.key === "Escape") $("#modalRoot").innerHTML = "";
        });

        renderMain();
        try {
            state.lookups = await api("product-lookups");
        } catch (e) {
            toast("Không tải được danh mục: " + e.message, "error");
            state.lookups = {};
        }
        await loadProducts();

        const hash = new URLSearchParams(location.hash.slice(1));
        const productId = Number(hash.get("p"));
        if (productId) {
            state.variantId = Number(hash.get("v")) || null;
            await selectProduct(productId, true);
        }
    }

    init();
})();
