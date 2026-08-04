package org.ssafy.b102.backend.group.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.group.dto.request.GroupCreateRequest;
import org.ssafy.b102.backend.group.dto.response.GroupDetailResponse;
import org.ssafy.b102.backend.group.dto.response.GroupListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupMemberResponse;
import org.ssafy.b102.backend.group.dto.response.GroupResponse;
import org.ssafy.b102.backend.group.dto.response.MyGroupListResponse;
import org.ssafy.b102.backend.group.entity.GroupRole;
import org.ssafy.b102.backend.group.entity.GroupVisibility;
import org.ssafy.b102.backend.group.exception.GroupErrorCode;
import org.ssafy.b102.backend.group.service.GroupService;

class GroupControllerTest {

	private static final Long USER_ID = 1L;
	private static final Instant CREATED_AT =
		Instant.parse("2026-08-03T00:00:00Z");

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 소모임을_생성하면_201과_방장정보를_반환한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.groupResponse = new GroupResponse(
			10L, "모임", "소개", 1, 30, GroupVisibility.PUBLIC,
			"방장", true, true, "A1B2C3", CREATED_AT
		);

		mockMvc(service)
			.perform(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"모임","description":"소개",
					 "visibility":"PUBLIC","capacity":30}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("GROUP_CREATE_SUCCESS"))
			.andExpect(jsonPath("$.data.groupId").value(10))
			.andExpect(jsonPath("$.data.isOwner").value(true))
			.andExpect(jsonPath("$.data.joinCode").value("A1B2C3"))
			.andExpect(jsonPath("$.data.leader").value("방장"));

		assertThat(service.capturedUserId).isEqualTo(USER_ID);
	}

	@Test
	void 이름이_비면_COMMON_001이다() throws Exception {
		mockMvc(new RecordingGroupService())
			.perform(post("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"visibility\":\"PUBLIC\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void 공개_목록을_조회한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.listResponse = new GroupListResponse(
			List.of(new GroupResponse(
				10L, "모임", "소개", 3, 30, GroupVisibility.PUBLIC,
				"방장", false, false, null, CREATED_AT)),
			1, 20, 1, 1
		);

		mockMvc(service)
			.perform(get("/api/v1/groups").param("keyword", "모"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_LIST_FOUND"))
			.andExpect(jsonPath("$.data.groups[0].groupId").value(10))
			.andExpect(jsonPath("$.data.groups[0].members").value(3))
			.andExpect(jsonPath("$.data.groups[0].isJoined").value(false))
			.andExpect(jsonPath("$.data.groups[0].joinCode").doesNotExist());

		assertThat(service.capturedKeyword).isEqualTo("모");
	}

	@Test
	void 내_소모임을_조회한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.myListResponse = new MyGroupListResponse(List.of(
			new GroupResponse(
				10L, "모임", "소개", 3, 30, GroupVisibility.PUBLIC,
				"방장", true, true, "A1B2C3", CREATED_AT)
		));

		mockMvc(service)
			.perform(get("/api/v1/groups/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("MY_GROUP_LIST_FOUND"))
			.andExpect(jsonPath("$.data.groups[0].isOwner").value(true));
	}

	@Test
	void 상세를_조회한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.detailResponse = new GroupDetailResponse(
			10L, "모임", "소개", 1, 30, GroupVisibility.PUBLIC,
			"방장", true, true, "A1B2C3", CREATED_AT,
			List.of(new GroupMemberResponse(
				USER_ID, "방장", GroupRole.OWNER, CREATED_AT))
		);

		mockMvc(service)
			.perform(get("/api/v1/groups/{groupId}", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_DETAIL_FOUND"))
			.andExpect(jsonPath("$.data.memberList[0].nickname").value("방장"));

		assertThat(service.capturedGroupId).isEqualTo(10L);
	}

	@Test
	void 없는_소모임_상세는_404_GROUP_001이다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.toThrow = new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);

		mockMvc(service)
			.perform(get("/api/v1/groups/{groupId}", 99L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP-001"));
	}

	@Test
	void 코드로_입장한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.groupResponse = new GroupResponse(
			10L, "모임", null, 2, 30, GroupVisibility.PUBLIC,
			"방장", false, true, "A1B2C3", CREATED_AT
		);

		mockMvc(service)
			.perform(post("/api/v1/groups/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"groupCode\":\"A1B2C3\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_JOIN_SUCCESS"))
			.andExpect(jsonPath("$.data.isJoined").value(true))
			.andExpect(jsonPath("$.data.isOwner").value(false));

		assertThat(service.capturedGroupCode).isEqualTo("A1B2C3");
	}

	@Test
	void 공개_소모임에_id로_입장한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.groupResponse = new GroupResponse(
			10L, "모임", null, 2, 30, GroupVisibility.PUBLIC,
			"방장", false, true, "A1B2C3", CREATED_AT
		);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/join", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_JOIN_SUCCESS"))
			.andExpect(jsonPath("$.data.isJoined").value(true))
			.andExpect(jsonPath("$.data.isOwner").value(false));

		assertThat(service.capturedGroupId).isEqualTo(10L);
	}

	@Test
	void 비공개_소모임_id_입장은_403_GROUP_011이다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.toThrow =
			new BusinessException(GroupErrorCode.PRIVATE_GROUP_REQUIRES_CODE);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/join", 10L))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("GROUP-011"));
	}

	@Test
	void 나간다() throws Exception {
		mockMvc(new RecordingGroupService())
			.perform(post("/api/v1/groups/{groupId}/leave", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_LEAVE_SUCCESS"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void 방장_나가기는_409_GROUP_007이다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.toThrow =
			new BusinessException(GroupErrorCode.OWNER_CANNOT_LEAVE);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/leave", 10L))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GROUP-007"));
	}

	@Test
	void 삭제한다() throws Exception {
		mockMvc(new RecordingGroupService())
			.perform(delete("/api/v1/groups/{groupId}", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_DELETE_SUCCESS"));
	}

	@Test
	void 방장이_아니면_삭제는_403_GROUP_006이다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.toThrow =
			new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);

		mockMvc(service)
			.perform(delete("/api/v1/groups/{groupId}", 10L))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("GROUP-006"));
	}

	@Test
	void 멤버를_강퇴한다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();

		mockMvc(service)
			.perform(delete("/api/v1/groups/{groupId}/members/{userId}", 10L, 2L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_MEMBER_KICK_SUCCESS"));

		assertThat(service.capturedGroupId).isEqualTo(10L);
		assertThat(service.capturedTargetUserId).isEqualTo(2L);
	}

	@Test
	void 자기_강퇴는_409_GROUP_008이다() throws Exception {
		RecordingGroupService service = new RecordingGroupService();
		service.toThrow =
			new BusinessException(GroupErrorCode.CANNOT_KICK_SELF);

		mockMvc(service)
			.perform(delete("/api/v1/groups/{groupId}/members/{userId}", 10L, 1L))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GROUP-008"));
	}

	private MockMvc mockMvc(GroupService service) {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(USER_ID),
				null,
				List.of()
			)
		);

		return MockMvcBuilders
			.standaloneSetup(new GroupController(service))
			.setCustomArgumentResolvers(
				new AuthenticationPrincipalArgumentResolver())
			.setValidator(validator())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static LocalValidatorFactoryBean validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		return validator;
	}

	private static class RecordingGroupService extends GroupService {

		private BusinessException toThrow;
		private GroupResponse groupResponse;
		private GroupListResponse listResponse;
		private GroupDetailResponse detailResponse;
		private MyGroupListResponse myListResponse;

		private Long capturedUserId;
		private Long capturedGroupId;
		private Long capturedTargetUserId;
		private String capturedKeyword;
		private String capturedGroupCode;

		private RecordingGroupService() {
			super(null, null, null, null);
		}

		private void maybeThrow() {
			if (toThrow != null) {
				throw toThrow;
			}
		}

		@Override
		public GroupResponse create(Long userId, GroupCreateRequest request) {
			maybeThrow();
			this.capturedUserId = userId;
			return groupResponse;
		}

		@Override
		public GroupListResponse getGroups(
			Long userId, String keyword, int page, int size
		) {
			maybeThrow();
			this.capturedUserId = userId;
			this.capturedKeyword = keyword;
			return listResponse;
		}

		@Override
		public GroupDetailResponse getGroup(Long userId, Long groupId) {
			maybeThrow();
			this.capturedGroupId = groupId;
			return detailResponse;
		}

		@Override
		public GroupResponse join(Long userId, String groupCode) {
			maybeThrow();
			this.capturedGroupCode = groupCode;
			return groupResponse;
		}

		@Override
		public GroupResponse joinById(Long userId, Long groupId) {
			maybeThrow();
			this.capturedGroupId = groupId;
			return groupResponse;
		}

		@Override
		public MyGroupListResponse getMyGroups(Long userId) {
			maybeThrow();
			return myListResponse;
		}

		@Override
		public void leave(Long userId, Long groupId) {
			maybeThrow();
			this.capturedGroupId = groupId;
		}

		@Override
		public void delete(Long userId, Long groupId) {
			maybeThrow();
			this.capturedGroupId = groupId;
		}

		@Override
		public void kick(Long userId, Long groupId, Long targetUserId) {
			maybeThrow();
			this.capturedGroupId = groupId;
			this.capturedTargetUserId = targetUserId;
		}
	}
}
