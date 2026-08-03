package org.ssafy.b102.backend.group.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.group.dto.request.GroupCreateRequest;
import org.ssafy.b102.backend.group.dto.response.GroupDetailResponse;
import org.ssafy.b102.backend.group.dto.response.GroupListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupMemberResponse;
import org.ssafy.b102.backend.group.dto.response.GroupResponse;
import org.ssafy.b102.backend.group.dto.response.GroupSummaryResponse;
import org.ssafy.b102.backend.group.dto.response.MyGroupListResponse;
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

/**
 * 소모임 도메인 서비스.
 *
 * <p>모든 기능은 인증된 회원 기준이다. 방장은 삭제·강퇴만 할 수 있고 나가기는 할 수 없으며,
 * 정원·중복 가입은 저장 전에 검증하되 동시성 최종 방어는 (group_id, user_id) 유니크 제약이 맡는다.
 */
@Service
public class GroupService {

	private static final int DEFAULT_CAPACITY = 50;
	private static final int MAX_CODE_ATTEMPTS = 10;

	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final UserRepository userRepository;
	private final GroupCodeGenerator groupCodeGenerator;
	private final Clock clock;

	@Autowired
	public GroupService(
		GroupRepository groupRepository,
		GroupMemberRepository groupMemberRepository,
		UserRepository userRepository,
		GroupCodeGenerator groupCodeGenerator
	) {
		this(
			groupRepository,
			groupMemberRepository,
			userRepository,
			groupCodeGenerator,
			Clock.systemUTC()
		);
	}

	GroupService(
		GroupRepository groupRepository,
		GroupMemberRepository groupMemberRepository,
		UserRepository userRepository,
		GroupCodeGenerator groupCodeGenerator,
		Clock clock
	) {
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.userRepository = userRepository;
		this.groupCodeGenerator = groupCodeGenerator;
		this.clock = clock;
	}

	@Transactional
	public GroupResponse create(Long userId, GroupCreateRequest request) {
		int capacity = request.capacity() == null
			? DEFAULT_CAPACITY
			: request.capacity();

		Group group = groupRepository.save(
			Group.create(
				request.name(),
				request.description(),
				generateUniqueCode(),
				userId,
				request.visibility(),
				capacity
			)
		);

		groupMemberRepository.save(
			GroupMember.of(
				group.getId(),
				userId,
				GroupRole.OWNER,
				clock.instant()
			)
		);

		return GroupResponse.of(group, 1, GroupRole.OWNER);
	}

	@Transactional(readOnly = true)
	public GroupListResponse getGroups(String keyword, int page, int size) {
		Pageable pageable = PageRequest.of(page - 1, size);
		String normalizedKeyword = keyword == null ? "" : keyword;

		Page<Group> groups = groupRepository
			.findByVisibilityAndNameContaining(
				GroupVisibility.PUBLIC,
				normalizedKeyword,
				pageable
			);

		List<GroupSummaryResponse> items = groups.getContent().stream()
			.map(group -> GroupSummaryResponse.of(
				group,
				groupMemberRepository.countByGroupId(group.getId()),
				null
			))
			.toList();

		return new GroupListResponse(
			items,
			page,
			size,
			groups.getTotalElements(),
			groups.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public GroupDetailResponse getGroup(Long userId, Long groupId) {
		Group group = findGroupOrThrow(groupId);

		GroupRole myRole = groupMemberRepository
			.findByGroupIdAndUserId(groupId, userId)
			.map(GroupMember::getRole)
			.orElse(null);
		String groupCodeForMember = myRole == null
			? null
			: group.getGroupCode();

		List<GroupMember> members = groupMemberRepository
			.findByGroupIdOrderByJoinedAtAsc(groupId);
		Map<Long, String> nicknames = loadNicknames(members);

		List<GroupMemberResponse> memberResponses = members.stream()
			.map(member -> new GroupMemberResponse(
				member.getUserId(),
				nicknames.get(member.getUserId()),
				member.getRole(),
				member.getJoinedAt()
			))
			.toList();

		return GroupDetailResponse.of(
			group,
			groupCodeForMember,
			myRole,
			memberResponses
		);
	}

	@Transactional
	public GroupResponse join(Long userId, String groupCode) {
		Group group = groupRepository.findByGroupCode(groupCode)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.INVALID_GROUP_CODE));

		if (groupMemberRepository.existsByGroupIdAndUserId(
			group.getId(),
			userId
		)) {
			throw new BusinessException(GroupErrorCode.ALREADY_JOINED);
		}

		int memberCount = groupMemberRepository.countByGroupId(group.getId());
		if (memberCount >= group.getCapacity()) {
			throw new BusinessException(GroupErrorCode.GROUP_FULL);
		}

		groupMemberRepository.save(
			GroupMember.of(
				group.getId(),
				userId,
				GroupRole.MEMBER,
				clock.instant()
			)
		);

		return GroupResponse.of(group, memberCount + 1, GroupRole.MEMBER);
	}

