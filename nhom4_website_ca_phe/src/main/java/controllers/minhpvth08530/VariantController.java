package controllers.minhpvth08530;

import dao.minhpvth08530.ProductVariantDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.enums.VariantStatus;
import models.minhpvth08530.ProductVariant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API biến thể — UC59..UC71.
 * <pre>
 * GET  /api/variants?productId=&amp;status=   danh sách biến thể (UC59)
 * GET  /api/variants?id=                      chi tiết kèm cách pha, nốt hương, ảnh
 * POST /api/variants action=
 *      create (UC60) | update (UC61) | delete (UC62) | complete (UC63)
 *      activate (UC64) | deactivate (UC65)
 *      assign field=region|variety|process|roastLevel valueId= (UC66..UC69)
 *      brewMethods brewMethodId=..&amp;note_{id}=.. (UC70)
 *      flavorProfile aromaScore..aftertasteScore, flavorNoteId=.. (UC71)
 * </pre>
 */
@WebServlet("/api/variants")
public class VariantController extends BaseController {

    private final ProductVariantDAO variantDAO = new ProductVariantDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        Integer id = optionalInt(request, "id");
        if (id != null) {
            return found(variantDAO.findById(id), "biến thể");
        }
        return variantDAO.findByProduct(optionalInt(request, "productId"),
                enumValue(request, "status", VariantStatus.class));
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        if ("create".equals(action)) {
            ProductVariant variant = read(request);
            variant.setProductId(requiredInt(request, "productId", "sản phẩm"));
            variant.setRegionId(optionalInt(request, "regionId"));
            variant.setVarietyId(optionalInt(request, "varietyId"));
            variant.setProcessId(optionalInt(request, "processId"));
            variant.setRoastLevelId(optionalInt(request, "roastLevelId"));
            int id = variantDAO.insert(variant);
            ProductVariant created = variantDAO.findById(id);
            String message = created.getStatus() == VariantStatus.ACTIVE
                    ? "Đã thêm biến thể và mở bán"
                    : "Đã thêm biến thể ở trạng thái nháp";
            return ok(message, "id", id);
        }

        int id = requiredInt(request, "id", "mã biến thể");
        switch (action) {
            case "update" -> {
                ProductVariant variant = read(request);
                variant.setVariantId(id);
                found(variantDAO.update(variant), "biến thể");
                return ok("Đã cập nhật biến thể");
            }
            case "delete" -> {
                List<String> publicIds = found(variantDAO.delete(id), "biến thể");
                ImageStorage storage = new ImageStorage(getServletContext());
                publicIds.forEach(storage::delete);
                return ok("Đã xóa biến thể");
            }
            case "complete" -> {
                ProductVariant variant = read(request);
                variant.setVariantId(id);
                variant.setRegionId(optionalInt(request, "regionId"));
                variant.setVarietyId(optionalInt(request, "varietyId"));
                variant.setProcessId(optionalInt(request, "processId"));
                variant.setRoastLevelId(optionalInt(request, "roastLevelId"));
                variantDAO.completeDraft(variant);
                return ok("Đã hoàn thiện thông tin biến thể nháp");
            }
            case "activate" -> {
                found(variantDAO.activate(id), "biến thể");
                return ok("Đã kích hoạt biến thể");
            }
            case "deactivate" -> {
                found(variantDAO.deactivate(id), "biến thể đang kinh doanh");
                return ok("Đã ngừng kinh doanh biến thể và các gói bán của nó");
            }
            case "assign" -> {
                return assign(request, id);
            }
            case "brewMethods" -> {
                Map<Integer, String> notes = new LinkedHashMap<>();
                String[] ids = request.getParameterValues("brewMethodId");
                if (ids != null) {
                    for (String value : ids) {
                        int brewMethodId = Integer.parseInt(value.strip());
                        notes.put(brewMethodId, text(request, "note_" + brewMethodId));
                    }
                }
                found(variantDAO.findById(id), "biến thể");
                variantDAO.setBrewMethods(id, notes);
                return ok("Đã lưu " + notes.size() + " phương pháp pha gợi ý");
            }
            case "flavorProfile" -> {
                ProductVariant scores = new ProductVariant();
                scores.setVariantId(id);
                scores.setAromaScore(optionalInt(request, "aromaScore"));
                scores.setAcidityScore(optionalInt(request, "acidityScore"));
                scores.setSweetnessScore(optionalInt(request, "sweetnessScore"));
                scores.setBitternessScore(optionalInt(request, "bitternessScore"));
                scores.setBodyScore(optionalInt(request, "bodyScore"));
                scores.setAftertasteScore(optionalInt(request, "aftertasteScore"));
                List<Integer> noteIds = new ArrayList<>();
                String[] values = request.getParameterValues("flavorNoteId");
                if (values != null) {
                    for (String value : values) {
                        noteIds.add(Integer.parseInt(value.strip()));
                    }
                }
                found(variantDAO.updateFlavorProfile(scores, noteIds), "biến thể");
                return ok("Đã cập nhật hồ sơ hương vị");
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ: " + action);
        }
    }

    private Object assign(HttpServletRequest request, int id) throws Exception {
        String field = text(request, "field");
        int valueId = requiredInt(request, "valueId", "giá trị cần gán");
        boolean changed;
        String label;
        switch (field == null ? "" : field) {
            case "region" -> {
                changed = variantDAO.assignRegion(id, valueId);
                label = "vùng trồng";
            }
            case "variety" -> {
                changed = variantDAO.assignVariety(id, valueId);
                label = "giống";
            }
            case "process" -> {
                changed = variantDAO.assignProcessingMethod(id, valueId);
                label = "phương pháp sơ chế";
            }
            case "roastLevel" -> {
                changed = variantDAO.assignRoastLevel(id, valueId);
                label = "mức rang";
            }
            default -> throw new IllegalArgumentException("Thuộc tính cần gán không hợp lệ");
        }
        found(changed, "biến thể");
        return ok("Đã gán " + label + " cho biến thể");
    }

    private static ProductVariant read(HttpServletRequest request) {
        ProductVariant variant = new ProductVariant();
        variant.setVariantCode(text(request, "variantCode"));
        variant.setVariantName(text(request, "variantName"));
        variant.setAltitudeM(optionalInt(request, "altitudeM"));
        variant.setHarvestSeason(text(request, "harvestSeason"));
        variant.setDisplayOrder(optionalInt(request, "displayOrder"));
        return variant;
    }
}
