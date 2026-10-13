package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserQueryServiceTest extends AdminUserServiceTestBase {

    // ---------- list ----------

    @Test
    void list_mapsRepositoryPageToResponse() {
        School school = school(5L, true);
        Page<User> page = new PageImpl<>(
                List.of(user(1L, UserRole.STUDENT, true, school), user(2L, UserRole.STUDENT, true, school)),
                PageRequest.of(0, 20), 2);
        when(userRepository.search(eq(5L), eq(UserRole.STUDENT), eq(true), any(Pageable.class))).thenReturn(page);

        PageResponse<UserResponse> response = service.list(5L, UserRole.STUDENT, true, 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).schoolId()).isEqualTo(5L);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void list_correctsNegativePageAndTooLargeSize() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, -5, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(AdminUserService.MAX_PAGE_SIZE);
    }

    @Test
    void list_nonPositiveSizeFallsBackToDefault() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(AdminUserService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void list_sortsBySurnameNameAndId() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("surname", "name", "id"));
    }

    // ---------- get ----------

    @Test
    void get_returnsUser() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));

        UserResponse response = service.get(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void get_unknownUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(10L)).isInstanceOf(UserNotFoundException.class);
    }
}