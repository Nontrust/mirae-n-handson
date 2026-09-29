package com.example.item;

/**
 * {@code GET /api/units} 응답 항목. {@code itemCount} 는 legacy units.php 와 같이 공개(status='A')
 * 문항 수만 센 값이다 — 삭제·검수중 문항은 포함하지 않는다.
 */
public record UnitResponse(Integer id, String code, String name, Integer grade, long itemCount) {

    static UnitResponse from(Unit unit, long itemCount) {
        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName(), unit.getGrade(), itemCount);
    }
}
