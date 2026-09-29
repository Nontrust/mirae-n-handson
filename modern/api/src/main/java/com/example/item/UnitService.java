package com.example.item;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UnitService {

    private static final Logger log = LoggerFactory.getLogger(UnitService.class);

    private final UnitRepository unitRepository;
    private final ItemRepository itemRepository;

    public UnitService(UnitRepository unitRepository, ItemRepository itemRepository) {
        this.unitRepository = unitRepository;
        this.itemRepository = itemRepository;
    }

    /** 단원 목록 — 학년, 코드 순. 단원별 공개(status='A') 문항 수(BR-24)를 함께 담는다. */
    public List<UnitResponse> listUnits() {
        List<UnitResponse> units = unitRepository.findAllByOrderByGradeAscCodeAsc().stream()
            .map(unit -> UnitResponse.from(unit, itemRepository.countByUnitIdAndStatus(unit.getId(), ItemStatus.ACTIVE)))
            .toList();
        log.debug("listed {} units", units.size());
        return units;
    }
}
