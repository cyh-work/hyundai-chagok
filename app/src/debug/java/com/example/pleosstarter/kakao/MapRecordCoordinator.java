package com.example.pleosstarter.kakao;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import com.example.pleosstarter.gesture.MapStrokeGesture;
import com.example.pleosstarter.map.MapGesture;
import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.records.LocationNoteDialog;
import com.example.pleosstarter.records.HistoryMetadataFailure;
import com.example.pleosstarter.records.MapHistoryEntry;
import com.example.pleosstarter.records.MapHistoryService;
import com.example.pleosstarter.records.MapRecordFailure;
import com.example.pleosstarter.records.MapRecordReadDialog;
import com.example.pleosstarter.records.MapRecordsService;
import com.example.pleosstarter.records.MemoSpeechInput;
import com.example.pleosstarter.records.RecordNavigationHandler;
import com.example.pleosstarter.navigation.NavigationDestination;
import com.example.pleosstarter.records.SavedMapPoint;
import com.example.pleosstarter.records.SavedPlacesDialog;
import com.example.pleosstarter.stores.StoreSearchService;
import com.example.pleosstarter.stores.StoreSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 그림 좌표, 기록 서비스와 편집·목록 화면을 연결한다. */
public final class MapRecordCoordinator {
    private final Context context;
    private final PlaceMapSession map;
    private final MapRecordsService records;
    private final MapHistoryService history;
    private final MemoSpeechInput speech;
    private final Consumer<String> feedback;
    private final Supplier<StoreSearchService> storeSearchFactory;
    private final Consumer<StoreSummary> storeSelection;
    private final RecordNavigationHandler recordNavigation;
    private LocationNoteDialog noteDialog;
    private MapRecordMarker savedRecordToOpen;
    private Dialog savedDialog;
    private Consumer<Boolean> dialogVisibility;
    private Consumer<List<MapHistoryEntry>> historyListener;
    private Consumer<List<MapRecordMarker>> recordsChanged;
    private List<MapHistoryEntry> historyEntries = Collections.emptyList();
    private boolean recordDragActive;
    private boolean closed;

    /** 지도·저장·음성과 주변 조회·가게 선택·기록 위치 길안내 동작을 주입한다. */
    public MapRecordCoordinator(Context context, PlaceMapSession map, MapRecordsService records,
            MapHistoryService history, MemoSpeechInput speech, Consumer<String> feedback,
            Supplier<StoreSearchService> storeSearchFactory, Consumer<StoreSummary> storeSelection,
            RecordNavigationHandler recordNavigation) {
        this.context = context;
        this.map = map;
        this.records = records;
        this.history = history;
        this.speech = speech;
        this.feedback = feedback;
        this.storeSearchFactory = storeSearchFactory;
        this.storeSelection = storeSelection;
        this.recordNavigation = recordNavigation;
    }

    /** 화면에 기존 저장 기록을 즉시 전달하고 이후 저장·삭제 때 같은 목록을 갱신한다. */
    public void setHistoryListener(Consumer<List<MapHistoryEntry>> listener) {
        historyListener = listener;
        try {
            refreshHistory();
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        }
    }

    /** 기록 창 표시 상태를 카메라 자동 복귀 보류에 연결하며 위치 재생은 유지한다. */
    public void setDialogVisibilityListener(Consumer<Boolean> listener) {
        dialogVisibility = listener;
    }

    /** 실제 저장 성공 뒤의 마커 목록을 재방문 판단에 전달한다. */
    public void setRecordsChangedListener(Consumer<List<MapRecordMarker>> listener) {
        recordsChanged = listener;
    }

    /** 재방문 카드를 누른 저장 참조를 재확인하고 지도 이동 없이 같은 기록 모달을 연다. */
    public void showRevisitRecord(MapRecordMarker record) {
        if (closed || record == null) {
            return;
        }
        try {
            if (record.favorite()) {
                Optional<SavedMapPoint> current = records.favorites().stream()
                        .filter(point -> point.id().equals(record.favoriteId())).findFirst();
                if (current.isPresent()) {
                    showFavorite(current.get());
                    return;
                }
            } else {
                List<LocationNote> notes = records.notes();
                int index = record.noteIndex();
                if (index >= 0 && index < notes.size() && notes.get(index).equals(record.note())) {
                    showRecord(record);
                    return;
                }
            }
            feedback.accept("이 기록이 변경됐어요. 다시 선택해 주세요.");
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        }
    }

