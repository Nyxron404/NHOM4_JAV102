// --- BÁO CÁO & THỐNG KÊ (UC: báo cáo theo ngày/khoảng/nhóm) ---
const REPORT_API = '../../api/reports';
let currentEndpoint = 'revenue-day';
let currentTitle = 'Doanh thu theo ngày';
let currentRows = [];

document.addEventListener('DOMContentLoaded', () => {
    const from = document.getElementById('rFrom');
    const to = document.getElementById('rTo');

    // Giao diện đầy đủ có bộ lọc ngày; nếu không có thì dùng giao diện tổng quan ngắn.
    if (from && to) {
        const today = new Date();
        from.value = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10);
        to.value = today.toISOString().slice(0, 10);
        refreshAllReports();
    } else {
        loadSimpleReport();
    }
});

async function refreshAllReports() {
    await loadSummary();
    if (document.getElementById('reportTable')) {
        await loadReportTab(currentEndpoint, currentTitle);
    }
}
window.refreshAllReports = refreshAllReports;

async function loadSummary() {
    try {
        const data = await fetchJson('/summary' + getDateQuery());
        setText('kpiToday', fmtMoney(data.incomeToday));
        setText('kpiRevPeriod', fmtMoney(data.revenuePeriod));
        setText('kpiExpLoss', fmtMoney((data.expensePeriod || 0) + (data.disposalLoss || 0)));
        setText('kpiProfit', fmtMoney(data.profit));
        setText('kpiIncomeToday', fmtMoney(data.incomeToday));
        setText('kpiPurchaseMonth', fmtMoney(data.expensePeriod));
        setText('kpiNearExpiry', `${data.nearExpiryCount || 0} lô`);
        setText('badgeNearExp', data.nearExpiryCount || 0);
    } catch (error) {
        console.error('Không tải được tổng quan báo cáo:', error);
    }
}

async function loadSimpleReport() {
    await loadSummary();

    try {
        const regions = await fetchJson('/by-region');
        renderTwoColumnTable('regionTableBody', regions);

        const nearExpiry = await fetchJson('/near-expiry');
        const expiryBody = document.getElementById('nearExpiryTableBody');
        if (expiryBody) {
            expiryBody.innerHTML = nearExpiry.length
                ? nearExpiry.map(row => `<tr>
                    <td>${escapeHtml(row.lotCode)}</td>
                    <td>${escapeHtml(row.sku)} — ${escapeHtml(row.variantName)}</td>
                    <td>${escapeHtml(row.expiryDate)}</td><td>${row.qty}</td>
                  </tr>`).join('')
                : '<tr><td colspan="4">Không có hàng cận hạn</td></tr>';
        }
    } catch (error) {
        console.error('Không tải được bảng báo cáo:', error);
    }
}

function renderTwoColumnTable(bodyId, rows) {
    const body = document.getElementById(bodyId);
    if (!body) return;
    body.innerHTML = rows.length
        ? rows.map(row => `<tr><td>${escapeHtml(row.label)}</td><td>${fmtMoney(row.value)}</td></tr>`).join('')
        : '<tr><td colspan="2">Chưa có dữ liệu</td></tr>';
}

async function loadReportTab(endpoint, title, btn) {
    currentEndpoint = endpoint;
    currentTitle = title;

    if (btn) {
        document.querySelectorAll('.tab-btn').forEach(item => item.classList.remove('active'));
        btn.classList.add('active');
    }

    setText('reportTableTitle', title);

    try {
        const query = endpoint === 'revenue-month' ? '' : getDateQuery();
        currentRows = await fetchJson(`/${endpoint}${query}`);
        renderReportTable(endpoint, currentRows);
    } catch (error) {
        console.error(`Không tải được báo cáo ${title}:`, error);
        setTableMessage('Không thể tải dữ liệu báo cáo');
    }
}
window.loadReportTab = loadReportTab;

function renderReportTable(endpoint, rows) {
    const thead = document.getElementById('reportThead');
    const tbody = document.getElementById('reportTbody');
    if (!thead || !tbody) return;

    if (endpoint === 'best-selling') {
        thead.innerHTML = '<tr><th>SKU</th><th>Tên biến thể</th><th>Số lượng bán</th><th>Doanh thu</th></tr>';
        tbody.innerHTML = rows.map(row => `<tr><td>${escapeHtml(row.sku)}</td><td>${escapeHtml(row.name)}</td><td>${row.qty}</td><td>${fmtMoney(row.revenue)}</td></tr>`).join('');
    } else if (endpoint === 'inventory') {
        thead.innerHTML = '<tr><th>SKU</th><th>Tên biến thể</th><th>Tồn hiện tại</th><th>Mức tối thiểu</th><th>Cảnh báo</th></tr>';
        tbody.innerHTML = rows.map(row => `<tr><td>${escapeHtml(row.sku)}</td><td>${escapeHtml(row.variantName)}</td><td>${row.qty}</td><td>${row.minStock}</td><td>${row.lowStock ? 'Tồn thấp' : 'Ổn định'}</td></tr>`).join('');
    } else if (endpoint === 'near-expiry') {
        thead.innerHTML = '<tr><th>Mã lô</th><th>SKU</th><th>Tên biến thể</th><th>Hạn sử dụng</th><th>Tồn</th></tr>';
        tbody.innerHTML = rows.map(row => `<tr><td>${escapeHtml(row.lotCode)}</td><td>${escapeHtml(row.sku)}</td><td>${escapeHtml(row.variantName)}</td><td>${escapeHtml(row.expiryDate)}</td><td>${row.qty}</td></tr>`).join('');
    } else {
        thead.innerHTML = '<tr><th>Hạng mục / Thời gian</th><th>Số tiền (đ)</th></tr>';
        tbody.innerHTML = rows.map(row => `<tr><td>${escapeHtml(row.label)}</td><td>${fmtMoney(row.value)}</td></tr>`).join('');
    }

    if (!rows.length) tbody.innerHTML = '<tr><td colspan="5">Không có dữ liệu trong khoảng thời gian này</td></tr>';
}

function exportExcel() {
    if (!window.XLSX) {
        alert('Không tải được thư viện XLSX. Kiểm tra kết nối Internet rồi thử lại.');
        return;
    }
    const table = document.getElementById('reportTable');
    if (!table) {
        alert('Giao diện hiện tại chưa hỗ trợ bảng xuất Excel chi tiết.');
        return;
    }
    const workbook = XLSX.utils.table_to_book(table, { sheet: 'BaoCao' });
    XLSX.writeFile(workbook, `${currentEndpoint}_${new Date().toISOString().slice(0, 10)}.xlsx`);
}
window.exportExcel = exportExcel;

async function fetchJson(path) {
    const response = await fetch(`${REPORT_API}${path}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
}

function getDateQuery() {
    const from = document.getElementById('rFrom')?.value || '';
    const to = document.getElementById('rTo')?.value || '';
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    return params.size ? `?${params}` : '';
}

function setText(id, text) {
    const element = document.getElementById(id);
    if (element) element.textContent = text;
}

function setTableMessage(message) {
    const tbody = document.getElementById('reportTbody');
    if (tbody) tbody.innerHTML = `<tr><td colspan="5">${escapeHtml(message)}</td></tr>`;
}

function fmtMoney(value) {
    return Number(value || 0).toLocaleString('vi-VN') + ' đ';
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}