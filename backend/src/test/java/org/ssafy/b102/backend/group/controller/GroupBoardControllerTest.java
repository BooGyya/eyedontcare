package org.ssafy.b102.backend.group.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.ssafy.b102.backend.group.dto.response.GroupCommentResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostResponse;
import org.ssafy.b102.backend.group.exception.GroupErrorCode;
import org.ssafy.b102.backend.group.service.GroupBoardService;

class GroupBoardControllerTest {

	private static final Long USER_ID = 1L;
	private static final Instant CREATED_AT =
		Instant.parse("2026-08-03T00:00:00Z");

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 후기_목록을_조회한다() throws Exception {
		RecordingBoardService service = new RecordingBoardService();
		service.listResponse = new GroupPostListResponse(List.of(
			new GroupPostResponse(
				5L, "방장", true, "첫 후기", CREATED_AT,
				List.of(new GroupCommentResponse(
					9L, "멤버", "좋아요", CREATED_AT)))
		));

		mockMvc(service)
			.perform(get("/api/v1/groups/{groupId}/posts", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("GROUP_POST_LIST_FOUND"))
			.andExpect(jsonPath("$.data.posts[0].postId").value(5))
			.andExpect(jsonPath("$.data.posts[0].isLeader").value(true))
			.andExpect(jsonPath("$.data.posts[0].comments[0].author")
				.value("멤버"));

		assertThat(service.capturedGroupId).isEqualTo(10L);
	}

	@Test
	void 후기를_작성하면_201을_반환한다() throws Exception {
		RecordingBoardService service = new RecordingBoardService();
		service.postResponse = new GroupPostResponse(
			5L, "나", false, "새 후기", CREATED_AT, List.of());

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/posts", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"새 후기\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("GROUP_POST_CREATE_SUCCESS"))
			.andExpect(jsonPath("$.data.postId").value(5));

		assertThat(service.capturedContent).isEqualTo("새 후기");
	}

	@Test
	void 내용이_비면_후기_작성은_COMMON_001이다() throws Exception {
		mockMvc(new RecordingBoardService())
			.perform(post("/api/v1/groups/{groupId}/posts", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON-001"));
	}

	@Test
	void 가입자가_아니면_후기_작성은_403_GROUP_005이다() throws Exception {
		RecordingBoardService service = new RecordingBoardService();
		service.toThrow = new BusinessException(GroupErrorCode.NOT_A_MEMBER);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/posts", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"후기\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("GROUP-005"));
	}

	@Test
	void 댓글을_작성하면_201을_반환한다() throws Exception {
		RecordingBoardService service = new RecordingBoardService();
		service.commentResponse =
			new GroupCommentResponse(9L, "나", "좋아요", CREATED_AT);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/posts/{postId}/comments",
				10L, 5L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"좋아요\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("GROUP_COMMENT_CREATE_SUCCESS"))
			.andExpect(jsonPath("$.data.commentId").value(9));

		assertThat(service.capturedPostId).isEqualTo(5L);
		assertThat(service.capturedContent).isEqualTo("좋아요");
	}

	@Test
	void 없는_후기에_댓글은_404_GROUP_012이다() throws Exception {
		RecordingBoardService service = new RecordingBoardService();
		service.toThrow = new BusinessException(GroupErrorCode.POST_NOT_FOUND);

		mockMvc(service)
			.perform(post("/api/v1/groups/{groupId}/posts/{postId}/comments",
				10L, 99L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"좋아요\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP-012"));
	}

	private MockMvc mockMvc(GroupBoardService service) {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(USER_ID),
				null,
				List.of()
			)
		);

		return MockMvcBuilders
			.standaloneSetup(new GroupBoardController(service))
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

	private static class RecordingBoardService extends GroupBoardService {

		private BusinessException toThrow;
		private GroupPostListResponse listResponse;
		private GroupPostResponse postResponse;
		private GroupCommentResponse commentResponse;

		private Long capturedGroupId;
		private Long capturedPostId;
		private String capturedContent;

		private RecordingBoardService() {
			super(null, null, null, null, null);
		}

		private void maybeThrow() {
			if (toThrow != null) {
				throw toThrow;
			}
		}

		@Override
		public GroupPostListResponse getPosts(Long userId, Long groupId) {
			maybeThrow();
			this.capturedGroupId = groupId;
			return listResponse;
		}

		@Override
		public GroupPostResponse createPost(
			Long userId, Long groupId, String content
		) {
			maybeThrow();
			this.capturedGroupId = groupId;
			this.capturedContent = content;
			return postResponse;
		}

		@Override
		public GroupCommentResponse createComment(
			Long userId, Long groupId, Long postId, String content
		) {
			maybeThrow();
			this.capturedGroupId = groupId;
			this.capturedPostId = postId;
			this.capturedContent = content;
			return commentResponse;
		}
	}
}
