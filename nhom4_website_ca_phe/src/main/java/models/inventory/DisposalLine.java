package models.inventory;

/**
 * Chi tiết xuất hủy theo lô
 * <p>Bảng: disposal_lines</p>
 */
public class DisposalLine {

    public static final String TABLE = "disposal_lines";
    public static final String[] PRIMARY_KEY = {"disposal_line_id"};

    /** disposal_line_id | BIGINT | PK | tự tăng */
    private Long disposalLineId;
    /** disposal_id | BIGINT | FK → disposals.disposal_id | NOT NULL */
    private Long disposalId;
    /** lot_id | BIGINT | FK → lots.lot_id | NOT NULL */
    private Long lotId;
    /** quantity | INT | NOT NULL */
    private Integer quantity;

    public DisposalLine() {}

    public Long getDisposalLineId() { return disposalLineId; }
    public void setDisposalLineId(Long disposalLineId) { this.disposalLineId = disposalLineId; }
    public Long getDisposalId() { return disposalId; }
    public void setDisposalId(Long disposalId) { this.disposalId = disposalId; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "DisposalLine{" + "disposalLineId=" + disposalLineId + ", disposalId=" + disposalId + ", lotId=" + lotId + ", quantity=" + quantity + "}";
    }
}
