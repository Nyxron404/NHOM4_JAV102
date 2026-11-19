const API_BASE = '../../api/purchase-invoices';
let currentDetailId = null;
let currentDetailSupplierId = null;
let currentDetailStatus = 'DRAFT';
let html5QrScanner = null;

document.addEventListener('DOMContentLoaded', async () => {
    document.getElementById('cInvoiceDate').valueAsDate = new Date();
    await loadLookups();
    await loadInvoices();
});

function switchTab(panelId, btn) {
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById(panelId).classList.add('active');
    btn.classList.add('active');
    if (panelId === 'tab-lots') loadLots();
    if (panelId === 'tab-mappings') loadMappings();
}

async function loadLookups() {
    const res = await fetch(`${API_BASE}/lookups`);
    const data = await res.json();
    const supOpts = data.suppliers.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
    document.getElementById('filterSupplier').innerHTML = `<option value="ALL">Tất cả Nhà cung cấp</option>` + supOpts;
    document.getElementById('cSupplierId').innerHTML = supOpts;
    document.getElementById('mapSupplierId').innerHTML = supOpts;

    const pkgOpts = data.packages.map(p => `<option value="${p.id}">${p.label}</option>`).join('');
    document.getElementById('linePackageId').innerHTML = pkgOpts;
    document.getElementById('mapPackageId').innerHTML = pkgOpts;
}

async function loadInvoices() {
    const params = new URLSearchParams({
        status: document.getElementById('filterStatus').value,
        q: document.getElementById('qSearch').value,
        supplierId: document.getElementById('filterSupplier').value,
        fromDate: document.getElementById('filterFrom').value,
        toDate: document.getElementById('filterTo').value
    });
    const res = await fetch(`${API_BASE}?${params}`);
    const data = await res.json();
    document.getElementById('invoiceTableBody').innerHTML = data.map(inv => `
        <tr>
            <td><strong>${inv.invoiceNo}</strong></td>
            <td>${inv.lookupCode || '-'}</td>
            <td>${inv.supplierName}</td>
            <td>${inv.invoiceDate}</td>
            <td>${inv.inputMethod}</td>
            <td>${Number(inv.totalAmount).toLocaleString('vi-VN')} đ</td>
            <td><span class="badge badge-${inv.status}">${inv.status}</span></td>
            <td>
                <button class="btn" onclick="openDetailModal(${inv.id}, ${inv.supplierId}, '${inv.status}')">Chi tiết</button>
                ${inv.status === 'DRAFT' ? `
                    <button class="btn btn-success" onclick="confirmInvoice(${inv.id})">Xác nhận</button>
                    <button class="btn btn-danger" onclick="cancelInvoice(${inv.id})">Hủy</button>
                ` : ''}
            </td>
        </tr>
    `).join('');
}

// --- QUÉT QR ---
function openQrModal() {
    document.getElementById('qrModal').style.display = 'flex';
    document.getElementById('qrResultText').innerText = 'Đang mở camera...';
    html5QrScanner = new Html5Qrcode("qrReader");
    html5QrScanner.start(
        { facingMode: "environment" },
        { fps: 10, qrbox: 250 },
        (decodedText) => {
            document.getElementById('qrResultText').innerText = 'Đã quét: ' + decodedText;
            closeQrModal();
            openCreateModal();
            document.getElementById('cInputMethod').value = 'QR';
            const parts = decodedText.split('|');
            document.getElementById('cInvoiceNo').value = parts[0] || decodedText;
            if (parts[1]) document.getElementById('cLookupCode').value = parts[1];
            previewInvoice();
        }
    ).catch(err => {
        document.getElementById('qrResultText').innerText = 'Không truy cập được camera: ' + err;
    });
}

function closeQrModal() {
    if (html5QrScanner) {
        html5QrScanner.stop().catch(() => {});
        html5QrScanner = null;
    }
    document.getElementById('qrModal').style.display = 'none';
}

