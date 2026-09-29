package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private UnitService unitService;

    @Test
    @DisplayName("listUnits: 저장소 순서를 유지하며 단원별 공개 문항 수(BR-24)를 담아 DTO 로 변환한다")
    void listUnitsMapsInOrderWithItemCount() {
        Unit m51 = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);
        Unit m61 = ItemFixtures.unit(4, "M6-1", "분수의 나눗셈", 6);
        when(unitRepository.findAllByOrderByGradeAscCodeAsc()).thenReturn(List.of(m51, m61));
        when(itemRepository.countByUnitIdAndStatus(1, ItemStatus.ACTIVE)).thenReturn(3L);
        when(itemRepository.countByUnitIdAndStatus(4, ItemStatus.ACTIVE)).thenReturn(0L);

        List<UnitResponse> units = unitService.listUnits();

        assertThat(units).extracting(UnitResponse::code).containsExactly("M5-1", "M6-1");
        assertThat(units.get(0)).isEqualTo(new UnitResponse(1, "M5-1", "분수의 덧셈과 뺄셈", 5, 3));
        assertThat(units.get(1)).isEqualTo(new UnitResponse(4, "M6-1", "분수의 나눗셈", 6, 0));
    }

    @Test
    @DisplayName("listUnits: 단원이 없으면 빈 목록")
    void listUnitsEmpty() {
        when(unitRepository.findAllByOrderByGradeAscCodeAsc()).thenReturn(List.of());

        assertThat(unitService.listUnits()).isEmpty();
    }
}
