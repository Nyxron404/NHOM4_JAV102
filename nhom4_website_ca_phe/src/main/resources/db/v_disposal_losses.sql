-- Lỗ xuất hủy: chỉ tính các phiếu có lý do counts_as_loss = true
CREATE OR REPLACE VIEW v_disposal_losses AS
SELECT d.disposal_id,
       d.disposed_at,
       d.disposal_reason_id,
       SUM(dl.quantity::numeric * pil.unit_cost) AS loss_amount
FROM disposals d
JOIN disposal_reasons dr ON dr.disposal_reason_id = d.disposal_reason_id
JOIN disposal_lines dl ON dl.disposal_id = d.disposal_id
JOIN lots l ON l.lot_id = dl.lot_id
JOIN purchase_invoice_lines pil ON pil.purchase_line_id = l.purchase_line_id
WHERE dr.counts_as_loss
GROUP BY d.disposal_id;
