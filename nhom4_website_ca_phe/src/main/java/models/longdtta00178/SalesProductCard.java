package models.longdtta00178;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 thẻ cà phê ở màn bán hàng = 1 biến thể ACTIVE, bên trong là các gói bán của nó.
 * Không phải bảng DB: DAO gom các dòng cùng variant_id của câu SELECT thành 1 thẻ.
 */
public class SalesProductCard {

    private Integer variantId;
    private String variantName;
    private String productName;
    private String speciesName;
    private String regionName;
    private String processName;
    private String roastName;
    private String imageUrl;
    /** Nốt hương, cách nhau bằng dấu phẩy, VD "Cam, Hoa nhài, Mật ong". */
    private String flavorNotes;
    private List<SellablePackage> packages = new ArrayList<>();

    public SalesProductCard() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getRoastName() { return roastName; }
    public void setRoastName(String roastName) { this.roastName = roastName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getFlavorNotes() { return flavorNotes; }
    public void setFlavorNotes(String flavorNotes) { this.flavorNotes = flavorNotes; }
    public List<SellablePackage> getPackages() { return packages; }
    public void setPackages(List<SellablePackage> packages) { this.packages = packages; }
}
