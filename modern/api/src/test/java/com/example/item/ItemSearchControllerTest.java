package com.example.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스는 목, JSON 필드 이름(items/count/message)만 확인한다. */
@WebMvcTest(ItemSearchController.class)
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("GET /api/items/search → 200, items/count/message 로 응답한다")
    void searchReturnsNormalizedShape() throws Exception {
        when(itemSearchService.search(eq("분수"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(new ItemSearchResponse(
                List.of(new ItemSearchItem(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음"))),
                1,
                null));

        mockMvc.perform(get("/api/items/search").param("q", "분수"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(12))
            .andExpect(jsonPath("$.items[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.items[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 단원 코드 → 404 가 아니라 200, 0건 + 경고 message")
    void unknownUnitReturnsOkWithZeroCount() throws Exception {
        when(itemSearchService.search(isNull(), eq("M7-1"), any(), any(), any(), any(), any()))
            .thenReturn(new ItemSearchResponse(List.of(), 0, "등록되지 않은 단원 코드입니다: M7-1"));

        mockMvc.perform(get("/api/items/search").param("unit", "M7-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.message").value("등록되지 않은 단원 코드입니다: M7-1"));
    }
}
