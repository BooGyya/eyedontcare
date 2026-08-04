package org.ssafy.b102.backend.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.group.dto.request.GroupCreateRequest;
import org.ssafy.b102.backend.group.dto.response.GroupDetailResponse;
import org.ssafy.b102.backend.group.dto.response.GroupListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupResponse;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupMember;
import org.ssafy.b102.backend.group.entity.GroupRole;
import org.ssafy.b102.backend.group.entity.GroupVisibility;
import org.ssafy.b102.backend.group.exception.GroupErrorCode;
import org.ssafy.b102.backend.group.repository.GroupMemberRepository;
import org.ssafy.b102.backend.group.repository.GroupRepository;
import org.ssafy.b102.backend.group.support.GroupCodeGenerator;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	private static final Long OWNER_ID = 1L;
	private static final Long MEMBER_ID = 2L;
	private static final Long GROUP_ID = 10L;
	private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

	@Mock
	private GroupRepository groupRepository;

	@Mock
	private GroupMemberRepository groupMemberRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private GroupCodeGenerator groupCodeGenerator;

	private GroupService groupService;

	@BeforeEach
	void setUp() {
		groupService = new GroupService(
			groupRepository,
			groupMemberRepository,
			userRepository,
			groupCodeGenerator,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void 소모임을_생성하면_방장을_OWNER로_등록한다() {
		when(groupCodeGenerator.generate()).thenReturn("A1B2C3");
		when(groupRepository.existsByGroupCode("A1B2C3")).thenReturn(false);
		when(groupRepository.save(any(Group.class)))
			.thenAnswer(invocation -> {
				Group saved = invocation.getArgument(0);
				setField(Group.class, saved, "id", GROUP_ID);
				return saved;
			});
		when(userRepository.findById(OWNER_ID))
			.thenReturn(Optional.of(user(OWNER_ID, "방장")));

		GroupResponse response = groupService.create(
			OWNER_ID,
			new GroupCreateRequest("모임", "소개", GroupVisibility.PUBLIC, 30)
		);

		assertThat(response.groupId()).isEqualTo(GROUP_ID);
		assertThat(response.isOwner()).isTrue();
		assertThat(response.isJoined()).isTrue();
		assertThat(response.members()).isEqualTo(1);
		assertThat(response.joinCode()).isEqualTo("A1B2C3");
		assertThat(response.leader()).isEqualTo("방장");

		ArgumentCaptor<GroupMember> captor =
			ArgumentCaptor.forClass(GroupMember.class);
		verify(groupMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getRole()).isEqualTo(GroupRole.OWNER);
		assertThat(captor.getValue().getJoinedAt()).isEqualTo(NOW);
	}

	@Test
	void capacity가_없으면_기본값_50을_적용한다() {
		when(groupCodeGenerator.generate()).thenReturn("A1B2C3");
		when(groupRepository.existsByGroupCode("A1B2C3")).thenReturn(false);
		when(groupRepository.save(any(Group.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.findById(OWNER_ID))
			.thenReturn(Optional.of(user(OWNER_ID, "방장")));

		GroupResponse response = groupService.create(
			OWNER_ID,
			new GroupCreateRequest("모임", null, GroupVisibility.PRIVATE, null)
		);

		assertThat(response.capacity()).isEqualTo(50);
	}

	@Test
	void 코드가_중복이면_새_코드를_재발급한다() {
		when(groupCodeGenerator.generate()).thenReturn("DUP111", "UNIQ22");
		when(groupRepository.existsByGroupCode("DUP111")).thenReturn(true);
		when(groupRepository.existsByGroupCode("UNIQ22")).thenReturn(false);
		when(groupRepository.save(any(Group.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.findById(OWNER_ID))
			.thenReturn(Optional.of(user(OWNER_ID, "방장")));

		GroupResponse response = groupService.create(
			OWNER_ID,
			new GroupCreateRequest("모임", null, GroupVisibility.PUBLIC, 10)
		);

		assertThat(response.joinCode()).isEqualTo("UNIQ22");
	}

	@Test
	void 코드_생성이_계속_충돌하면_GROUP_009를_던진다() {
		when(groupCodeGenerator.generate()).thenReturn("SAME11");
		when(groupRepository.existsByGroupCode("SAME11")).thenReturn(true);

		assertThatThrownBy(() -> groupService.create(
			OWNER_ID,
			new GroupCreateRequest("모임", null, GroupVisibility.PUBLIC, 10)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
			assertThat(exception.getErrorCode())
				.isEqualTo(GroupErrorCode.GROUP_CODE_GENERATION_FAILED));

		verify(groupRepository, never()).save(any());
	}

	@Test
	void 공개_목록은_비멤버에게_joinCode를_숨긴다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		Page<Group> page = new PageImpl<>(
			List.of(group), PageRequest.of(0, 20), 1);
		when(groupRepository.findByNameContaining(
			eq(""), any())).thenReturn(page);
		when(groupMemberRepository.findByUserIdOrderByJoinedAtAsc(MEMBER_ID))
			.thenReturn(List.of());
		when(userRepository.findAllById(any()))
			.thenReturn(List.of(user(OWNER_ID, "방장")));
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(3);

		GroupListResponse response =
			groupService.getGroups(MEMBER_ID, null, 1, 20);

		GroupResponse item = response.groups().get(0);
		assertThat(item.members()).isEqualTo(3);
		assertThat(item.leader()).isEqualTo("방장");
		assertThat(item.isJoined()).isFalse();
		assertThat(item.isOwner()).isFalse();
		assertThat(item.joinCode()).isNull();
	}

	@Test
	void 전체_목록은_PUBLIC과_PRIVATE을_검색하고_페이지네이션한다() {
		Group publicGroup = group(GroupVisibility.PUBLIC, 30);
		Group privateGroup = group(GroupVisibility.PRIVATE, 30);
		setField(Group.class, privateGroup, "id", 11L);
		Page<Group> page = new PageImpl<>(
			List.of(publicGroup, privateGroup),
			PageRequest.of(1, 2),
			4
		);
		when(groupRepository.findByNameContaining(
			eq("모임"), eq(PageRequest.of(1, 2))
		)).thenReturn(page);
		when(groupMemberRepository.findByUserIdOrderByJoinedAtAsc(MEMBER_ID))
			.thenReturn(List.of());
		when(userRepository.findAllById(any()))
			.thenReturn(List.of(user(OWNER_ID, "방장")));

		GroupListResponse response =
			groupService.getGroups(MEMBER_ID, "모임", 2, 2);

		assertThat(response.groups())
			.extracting(GroupResponse::visibility)
			.containsExactly(GroupVisibility.PUBLIC, GroupVisibility.PRIVATE);
		assertThat(response.page()).isEqualTo(2);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(4);
		assertThat(response.totalPages()).isEqualTo(2);
		assertThat(response.groups())
			.allSatisfy(item -> assertThat(item.joinCode()).isNull());
	}

	@Test
	void PRIVATE_멤버는_전체_목록에서_joinCode를_본다() {
		Group group = group(GroupVisibility.PRIVATE, 30);
		Page<Group> page = new PageImpl<>(
			List.of(group), PageRequest.of(0, 20), 1);
		when(groupRepository.findByNameContaining(
			eq(""), any())).thenReturn(page);
		when(groupMemberRepository.findByUserIdOrderByJoinedAtAsc(MEMBER_ID))
			.thenReturn(List.of(member(MEMBER_ID, GroupRole.MEMBER)));
		when(userRepository.findAllById(any()))
			.thenReturn(List.of(user(OWNER_ID, "방장")));
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(2);

		GroupResponse item = groupService
			.getGroups(MEMBER_ID, null, 1, 20)
			.groups()
			.get(0);

		assertThat(item.visibility()).isEqualTo(GroupVisibility.PRIVATE);
		assertThat(item.isJoined()).isTrue();
		assertThat(item.joinCode()).isEqualTo("A1B2C3");
	}

	@Test
	void 멤버는_상세에서_joinCode와_isOwner를_본다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID))
			.thenReturn(Optional.of(member(OWNER_ID, GroupRole.OWNER)));
		when(groupMemberRepository.findByGroupIdOrderByJoinedAtAsc(GROUP_ID))
			.thenReturn(List.of(member(OWNER_ID, GroupRole.OWNER)));
		when(userRepository.findAllById(any()))
			.thenReturn(List.of(user(OWNER_ID, "방장")));

		GroupDetailResponse response =
			groupService.getGroup(OWNER_ID, GROUP_ID);

		assertThat(response.joinCode()).isEqualTo("A1B2C3");
		assertThat(response.isOwner()).isTrue();
		assertThat(response.isJoined()).isTrue();
		assertThat(response.leader()).isEqualTo("방장");
		assertThat(response.memberList()).hasSize(1);
		assertThat(response.memberList().get(0).nickname()).isEqualTo("방장");
	}

	@Test
	void 비멤버는_상세에서_joinCode가_null이다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(groupMemberRepository.findByGroupIdOrderByJoinedAtAsc(GROUP_ID))
			.thenReturn(List.of(member(OWNER_ID, GroupRole.OWNER)));
		when(userRepository.findAllById(any()))
			.thenReturn(List.of(user(OWNER_ID, "방장")));

		GroupDetailResponse response =
			groupService.getGroup(MEMBER_ID, GROUP_ID);

		assertThat(response.joinCode()).isNull();
		assertThat(response.isJoined()).isFalse();
		assertThat(response.isOwner()).isFalse();
	}

	@Test
	void 없는_소모임_상세는_GROUP_001이다() {
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.getGroup(OWNER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
	}

	@Test
	void 코드로_입장하면_MEMBER로_가입한다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findByGroupCode("A1B2C3"))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(false);
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(1);
		when(userRepository.findById(OWNER_ID))
			.thenReturn(Optional.of(user(OWNER_ID, "방장")));

		GroupResponse response = groupService.join(MEMBER_ID, "A1B2C3");

		assertThat(response.isJoined()).isTrue();
		assertThat(response.isOwner()).isFalse();
		assertThat(response.members()).isEqualTo(2);
		assertThat(response.joinCode()).isEqualTo("A1B2C3");
		assertThat(response.leader()).isEqualTo("방장");
		verify(groupMemberRepository).save(any(GroupMember.class));
	}

	@Test
	void 잘못된_코드는_GROUP_002다() {
		when(groupRepository.findByGroupCode("NOPE00"))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.join(MEMBER_ID, "NOPE00"))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.INVALID_GROUP_CODE));
	}

	@Test
	void 이미_가입한_소모임은_GROUP_004다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findByGroupCode("A1B2C3"))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(true);

		assertThatThrownBy(() -> groupService.join(MEMBER_ID, "A1B2C3"))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.ALREADY_JOINED));
	}

	@Test
	void 정원이_가득_차면_GROUP_003이다() {
		Group group = group(GroupVisibility.PUBLIC, 2);
		when(groupRepository.findByGroupCode("A1B2C3"))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(false);
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(2);

		assertThatThrownBy(() -> groupService.join(MEMBER_ID, "A1B2C3"))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.GROUP_FULL));
		verify(groupMemberRepository, never()).save(any());
	}

	@Test
	void 멤버는_나갈_수_있다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));
		GroupMember membership = member(MEMBER_ID, GroupRole.MEMBER);
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(Optional.of(membership));

		groupService.leave(MEMBER_ID, GROUP_ID);

		verify(groupMemberRepository).delete(membership);
	}

	@Test
	void 방장은_나갈_수_없고_GROUP_007이다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID))
			.thenReturn(Optional.of(member(OWNER_ID, GroupRole.OWNER)));

		assertThatThrownBy(() -> groupService.leave(OWNER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.OWNER_CANNOT_LEAVE));
		verify(groupMemberRepository, never()).delete(any());
	}

	@Test
	void 멤버가_아니면_나가기는_GROUP_005다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.leave(MEMBER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.NOT_A_MEMBER));
	}

	@Test
	void 방장은_소모임을_삭제한다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

		groupService.delete(OWNER_ID, GROUP_ID);

		verify(groupMemberRepository).deleteByGroupId(GROUP_ID);
		verify(groupRepository).delete(group);
	}

	@Test
	void 방장이_아니면_삭제는_GROUP_006이다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));

		assertThatThrownBy(() -> groupService.delete(MEMBER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.NOT_GROUP_OWNER));
		verify(groupRepository, never()).delete(any());
	}

	@Test
	void 방장은_멤버를_강퇴한다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));
		GroupMember target = member(MEMBER_ID, GroupRole.MEMBER);
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(Optional.of(target));

		groupService.kick(OWNER_ID, GROUP_ID, MEMBER_ID);

		verify(groupMemberRepository).delete(target);
	}

	@Test
	void 자기_자신_강퇴는_GROUP_008이다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));

		assertThatThrownBy(() ->
			groupService.kick(OWNER_ID, GROUP_ID, OWNER_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.CANNOT_KICK_SELF));
	}

	@Test
	void 대상이_멤버가_아니면_강퇴는_GROUP_010이다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PUBLIC, 30)));
		when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() ->
			groupService.kick(OWNER_ID, GROUP_ID, MEMBER_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.MEMBER_NOT_FOUND));
	}

	@Test
	void 공개_소모임은_id로_바로_가입한다() {
		Group group = group(GroupVisibility.PUBLIC, 30);
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(false);
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(1);
		when(userRepository.findById(OWNER_ID))
			.thenReturn(Optional.of(user(OWNER_ID, "방장")));

		GroupResponse response = groupService.joinById(MEMBER_ID, GROUP_ID);

		assertThat(response.isJoined()).isTrue();
		assertThat(response.isOwner()).isFalse();
		assertThat(response.members()).isEqualTo(2);
		assertThat(response.leader()).isEqualTo("방장");
		verify(groupMemberRepository).save(any(GroupMember.class));
	}

	@Test
	void 비공개_소모임_id_가입은_GROUP_011이다() {
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group(GroupVisibility.PRIVATE, 30)));

		assertThatThrownBy(() -> groupService.joinById(MEMBER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.PRIVATE_GROUP_REQUIRES_CODE));
		verify(groupMemberRepository, never()).save(any());
	}

	@Test
	void id_가입도_정원이_가득_차면_GROUP_003이다() {
		Group group = group(GroupVisibility.PUBLIC, 2);
		when(groupRepository.findById(GROUP_ID))
			.thenReturn(Optional.of(group));
		when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
			.thenReturn(false);
		when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(2);

		assertThatThrownBy(() -> groupService.joinById(MEMBER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.GROUP_FULL));
		verify(groupMemberRepository, never()).save(any());
	}

	@Test
	void 없는_소모임_id_가입은_GROUP_001이다() {
		when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.joinById(MEMBER_ID, GROUP_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
	}

	private static Group group(GroupVisibility visibility, int capacity) {
		Group group = Group.create(
			"모임", "소개", "A1B2C3", OWNER_ID, visibility, capacity);
		setField(Group.class, group, "id", GROUP_ID);
		return group;
	}

	private static GroupMember member(Long userId, GroupRole role) {
		return GroupMember.of(GROUP_ID, userId, role, NOW);
	}

	private static User user(Long id, String nickname) {
		User user = User.createSocial(nickname);
		setField(User.class, user, "id", id);
		return user;
	}

	private static void setField(
		Class<?> declaringClass,
		Object target,
		String fieldName,
		Object value
	) {
		try {
			Field field = declaringClass.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (NoSuchFieldException | IllegalAccessException exception) {
			throw new IllegalStateException(
				"테스트 필드 설정에 실패했습니다.",
				exception
			);
		}
	}
}
