package com.example.pleosstarter.navigation;

/** Pleos 내비가 제공한 안내 상태와 다음 회전 정보를 담는다. */
public record GuidanceSnapshot(boolean active, String nextTurn, int metersToTurn) {
}
