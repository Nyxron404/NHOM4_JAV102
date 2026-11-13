package models.sales;

/**
 * Chi tiết trừ kho: dòng bán lấy từ lô nào (FEFO)
 * <p>Bảng: sales_line_allocations</p>
 */
public class SalesLineAllocation {

    public static final String TABLE = "sales_line_allocations";
    public static final String[] PRIMARY_KEY = {"allocation_id"};

    /** allocation_id | BIGINT | PK | tự tăng */
    private Long allocationId;
    /** sales_line_id | BIGINT | FK → sales_invoice_lines.sales_line_id | NOT NULL */
    private Long salesLineId;
    /** lot_id | BIGINT | FK → lots.lot_id | NOT NULL */
    private Long lotId;
    /** quantity | INT | NOT NULL */
    private Integer quantity;

    public SalesLineAllocation() {}

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getSalesLineId() { return salesLineId; }
    public void setSalesLineId(Long salesLineId) { this.salesLineId = salesLineId; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "SalesLineAllocation{" + "allocationId=" + allocationId + ", salesLineId=" + salesLineId + ", lotId=" + lotId + ", quantity=" + quantity + "}";
    }
}