    /** 열려 있는 기록 창을 기준으로 보류 상태를 알린다. */
    private void notifyDialogVisibility() {
        if (dialogVisibility != null) {
            dialogVisibility.accept((noteDialog != null && noteDialog.isShowing())
                    || (savedDialog != null && savedDialog.isShowing()));
        }
    }

    /** 원·별의 짧은 탭을 같은 읽기 모달로 소비하고 저장본·음성 수집은 변경하지 않는다. */
    public boolean showRecordAt(float x, float y) {
        Optional<MapRecordMarker> selected = map.findRecordAt(x, y);
        if (selected.isEmpty()) {
            return false;
        }

        showRecord(selected.get());
        return true;
    }

    /** 두 기록의 저장 좌표를 공통 모달의 읽기·주변 조회·삭제·길안내에 연결한다. */
    private void showRecord(MapRecordMarker record) {
        if (closed) {
            return;
        }
        String text = record.note() == null ? "" : record.note().text();
        MapRecordReadDialog dialog = new MapRecordReadDialog(context, record.favorite(), text,
                record.latitude(), record.longitude(), storeSearchFactory.get(), storeSelection,
                openedDialog -> deleteRecord(record, openedDialog),
                openedDialog -> navigateRecord(record, openedDialog));
        showSavedDialog(dialog);
        Log.i("PlaceMap", "RECORD_READ code=OPEN favorite=" + record.favorite());
    }

    /** 사용자가 길안내를 누른 기록만 다시 검증하고 메모 원문 없이 내비 목적지로 전달한다. */
    private void navigateRecord(MapRecordMarker record, MapRecordReadDialog dialog) {
        if (closed || savedDialog != dialog || !dialog.isShowing()) {
            return;
        }
        try {
            NavigationDestination destination = recordDestination(record);
            if (!recordNavigation.requestNavigation(destination)) {
                dialog.showNavigationFailure("지금은 길안내를 시작할 수 없어요. 잠시 후 다시 눌러 주세요.");
                return;
            }
            dialog.dismiss();
            Log.i("PlaceMap", "RECORD_NAVIGATION code=ACCEPTED favorite=" + record.favorite());
        } catch (MapRecordFailure failure) {
            dialog.showNavigationFailure(failure.getMessage());
            reportFailure(failure);
        }
    }

    /** 삭제되거나 다른 메모로 바뀐 참조를 거부하고 저장 위치를 짧은 목적지 이름에 연결한다. */
    private NavigationDestination recordDestination(MapRecordMarker record) {
        if (record.favorite()) {
            SavedMapPoint point = records.favorites().stream()
                    .filter(candidate -> candidate.id().equals(record.favoriteId())).findFirst()
                    .orElseThrow(() -> new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                            "이 즐겨찾기를 찾을 수 없어요. 다시 선택해 주세요."));
            String name = point.name().isBlank() ? "즐겨찾기 위치" : point.name();
            return new NavigationDestination(name, "", point.latitude(), point.longitude());
        }

