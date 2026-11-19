package models.hieuttth08297;

import java.util.List;

public final class TrangSanPham {

    private TrangSanPham() {}

    public record SanPham(String productCode, String productName, String description, boolean active) {}

    public record VungTrong(String regionName, String provinceName, Integer altitudeMinM,
                            Integer altitudeMaxM, String soil, String climate, String description) {}

    public record Giong(String varietyName, String speciesName, String description) {}

    public record TenMoTa(String name, String description) {}

    public record DiemHuongVi(Integer aroma, Integer acidity, Integer sweetness,
                              Integer bitterness, Integer body, Integer aftertaste) {}

    public record NotHuong(String noteName, String category) {}

    public record CachPha(String methodName, String coffeeWaterRatio, String waterTempC,
                          String brewTime, String guide, String note) {}

    public record BienThe(int variantId, String variantCode, String variantName,
                          Integer altitudeM, String harvestSeason,
                          VungTrong region, Giong variety, TenMoTa process, TenMoTa roast,
                          DiemHuongVi scores, List<NotHuong> flavorNotes,
                          List<CachPha> brewMethods, List<String> images) {}

    public record Trang(SanPham product, Integer defaultVariantId, List<BienThe> variants) {}
}