// --- TẠO PHIẾU, KIỂM TRA TRÙNG, XEM TRƯỚC ---
function openCreateModal() { document.getElementById('createModal').style.display = 'flex'; }
function closeModal(id) { document.getElementById(id).style.display = 'none'; }

async function checkDuplicate() {
    const supplierId = document.getElementById('cSupplierId').value;
    const invoiceNo = document.getElementById('cInvoiceNo').value;
    if (!invoiceNo) return alert('Vui lòng nhập số hóa đơn!');
    const res = await fetch(`${API_BASE}/check-duplicate?supplierId=${supplierId}&invoiceNo=${encodeURIComponent(invoiceNo)}`);
    const data = await res.json();
    alert(data.duplicate ? '⚠️ CẢNH BÁO: Số hóa đơn này ĐÃ TỒN TẠI!' : '✅ Hợp lệ: Chưa có hóa đơn trùng.');
}

function previewInvoice() {
    const sup = document.getElementById('cSupplierId').selectedOptions[0]?.text || '';
    const inv = document.getElementById('cInvoiceNo').value;
    const lkp = document.getElementById('cLookupCode').value;
    const dt = document.getElementById('cInvoiceDate').value;
    const mth = document.getElementById('cInputMethod').value;
    document.getElementById('previewContent').innerText =
        `NCC: ${sup} | Số HĐ: ${inv} | Mã tra cứu: ${lkp || 'Không'} | Ngày: ${dt} | Phương thức: ${mth}`;
}

async function submitCreateInvoice() {
    const params = new URLSearchParams({
        supplierId: document.getElementById('cSupplierId').value,
        invoiceNo: document.getElementById('cInvoiceNo').value,
        lookupCode: document.getElementById('cLookupCode').value,
        invoiceDate: document.getElementById('cInvoiceDate').value,
        inputMethod: document.getElementById('cInputMethod').value
    });
    const res = await fetch(`${API_BASE}/create`, { method: 'POST', body: params });
    const data = await res.json();
    if (data.success) {
        closeModal('createModal');
        await loadInvoices();
        openDetailModal(data.id, document.getElementById('cSupplierId').value, 'DRAFT');
    } else {
        alert('Lỗi: ' + data.error);
    }
}

// --- CHI TIẾT PHIẾU: AUTO-MATCH, TẠO BIẾN THỂ NHÁP, THÊM/SỬA/XÓA DÒNG ---
async function openDetailModal(id, supplierId, status) {
    currentDetailId = id;
    currentDetailSupplierId = supplierId;
    currentDetailStatus = status;
    document.getElementById('detailInvoiceId').innerText = id;
    document.getElementById('draftTools').style.display = (status === 'DRAFT') ? 'block' : 'none';
    document.getElementById('detailModal').style.display = 'flex';
    await loadInvoiceLines();
}

async function autoMatchLine() {
    const code = document.getElementById('lineSupCode').value;
    if (!code) return alert('Nhập mã hàng NCC trước!');
    const res = await fetch(`${API_BASE}/auto-match?supplierId=${currentDetailSupplierId}&itemCode=${encodeURIComponent(code)}`);
    const data = await res.json();
    if (data.matched) {
        document.getElementById('linePackageId').value = data.packageId;
        alert(`✅ Đã khớp tự động với SKU: ${data.sku} (${data.variantName})`);
    } else {
        alert('Chưa có ánh xạ cho mã này. Bạn có thể chọn SKU thủ công hoặc bấm "Tạo biến thể nháp".');
    }
}

