CREATE OR REPLACE VIEW v_purchase_invoice_totals AS
SELECT
    pi.purchase_invoice_id,
    pi.invoice_no,
    pi.status,
    COALESCE(SUM(pil.quantity * pil.unit_cost), 0) AS total_amount,
    pi.confirmed_at,
    pi.supplier_id
FROM purchase_invoices pi
LEFT JOIN purchase_invoice_lines pil
    ON pil.purchase_invoice_id = pi.purchase_invoice_id
GROUP BY
    pi.purchase_invoice_id,
    pi.invoice_no,
    pi.status,
    pi.confirmed_at,
    pi.supplier_id;