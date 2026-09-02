package com.devknowledge.service;

import com.devknowledge.mapper.AdminMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService - 请求记录分页")
class AdminServiceTest {

    @Mock
    private AdminMapper adminMapper;

    @InjectMocks
    private AdminService adminService;

    @Test
    @DisplayName("按页查询请求记录并返回分页元数据")
    void listTracesReturnsPageMetadata() {
        when(adminMapper.countTraces()).thenReturn(45L);
        when(adminMapper.listTraces(20, 20)).thenReturn(List.of());

        var result = adminService.listTraces(2, 20).block();

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(45);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isHasPrevious()).isTrue();
        assertThat(result.isHasNext()).isTrue();
        verify(adminMapper).listTraces(eq(20), eq(20));
    }

    @Test
    @DisplayName("页码超出范围时回退到最后一页")
    void clampsPageToLastPage() {
        when(adminMapper.countTraces()).thenReturn(3L);
        when(adminMapper.listTraces(0, 20)).thenReturn(List.of());

        var result = adminService.listTraces(99, 20).block();

        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isHasPrevious()).isFalse();
        assertThat(result.isHasNext()).isFalse();
    }
}
