package com.example.pleosstarter.kakao;

import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.api.objects.KNPoiDetail;
import com.kakaomobility.knsdk.api.objects.KNPoiDetailInfo;
import com.kakaomobility.knsdk.api.objects.KNPoiDetailResult;
import com.kakaomobility.knsdk.api.objects.KNSearchResult;
import com.kakaomobility.knsdk.api.objects.KNSearchResultObject;
import com.kakaomobility.knsdk.common.objects.KNPOI;
import com.kakaomobility.knsdk.common.objects.KNSearchPOI;
import com.kakaomobility.knsdk.common.util.DoublePoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** SDK 응답을 화면용 장소 모델로 바꾸며 공급자 데이터가 없는 필드를 만들지 않는다. */
public final class KakaoPlaceMapper {
    /** 장소 검색 결과를 제한 개수까지 변환한다. */
    public List<PlaceDetails> fromSearch(KNSearchResult result, int limit) {
        KNSearchResultObject places = result.getPlaceResult();
        if (places == null || places.getPoiList() == null) {
            return Collections.emptyList();
        }

        List<PlaceDetails> mapped = new ArrayList<>();
        for (KNSearchPOI place : places.getPoiList()) {
            if (place == null || place.getPos() == null) {
                continue;
            }
            DoublePoint coordinate = KNSDK.INSTANCE.convertKATECToWGS84(
                    place.getPos().getX(), place.getPos().getY());
            KakaoPlaceSelection selection = new KakaoPlaceSelection(findLookupId(place),
                    place.getName(), coordinate.getY(), coordinate.getX());
            mapped.add(new PlaceDetails(selection, place.getAddress(), place.getRnAddress(), "", ""));
            if (mapped.size() >= limit) {
                break;
            }
        }
        return Collections.unmodifiableList(mapped);
    }

    /** 상세 응답 중 요청 ID가 같은 항목만 선택하며 이름으로 다른 업체를 추측하지 않는다. */
    public Optional<PlaceDetails> fromDetail(KNPoiDetailResult result, KakaoPlaceSelection selection) {
        if (result.getDetailList() == null) {
            return Optional.empty();
        }

        for (KNPoiDetail detail : result.getDetailList()) {
            if (detail == null || detail.getInfo() == null) {
                continue;
            }
            KNPoiDetailInfo info = detail.getInfo();
            if (!selection.getLookupId().equals(detail.getReqId())
                    && !selection.getLookupId().equals(info.getPoiId())) {
                continue;
            }

            // 좌표가 누락된 상세 응답은 이미 선택한 지도의 위치를 유지한다.
            double latitude = selection.getLatitude();
            double longitude = selection.getLongitude();
            if (info.getX() != 0 && info.getY() != 0) {
                DoublePoint coordinate = KNSDK.INSTANCE.convertKATECToWGS84(info.getX(), info.getY());
                latitude = coordinate.getY();
                longitude = coordinate.getX();
            }
            String name = selection.getName();
            if (info.getName() != null && !info.getName().trim().isEmpty()) {
                name = info.getName();
            }
            KakaoPlaceSelection detailed = new KakaoPlaceSelection(
                    selection.getLookupId(), name, latitude, longitude);
            return Optional.of(new PlaceDetails(detailed, info.getAddr(), info.getRnaddr(),
                    info.getTel(), info.getCateDisp()));
        }
        return Optional.empty();
    }

    /** 안내 지점에 포함된 실제 장소 ID를 재사용하고 없으면 상세 조회를 생략한다. */
    private String findLookupId(KNSearchPOI place) {
        if (place.getGuidePoints() != null) {
            for (KNPOI guide : place.getGuidePoints()) {
                if (guide != null && guide.getPoiId() != null && !guide.getPoiId().trim().isEmpty()) {
                    return guide.getPoiId().trim();
                }
            }
        }
        return "";
    }
}