        List<LocationNote> notes = records.notes();
        int index = record.noteIndex();
        if (index < 0 || index >= notes.size() || !notes.get(index).equals(record.note())) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "이 메모가 변경됐어요. 다시 선택해 주세요.");
        }
        LocationNote note = notes.get(index);
        return new NavigationDestination("메모 위치", "", note.latitude(), note.longitude());
    }

    /** 선택한 한 건의 영속 삭제가 성공한 뒤에만 마커와 히스토리를 갱신하고 창을 닫는다. */
    private void deleteRecord(MapRecordMarker record, MapRecordReadDialog dialog) {
        if (closed || savedDialog != dialog || !dialog.isShowing()) {
            return;
        }
        try {
            if (record.favorite()) {
                SavedMapPoint selected = records.favorites().stream()
                        .filter(point -> point.id().equals(record.favoriteId())).findFirst()
                        .orElseThrow(() -> new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                                "삭제할 즐겨찾기를 찾을 수 없어요. 다시 선택해 주세요."));
                records.removeFavorite(selected.id());
                updateHistoryMetadata(() -> history.favoriteDeleted(selected));
            } else {
                List<LocationNote> beforeDelete = records.notes();
                records.removeNote(record.noteIndex(), record.note());
                updateHistoryMetadata(() -> history.notesDeleted(beforeDelete, record.noteIndex()));
            }

            refreshMarkers();
            dialog.dismiss();
            feedback.accept(record.favorite() ? "즐겨찾기를 삭제했어요" : "메모를 삭제했어요");
            Log.i("PlaceMap", "RECORD_DELETE code=SAVED favorite=" + record.favorite());
        } catch (MapRecordFailure failure) {
            dialog.showFailure(failure);
            reportFailure(failure);
        }
    }

    /** 원·별만 처리하며 그린 중심의 지도 좌표를 저장 서비스로 전달한다. */
    public void onGesture(MapStrokeGesture gesture) {
        if (gesture.type() != MapGesture.Type.CIRCLE && gesture.type() != MapGesture.Type.STAR) {
            feedback.accept("원은 위치 메모, 별은 즐겨찾기 메모예요. 조금 크게 그려 주세요.");
            return;
        }
        try {
            KakaoPlaceSelection point = map.locationAtScreen(gesture.centerX(), gesture.centerY());
            Log.i("PlaceMap", "GESTURE_TARGET type=" + gesture.type());
            if (gesture.type() == MapGesture.Type.STAR) {
                openFavoriteMemo(point);
            } else {
                openMemo(point);
            }
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        } catch (IllegalArgumentException failure) {
            feedback.accept(failure.getMessage());
        }
    }

    /** 지도 표시 계층이 잡은 실제 저장 기록만 이동 상태로 전환한다. */
    public boolean beginRecordDrag(float x, float y) {
        cancelRecordDrag();
        try {
            recordDragActive = map.beginRecordDrag(x, y);
            return recordDragActive;
        } catch (IllegalArgumentException failure) {
            feedback.accept(failure.getMessage());
            return false;
        }
    }

    /** 이동 중에는 저장 서비스를 호출하지 않으며 투영 실패는 미리보기를 취소한다. */
    public void previewRecordDrag(float x, float y) {
        if (!recordDragActive) {
            return;
        }
        try {
            map.previewRecordDrag(x, y);
        } catch (IllegalArgumentException failure) {
            cancelRecordDrag();
            feedback.accept(failure.getMessage());
        }
    }

    /** 포인터 UP에서만 좌표를 저장하고 메모 시각은 기존 순번에 대응시켜 보존한다. */
    public void endRecordDrag(float x, float y, boolean cancelled) {
        if (!recordDragActive) {
            return;
        }
        recordDragActive = false;
        try {
            Optional<MapRecordMove> result = map.endRecordDrag(x, y, cancelled);
            if (result.isEmpty()) {
                return;
            }

            MapRecordMove move = result.get();
            MapRecordMarker original = move.original();
            if (original.favorite()) {
                records.moveFavorite(original.favoriteId(), move.latitude(), move.longitude());
                feedback.accept("즐겨찾기 위치를 옮겼어요");
            } else {
                List<LocationNote> beforeMove = records.notes();
                records.moveNote(original.noteIndex(), original.note(), move.latitude(), move.longitude());
                feedback.accept("메모 위치를 옮겼어요");
                updateHistoryMetadata(() -> history.notesMoved(beforeMove));
            }
            refreshMarkers();
            Log.i("PlaceMap", "RECORD_DRAG code=SAVED favorite=" + original.favorite());
        } catch (MapRecordFailure failure) {
            // 렌더러는 저장 전에 원위치를 복원하므로 실패한 좌표가 화면에 남지 않는다.
            reportFailure(failure);
            refreshMarkers();
        } catch (IllegalArgumentException failure) {
            feedback.accept(failure.getMessage());
        }
    }

    /** 화면 이탈이나 입력 취소는 좌표 저장 없이 미리보기만 제거한다. */
    public void cancelRecordDrag() {
        recordDragActive = false;
        map.endRecordDrag(0, 0, true);
    }

    /** 별 위치를 고정하고 저장 확인 전에는 즐겨찾기를 만들거나 기존 메모를 바꾸지 않는다. */
    private void openFavoriteMemo(KakaoPlaceSelection point) {
        savedRecordToOpen = null;
        if (noteDialog != null) {
            noteDialog.dismiss();
        }
        String initialText = records.findFavoriteNear(point.getLatitude(), point.getLongitude())
                .map(SavedMapPoint::note).orElse("");
        noteDialog = new LocationNoteDialog(context, speech, "즐겨찾기 메모", point.getName(),
                initialText, text -> {
                    List<SavedMapPoint> beforeSave = records.favorites();
                    SavedMapPoint saved = records.saveFavorite(point.getLatitude(),
                            point.getLongitude(), point.getName(), text);
                    feedback.accept("즐겨찾기 메모를 저장했어요");
                    updateHistoryMetadata(() -> history.favoriteSaved(saved, beforeSave));
                    Log.i("PlaceMap", "FAVORITE_SAVED count=" + records.favorites().size()
                            + " memo=true");
                    refreshMarkers();
                    LocationNote memo = new LocationNote(saved.latitude(), saved.longitude(), saved.note());
                    savedRecordToOpen = new MapRecordMarker(saved.latitude(), saved.longitude(),
                            true, memo, saved.id(), -1);
                });
        showMemoDialog();
    }

    /** 그린 위치를 고정한 상태로 음성 메모 편집창을 연다. */
    private void openMemo(KakaoPlaceSelection point) {
        savedRecordToOpen = null;
        if (noteDialog != null) {
            noteDialog.dismiss();
        }
        List<LocationNote> beforeSave = records.notes();
        noteDialog = new LocationNoteDialog(context, records, speech,
                point.getLatitude(), point.getLongitude(), point.getName(), note -> {
                    feedback.accept("메모를 저장했어요");
                    updateHistoryMetadata(() -> history.noteSaved(note, beforeSave));
                    Log.i("PlaceMap", "NOTE_SAVED");
                    refreshMarkers();
                    savedRecordToOpen = new MapRecordMarker(note.latitude(), note.longitude(),
                            false, note, "", records.notes().indexOf(note));
                });
        showMemoDialog();
    }

    /** 성공적으로 저장한 편집창이 닫히면 글자와 주변 가게 화면을 이어서 연다. */
    private void showMemoDialog() {
        LocationNoteDialog openedDialog = noteDialog;
        openedDialog.setOnDismissListener(dialog -> {
            if (noteDialog == openedDialog) {
                noteDialog = null;
                MapRecordMarker savedRecord = savedRecordToOpen;
                savedRecordToOpen = null;
                if (savedRecord != null && !closed) {
                    showRecord(savedRecord);
                }
            }
            notifyDialogVisibility();
        });
        noteDialog.show();
        notifyDialogVisibility();
    }

    /** 저장된 모델을 지도 표시용 DTO로 변환한다. */
    public void refreshMarkers() {
        try {
            List<MapRecordMarker> markers = new ArrayList<>();
            for (SavedMapPoint point : records.favorites()) {
                LocationNote memo = null;
                if (!point.note().isEmpty()) {
                    memo = new LocationNote(point.latitude(), point.longitude(), point.note());
                }
                markers.add(new MapRecordMarker(point.latitude(), point.longitude(), true, memo,
                        point.id(), -1));
            }
            List<LocationNote> notes = records.notes();
            for (int index = 0; index < notes.size(); index++) {
                LocationNote note = notes.get(index);
                markers.add(new MapRecordMarker(note.latitude(), note.longitude(), false, note, "", index));
            }
            map.showRecords(markers);
            if (recordsChanged != null) {
                recordsChanged.accept(Collections.unmodifiableList(new ArrayList<>(markers)));
            }
            refreshHistory();
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        }
    }

    /** 메모와 즐겨찾기를 합친 전체 히스토리를 열고 원래 기록으로 선택을 연결한다. */
    public void showSavedPlaces() {
        if (closed) {
            return;
        }
        try {
            refreshHistory();
            SavedPlacesDialog dialog = new SavedPlacesDialog(context, historyEntries, this::selectHistory);
            showSavedDialog(dialog);
            Log.i("PlaceMap", "HISTORY_LIST count=" + historyEntries.size());
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        }
    }

    /** 히스토리의 최신 저장 참조를 다시 찾아 해당 위치로 이동하고 공통 기록 모달을 연다. */
    public void selectHistory(MapHistoryEntry entry) {
        if (closed || entry == null) {
            return;
        }
        try {
            if (entry.type() == MapHistoryEntry.Type.FAVORITE) {
                Optional<SavedMapPoint> favorite = history.findFavorite(entry.id());
                if (favorite.isPresent()) {
                    SavedMapPoint point = favorite.get();
                    map.showPlace(new KakaoPlaceSelection(0, point.name(), point.latitude(), point.longitude()));
                    showFavorite(point);
                    Log.i("PlaceMap", "HISTORY_FOCUSED type=FAVORITE");
                    return;
                }
            } else {
                OptionalInt selectedIndex = history.findNoteIndex(entry.id());
                List<LocationNote> notes = records.notes();
                if (selectedIndex.isPresent() && selectedIndex.getAsInt() < notes.size()) {
                    int index = selectedIndex.getAsInt();
                    LocationNote note = notes.get(index);
                    map.showPlace(new KakaoPlaceSelection(0, "메모 위치", note.latitude(), note.longitude()));
                    showRecord(new MapRecordMarker(note.latitude(), note.longitude(), false, note, "", index));
                    Log.i("PlaceMap", "HISTORY_FOCUSED type=NOTE");
                    return;
                }
            }
            feedback.accept("이 기록을 찾을 수 없어요.");
            refreshHistory();
        } catch (MapRecordFailure failure) {
            reportFailure(failure);
        }
    }

    /** 시각이 없거나 복원에 실패해도 실제 기록은 이전 기록으로 남겨 화면에 전달한다. */
    private void refreshHistory() {
        try {
            historyEntries = history.entries();
        } catch (HistoryMetadataFailure failure) {
            historyEntries = history.entriesWithoutTimes();
            reportHistoryMetadataFailure(failure);
        }
        if (historyListener != null) {
            historyListener.accept(historyEntries);
        }
    }

    /** 본 기록 저장 성공과 시각 저장 실패를 분리해 처리한다. */
    private void updateHistoryMetadata(Runnable action) {
        try {
            action.run();
        } catch (HistoryMetadataFailure failure) {
            reportHistoryMetadataFailure(failure);
        }
    }

    /** 기록이 유지되는 시각 관련 실패만 별도 문구와 코드로 전달한다. */
    private void reportHistoryMetadataFailure(HistoryMetadataFailure failure) {
        feedback.accept(failure.getMessage());
        Log.w("PlaceMap", "HISTORY_METADATA_FAILURE code=" + failure.getCode());
    }

    /** 히스토리에서 선택한 즐겨찾기도 지도와 같은 읽기·주변 조회 화면으로 연결한다. */
    private void showFavorite(SavedMapPoint point) {
        LocationNote memo = null;
        if (!point.note().isEmpty()) {
            memo = new LocationNote(point.latitude(), point.longitude(), point.note());
        }
        showRecord(new MapRecordMarker(point.latitude(), point.longitude(), true, memo, point.id(), -1));
    }

    /** 저장 관련 창을 하나만 유지하고 닫힌 창의 참조를 정리한다. */
    private void showSavedDialog(Dialog dialog) {
        if (savedDialog != null) {
            savedDialog.dismiss();
        }
        savedDialog = dialog;
        if (context instanceof Activity activity) {
            dialog.setOwnerActivity(activity);
        }
        dialog.setOnDismissListener(closedDialog -> {
            if (savedDialog == dialog) {
                savedDialog = null;
            }
            notifyDialogVisibility();
        });
        dialog.show();
        notifyDialogVisibility();
    }

    /** 저장 실패를 성공 문구와 구분해 표시한다. */
    private void reportFailure(MapRecordFailure failure) {
        feedback.accept(failure.getMessage());
        Log.w("PlaceMap", "RECORD_FAILURE code=" + failure.getCode());
    }

    /** 화면을 떠날 때 진행 중인 음성 수집을 중단한다. */
    public void pause() {
        cancelRecordDrag();
        if (noteDialog != null) {
            noteDialog.cancelSpeech();
        }
    }

    /** 화면 종료 시 열린 기록 창과 음성 자원을 정리한다. */
    public void close() {
        closed = true;
        savedRecordToOpen = null;
        cancelRecordDrag();
        historyListener = null;
        recordsChanged = null;
        if (noteDialog != null) {
            noteDialog.dismiss();
        }
        if (savedDialog != null) {
            savedDialog.dismiss();
        }
        speech.close();
    }
}
