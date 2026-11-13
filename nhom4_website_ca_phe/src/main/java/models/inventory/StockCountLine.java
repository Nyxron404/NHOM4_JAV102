package models.inventory;

/**
 * Chi tiết kiểm kê theo lô
 * <p>Bảng: stock_count_lines</p>
 */
public class StockCountLine {

    public static final String TABLE = "stock_count_lines";
    public static final String[] PRIMARY_KEY = {"count_line_id"};

    /** count_line_id | BIGINT | PK | tự tăng */
    private Long countLineId;
    /** stock_count_id | BIGINT | FK → stock_counts.stock_count_id | NOT NULL */
    private Long stockCountId;
    /** lot_id | BIGINT | FK → lots.lot_id | NOT NULL */
    private Long lotId;
    /** system_qty | INT | NOT NULL */
    private Integer systemQty;
    /** counted_qty | INT | NOT NULL */
    private Integer countedQty;
    /** note | d_note */
    private String note;

    public StockCountLine() {}

    public Long getCountLineId() { return countLineId; }
    public void setCountLineId(Long countLineId) { this.countLineId = countLineId; }
    public Long getStockCountId() { return stockCountId; }
    public void setStockCountId(Long stockCountId) { this.stockCountId = stockCountId; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Integer getSystemQty() { return systemQty; }
    public void setSystemQty(Integer systemQty) { this.systemQty = systemQty; }
    public Integer getCountedQty() { return countedQty; }
    public void setCountedQty(Integer countedQty) { this.countedQty = countedQty; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "StockCountLine{" + "countLineId=" + countLineId + ", stockCountId=" + stockCountId + ", lotId=" + lotId + ", systemQty=" + systemQty + ", countedQty=" + countedQty + ", note=" + note + "}";
    }
}
