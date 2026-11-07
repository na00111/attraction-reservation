package com.example.attractionreservation.domain.routeRecommendation.algorithm;
//최적 방문 순서 탐색

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import com.example.attractionreservation.domain.routeRecommendation.preference.PreferenceScores;
import com.example.attractionreservation.global.util.KstTime;
import org.springframework.stereotype.Component;
//후보 어트랙션으로 방문 순서와 탑승 슬롯을 결정
@Component
public class RouteSearcher {

    // 취향 문장이 없는 경우. 모든 경로의 취향 점수가 같아 기존 비교 규칙만으로 고른다.
    public RouteSearchResult search(
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<CandidateAttraction> candidates
    ) {
        return search(startAt, endAt, candidates, PreferenceScores.neutral());
    }

    /**
     * @param preferenceScores 어트랙션별 취향 단계. 방문 개수가 같은 경로들 중 무엇을 고를지에만 쓰인다(isBetter 2순위).
     *                         슬롯 선택·도착 시각 계산·이용 종료 제한에는 관여하지 않는다.
     */
    public RouteSearchResult search(
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<CandidateAttraction> candidates,
            PreferenceScores preferenceScores
    ) {
        // 아무것도 방문하지 않는 경로를 초기 비교 대상으로 둔다
        RouteSearchResult emptyResult = new RouteSearchResult(
                startAt, List.of(), List.of()
        );
        RouteSearchResult bestResult = searchPaths(
                startAt, //전체 소요 시간 계산의 기준
                endAt,
                candidates,
                startAt, //첫 이동을 시작할 수 있는 시각
                new ArrayList<>(), //현재 만들어 가는 방문 경로
                new HashSet<>(), //현재 경로에서 이미 방문한 어트랙션 ID
                emptyResult,
                preferenceScores
        );
        //최종 선택된 경로에 포함된 어트랙션 ID를 모음
        Set<Long> includedIds = new HashSet<>();
        for (PlannedVisit visit: bestResult.visits()) {
            includedIds.add(visit.attractionId());
        }
        //후보는 있었지만 최종 경로에 포함되지 않은 어트랙션을 찾는다
        List<Long> unreachableIds = new ArrayList<>();
        for (CandidateAttraction attraction : candidates) {
            if (!includedIds.contains(attraction.attractionId())) {
                unreachableIds.add(attraction.attractionId());
            }
        }
        return new RouteSearchResult(
                startAt,
                bestResult.visits(),
                List.copyOf(unreachableIds)
        );
    }

    //현재 경로에서 다음 방문지를 하나씩 선택하며 재귀적으로 탐색
    private RouteSearchResult searchPaths(
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<CandidateAttraction> candidates,
            LocalDateTime departureAt,
            List<PlannedVisit> path,
            Set<Long> visitedIds,
            RouteSearchResult bestResult,
            PreferenceScores preferenceScores
    ) {
        //현재 경로도 하나의 완성 가능한 추천 결과로 평가
        RouteSearchResult currentResult = new RouteSearchResult(
                startAt, List.copyOf(path), List.of()
        );
        if (isBetter(currentResult, bestResult, preferenceScores)) {
            bestResult = currentResult;
        }
        // 다음 방문지로 선택할 수 있는 모든 어트랙션을 시도
        for (CandidateAttraction attraction : candidates) {
            //현재 경로에서 이미 방문한 어트랙션은 다시 선택하지 않는다
            if (visitedIds.contains(attraction.attractionId())) {
                continue;
            }

            LocalDateTime arrivalAt = departureAt.plusMinutes(
                    attraction.moveMinutes()
            );
            //도착 후 이용할 수 있는 가장 이른 슬롯 하나를 찾는다
            Optional<CandidateSlot> availableSLot = findEarliestAvailableSlot(attraction,arrivalAt,endAt);
            if (availableSLot.isEmpty()) {
                continue;
            }
            //도착 후 슬롯 시작까지 기다릴 시간을 계산한다
            CandidateSlot slot = availableSLot.get();
            int waitMinutes = Math.toIntExact(
                    Duration.between(arrivalAt.atZone(KstTime.ZONE), slot.startsAt().atZone(KstTime.ZONE)).toMinutes()
            );
            //이번에 선택한 다음 방문의 시간표를 만든다
            PlannedVisit visit = new PlannedVisit(
                    path.size() +1,
                    attraction.attractionId(),
                    attraction.attractionName(),
                    slot.slotId(),
                    attraction.moveMinutes(),
                    arrivalAt,
                    waitMinutes,
                    slot.startsAt(),
                    slot.endsAt(),
                    slot.rideMinutes()
            );
            //현재 경로에 방문을 추가하고 중복 방문 방지 집합에도 기록
            path.add(visit);
            visitedIds.add(attraction.attractionId());
            //방금 선택한 슬롯이 끝난 시각부터 다음 방문을 이어 붙인다
            //재귀 호출에서 찾은 더 좋은 결과를 받아 이후 다른 방문 순서를 탐색할 때도 비교 기준으로 사용
            bestResult = searchPaths(
                    startAt,
                    endAt,
                    candidates,slot.endsAt(),
                    path,
                    visitedIds,
                    bestResult,
                    preferenceScores
            );
            //백트래킨 -> 이번 방문 선택을 되돌려 다음 후보를 같은 출발 상태에서 시도
            visitedIds.remove(attraction.attractionId());
            path.remove(path.size() - 1);
        }
        return bestResult;
    }