async function createDraftVariant() {
    const itemName = document.getElementById('lineSupName').value;
    const weightG = document.getElementById('draftWeightG').value;
    if (!itemName) return alert('Nhập tên hàng NCC để tạo biến thể nháp!');
    const res = await fetch(`${API_BASE}/create-draft-variant`, {
        method: 'POST',
        body: new URLSearchParams({ itemName, weightG })
    });
    const data = await res.json();
    if (data.success) {
        await loadLookups();
        document.getElementById('linePackageId').value = data.packageId;
        alert('✅ Đã tạo biến thể nháp (DRAFT) và chọn sẵn!');
    } else {
        alert('Lỗi: ' + data.error);
    }
}

async function loadInvoiceLines() {
    const res = await fetch(`${API_BASE}/detail?id=${currentDetailId}`);
    const lines = await res.json();
    document.getElementById('detailLinesBody').innerHTML = lines.map(l => `
        <tr>
            <td>${l.sku}</td>
            <td>${l.variantName}</td>
            <td>${l.lotCode}</td>
            <td><input type="date" id="r_${l.lineId}" value="${l.roastDate}" ${currentDetailStatus !== 'DRAFT' ? 'disabled' : ''}></td>
            <td><input type="date" id="e_${l.lineId}" value="${l.expiryDate}" ${currentDetailStatus !== 'DRAFT' ? 'disabled' : ''}></td>
            <td><input type="number" id="q_${l.lineId}" value="${l.quantity}" style="width:70px;" ${currentDetailStatus !== 'DRAFT' ? 'disabled' : ''}></td>
            <td><input type="number" id="c_${l.lineId}" value="${l.unitCost}" style="width:100px;" ${currentDetailStatus !== 'DRAFT' ? 'disabled' : ''}></td>
            <td>
                ${currentDetailStatus === 'DRAFT' ? `
                    <button class="btn btn-primary" onclick="updateLine(${l.lineId})">Lưu</button>
                    <button class="btn btn-danger" onclick="deleteLine(${l.lineId})">Xóa</button>
                ` : '-'}
            </td>
        </tr>
    `).join('');
}

async function submitAddLine() {
    const params = new URLSearchParams({
        purchaseInvoiceId: currentDetailId,
        packageId: document.getElementById('linePackageId').value,
        supplierItemCode: document.getElementById('lineSupCode').value,
        supplierItemName: document.getElementById('lineSupName').value,
        lotCode: document.getElementById('lineLotCode').value,
        quantity: document.getElementById('lineQty').value,
        unitCost: document.getElementById('lineUnitCost').value,
        roastDate: document.getElementById('lineRoastDate').value,
        expiryDate: document.getElementById('lineExpiryDate').value
    });
    const res = await fetch(`${API_BASE}/add-line`, { method: 'POST', body: params });
    const data = await res.json();
    if (data.success) {
        await loadInvoiceLines();
        loadInvoices();
    } else alert('Lỗi: ' + data.error);
}

async function updateLine(lineId) {
    const params = new URLSearchParams({
        lineId,
        quantity: document.getElementById(`q_${lineId}`).value,
        unitCost: document.getElementById(`c_${lineId}`).value,
        roastDate: document.getElementById(`r_${lineId}`).value,
        expiryDate: document.getElementById(`e_${lineId}`).value
    });
    const res = await fetch(`${API_BASE}/update-line`, { method: 'POST', body: params });
    const data = await res.json();
    if (data.success) { alert('Đã cập nhật dòng hàng!'); loadInvoices(); }
    else alert('Lỗi: ' + data.error);
}

async function deleteLine(lineId) {
    if (!confirm('Xóa dòng hàng này?')) return;
    const res = await fetch(`${API_BASE}/delete-line`, { method: 'POST', body: new URLSearchParams({ lineId }) });
    const data = await res.json();
    if (data.success) { await loadInvoiceLines(); loadInvoices(); }
    else alert('Lỗi: ' + data.error);
}

async function confirmInvoice(id) {
    if (!confirm('Xác nhận nhập kho phiếu này?')) return;
    const res = await fetch(`${API_BASE}/confirm`, { method: 'POST', body: new URLSearchParams({ id }) });
    const data = await res.json();
    if (data.success) { alert(data.message); loadInvoices(); }
    else alert('Lỗi: ' + data.error);
}

