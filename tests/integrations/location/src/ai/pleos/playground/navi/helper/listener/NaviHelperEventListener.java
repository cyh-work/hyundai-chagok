package ai.pleos.playground.navi.helper.listener;

import ai.pleos.playground.navi.data.CurrentLocationInfo;

/** 위치 콜백만 제공하는 SDK 대역이다. */
public interface NaviHelperEventListener {
    /** 시험 좌표를 전달한다. */
    void onCurrentLocationInfo(CurrentLocationInfo location);
}