    //도착 시각 이후에 시작하고 이용 종료 시각 안에 끝나는 가장 이른 후보 슬롯을 반환
    private Optional<CandidateSlot> findEarliestAvailableSlot(
            CandidateAttraction attraction,
            LocalDateTime arrivalAt,
            LocalDateTime endAt
    ) {
        for(CandidateSlot slot : attraction.slots()) {
            // 도착 시각에는 이미 시작한 슬롯이므로 다음 슬롯 확인, 도착 시간과 슬롯 시작 시각이 같으면 허용
            if (arrivalAt.isAfter(slot.startsAt())) {
                continue;
            }
            //이 슬롯이 이용 종료 제한을 넘으면 방문할 수 없음
            if (slot.endsAt().isAfter(endAt)) {
                return Optional.empty();
            }
            //정렬된 순서에서 처음 발견한 유효 슬롯이므로 바로 선택
            return Optional.of(slot);
        }
        // 모든 슬롯이 이미 시작했거나, 슬롯 목록이 비어 있는 경우
        return Optional.empty();
    }

    //새 경로가 기존 최선 경로보다 우선하는지 판단
    private boolean isBetter(
            RouteSearchResult candidate,
            RouteSearchResult best,
            PreferenceScores preferenceScores
    ) {
        // 1순위: 방문 개수가 많은 경로
        int candidateCount = candidate.visits().size();
        int bestCount = best.visits().size();
        if (candidateCount != bestCount) {
            // 개수가 다르면 다음 기준을 비교하지 않고,
            // 새 경로의 방문 개수가 더 많은지 반환
            return candidateCount > bestCount;
        }
        // 방문 개수가 같으면 아래에서 취향 점수, 총 소요 시간 순으로 비교하기

        if (candidate.isEmpty()) {
            return false;
        }
        //2순위(추가): 취향 점수가 높은 경로 = 좋아하는 어트랙션이 많고 싫어하는 어트랙션이 적은 경로
        // 방문 개수가 같을 때만 비교하므로 "더 많이 탄다"는 1순위를 취향이 뒤집지 않는다.
        // 같은 어트랙션 집합이면 점수가 같아 아래 시간 비교로 넘어간다. 즉 전부 탈 수 있으면 결과가 기존과 같고,
        // 일부만 탈 수 있을 때 "어떤 어트랙션을 남길지"에만 영향을 준다.
        // 취향 문장이 없거나 Jev가 답하지 않으면 모든 점수가 0이라 기존 비교와 같다.
        int preferenceComparison = Integer.compare(
                preferenceScores.totalOf(candidate.visits()),
                preferenceScores.totalOf(best.visits())
        );
        if (preferenceComparison != 0) {
            return preferenceComparison > 0;
        }
        //3순위: 전체 소요 시간이 짧은 경로
        // compare(a, b):
        // - 음수: a가 작다
        // - 0: 같다
        // - 양수: a가 크다
        int durationComparison =  Integer.compare(
                candidate.totalDurationMinutes(),
                best.totalDurationMinutes()
        );
        if (durationComparison != 0) {
            return durationComparison < 0;
        }
        //4순위: 첫 번째 슬롯의 탑승 시작 시각이 빠른 경로
        int firstStartComparison = candidate.visits().get(0).startsAt()
                .compareTo(best.visits().get(0).startsAt());
        if (firstStartComparison != 0) {
            return firstStartComparison <0;
        }
        //5순위: 방문 순서대로 어트랙션 ID를 사전순 비교
        for (int visitIndex = 0; visitIndex <candidateCount; visitIndex++) {
            int idComparison = Long.compare(
                    candidate.visits().get(visitIndex).attractionId(),
                    best.visits().get(visitIndex).attractionId()
            );
            if (idComparison != 0) {
                return idComparison < 0;
            }
        }
        // 정의된 모든 비교 기준이 같으면 기존 결과를 유지
        return false;
    }
}