async function cancelInvoice(id) {
    if (!confirm('Hủy phiên nhập này?')) return;
    const res = await fetch(`${API_BASE}/cancel`, { method: 'POST', body: new URLSearchParams({ id }) });
    const data = await res.json();
    if (data.success) loadInvoices();
    else alert('Lỗi: ' + data.error);
}

// --- TAB 2: QUẢN LÝ LÔ HÀNG ---
async function loadLots() {
    const res = await fetch(`${API_BASE}/lots`);
    const lots = await res.json();
    document.getElementById('lotTableBody').innerHTML = lots.map(l => `
        <tr>
            <td><strong>${l.lotCode}</strong></td>
            <td>${l.variantName} (${l.sku})</td>
            <td>${l.qty}</td>
            <td><input type="date" id="lot_r_${l.lotId}" value="${l.roastDate}"></td>
            <td><input type="date" id="lot_e_${l.lotId}" value="${l.expiryDate}"></td>
            <td>
                <button class="btn btn-primary" onclick="saveLotDates(${l.lotId})">Cập nhật ngày</button>
            </td>
        </tr>
    `).join('');
}

async function saveLotDates(lotId) {
    const roastDate = document.getElementById(`lot_r_${lotId}`).value;
    const expiryDate = document.getElementById(`lot_e_${lotId}`).value;
    await fetch(`${API_BASE}/lots/update-roast`, { method: 'POST', body: new URLSearchParams({ lotId, roastDate }) });
    const res = await fetch(`${API_BASE}/lots/update-expiry`, { method: 'POST', body: new URLSearchParams({ lotId, expiryDate }) });
    const data = await res.json();
    if (data.success) alert('✅ Đã cập nhật ngày rang & hạn sử dụng lô!');
    else alert('Lỗi: ' + data.error);
}

// --- TAB 3: ÁNH XẠ MÃ HÀNG NCC ---
async function loadMappings() {
    const res = await fetch(`${API_BASE}/mappings`);
    const rows = await res.json();
    document.getElementById('mappingTableBody').innerHTML = rows.map(m => `
        <tr>
            <td>${m.supplierName}</td>
            <td><strong>${m.supplierItemCode}</strong></td>
            <td>${m.sku} - ${m.variantName}</td>
            <td>
                <button class="btn" onclick="editMapping(${m.mappingId}, ${m.supplierId}, '${m.supplierItemCode}', ${m.packageId})">Sửa</button>
                <button class="btn btn-danger" onclick="deleteMapping(${m.mappingId})">Xóa</button>
            </td>
        </tr>
    `).join('');
}

function editMapping(id, supId, code, pkgId) {
    document.getElementById('editMappingId').value = id;
    document.getElementById('mapSupplierId').value = supId;
    document.getElementById('mapItemCode').value = code;
    document.getElementById('mapPackageId').value = pkgId;
}

function resetMappingForm() {
    document.getElementById('editMappingId').value = '';
    document.getElementById('mapItemCode').value = '';
}

async function saveMapping() {
    const params = new URLSearchParams({
        mappingId: document.getElementById('editMappingId').value,
        supplierId: document.getElementById('mapSupplierId').value,
        supplierItemCode: document.getElementById('mapItemCode').value,
        packageId: document.getElementById('mapPackageId').value
    });
    const res = await fetch(`${API_BASE}/mappings/save`, { method: 'POST', body: params });
    const data = await res.json();
    if (data.success) { resetMappingForm(); loadMappings(); }
    else alert('Lỗi: ' + data.error);
}

async function deleteMapping(mappingId) {
    if (!confirm('Xóa ánh xạ này?')) return;
    await fetch(`${API_BASE}/mappings/delete`, { method: 'POST', body: new URLSearchParams({ mappingId }) });
    loadMappings();
}