	@Transactional(readOnly = true)
	public MyGroupListResponse getMyGroups(Long userId) {
		List<GroupMember> memberships = groupMemberRepository
			.findByUserIdOrderByJoinedAtAsc(userId);

		Map<Long, Group> groups = groupRepository
			.findAllById(memberships.stream()
				.map(GroupMember::getGroupId)
				.toList())
			.stream()
			.collect(Collectors.toMap(Group::getId, Function.identity()));

		List<GroupSummaryResponse> items = memberships.stream()
			.map(membership -> groups.get(membership.getGroupId()))
			.filter(Objects::nonNull)
			.map(group -> GroupSummaryResponse.of(
				group,
				groupMemberRepository.countByGroupId(group.getId()),
				resolveRole(memberships, group.getId())
			))
			.toList();

		return new MyGroupListResponse(items);
	}

	@Transactional
	public void leave(Long userId, Long groupId) {
		findGroupOrThrow(groupId);

		GroupMember membership = groupMemberRepository
			.findByGroupIdAndUserId(groupId, userId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.NOT_A_MEMBER));

		if (membership.getRole() == GroupRole.OWNER) {
			throw new BusinessException(GroupErrorCode.OWNER_CANNOT_LEAVE);
		}

		groupMemberRepository.delete(membership);
	}

	@Transactional
	public void delete(Long userId, Long groupId) {
		Group group = findGroupOrThrow(groupId);
		requireOwner(group, userId);

		groupMemberRepository.deleteByGroupId(groupId);
		groupRepository.delete(group);
	}

	@Transactional
	public void kick(Long userId, Long groupId, Long targetUserId) {
		Group group = findGroupOrThrow(groupId);
		requireOwner(group, userId);

		if (Objects.equals(userId, targetUserId)) {
			throw new BusinessException(GroupErrorCode.CANNOT_KICK_SELF);
		}

		GroupMember target = groupMemberRepository
			.findByGroupIdAndUserId(groupId, targetUserId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.MEMBER_NOT_FOUND));

		groupMemberRepository.delete(target);
	}

	private Group findGroupOrThrow(Long groupId) {
		return groupRepository.findById(groupId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
	}

	private void requireOwner(Group group, Long userId) {
		if (!group.isOwner(userId)) {
			throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
		}
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String code = groupCodeGenerator.generate();
			if (!groupRepository.existsByGroupCode(code)) {
				return code;
			}
		}
		throw new BusinessException(
			GroupErrorCode.GROUP_CODE_GENERATION_FAILED
		);
	}

	private Map<Long, String> loadNicknames(List<GroupMember> members) {
		List<Long> userIds = members.stream()
			.map(GroupMember::getUserId)
			.toList();

		return userRepository.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, User::getNickname));
	}

	private GroupRole resolveRole(
		List<GroupMember> memberships,
		Long groupId
	) {
		return memberships.stream()
			.filter(membership ->
				membership.getGroupId().equals(groupId))
			.map(GroupMember::getRole)
			.findFirst()
			.orElse(null);
	}
}
