package models.inventory;

import java.time.OffsetDateTime;

import models.enums.MovementType;

/**
 * Sổ cái biến động tồn (append-only), tồn = SUM theo lô
 * <p>Bảng: stock_movements</p>
 */
public class StockMovement {

    public static final String TABLE = "stock_movements";
    public static final String[] PRIMARY_KEY = {"movement_id"};

    /** movement_id | BIGINT | PK | tự tăng */
    private Long movementId;
    /** lot_id | BIGINT | FK → lots.lot_id | NOT NULL */
    private Long lotId;
    /** movement_type | VARCHAR(16) | NOT NULL */
    private MovementType movementType;
    /** quantity_change | INT | NOT NULL */
    private Integer quantityChange;
    /** sales_invoice_id | BIGINT | FK → sales_invoices.sales_invoice_id */
    private Long salesInvoiceId;
    /** disposal_id | BIGINT | FK → disposals.disposal_id */
    private Long disposalId;
    /** stock_count_id | BIGINT | FK → stock_counts.stock_count_id */
    private Long stockCountId;
    /** performed_by | INT | FK → users.user_id */
    private Integer performedBy;
    /** occurred_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime occurredAt;

    public StockMovement() {}

    public Long getMovementId() { return movementId; }
    public void setMovementId(Long movementId) { this.movementId = movementId; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public MovementType getMovementType() { return movementType; }
    public void setMovementType(MovementType movementType) { this.movementType = movementType; }
    public Integer getQuantityChange() { return quantityChange; }
    public void setQuantityChange(Integer quantityChange) { this.quantityChange = quantityChange; }
    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public void setSalesInvoiceId(Long salesInvoiceId) { this.salesInvoiceId = salesInvoiceId; }
    public Long getDisposalId() { return disposalId; }
    public void setDisposalId(Long disposalId) { this.disposalId = disposalId; }
    public Long getStockCountId() { return stockCountId; }
    public void setStockCountId(Long stockCountId) { this.stockCountId = stockCountId; }
    public Integer getPerformedBy() { return performedBy; }
    public void setPerformedBy(Integer performedBy) { this.performedBy = performedBy; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }

    @Override
    public String toString() {
        return "StockMovement{" + "movementId=" + movementId + ", lotId=" + lotId + ", movementType=" + movementType + ", quantityChange=" + quantityChange + ", salesInvoiceId=" + salesInvoiceId + ", disposalId=" + disposalId + ", stockCountId=" + stockCountId + ", performedBy=" + performedBy + ", occurredAt=" + occurredAt + "}";
    }
}
