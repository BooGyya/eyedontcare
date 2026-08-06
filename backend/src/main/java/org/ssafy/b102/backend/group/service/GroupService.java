package org.ssafy.b102.backend.group.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.group.dto.request.GroupCreateRequest;
import org.ssafy.b102.backend.group.dto.response.GroupDetailResponse;
import org.ssafy.b102.backend.group.dto.response.GroupListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupMemberResponse;
import org.ssafy.b102.backend.group.dto.response.GroupResponse;
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
 * 길드 도메인 서비스.
 *
 * <p>모든 기능은 인증된 회원 기준이다. 응답은 프론트 CommunityGroup 모양에 맞춰
 * 인원수(members)·방장 닉네임(leader)·요청자 상태(isOwner/isJoined)·입장 코드(joinCode)를 담는다.
 * 방장은 삭제·강퇴만 할 수 있고 나가기는 할 수 없으며, 정원·중복 가입은 저장 전에 검증하되
 * 동시성 최종 방어는 (group_id, user_id) 유니크 제약이 맡는다.
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

		return GroupResponse.of(group, 1, nickname(userId), true, true);
	}

	@Transactional(readOnly = true)
	public GroupListResponse getGroups(
		Long userId,
		String keyword,
		int page,
		int size
	) {
		// 최신 길드가 먼저 보이도록 정렬 — 정렬이 없으면 새 길드가 페이지 밖으로 밀려 안 보인다.
		Pageable pageable = PageRequest.of(
			page - 1,
			size,
			Sort.by(Sort.Direction.DESC, "createdAt")
		);
		String normalizedKeyword = keyword == null ? "" : keyword;

		Page<Group> groups = groupRepository
			.findByNameContaining(
				normalizedKeyword,
				pageable
			);

		Map<Long, GroupRole> myRoles = myRolesByGroup(userId);
		Map<Long, String> leaders = leadersOf(groups.getContent());

		List<GroupResponse> items = groups.getContent().stream()
			.map(group -> toResponse(
				group,
				leaders.get(group.getOwnerUserId()),
				myRoles.get(group.getId())
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

		// 비공개 길드는 로그인만으로 상세에 접근할 수 없다. 길드원(방장 포함)만 볼 수 있고,
		// 비회원은 403으로 막는다(공개 길드는 비회원도 조회 가능).
		if (group.getVisibility() != GroupVisibility.PUBLIC && myRole == null) {
			throw new BusinessException(GroupErrorCode.PRIVATE_GROUP_MEMBER_ONLY);
		}

		List<GroupMember> members = groupMemberRepository
			.findByGroupIdOrderByJoinedAtAsc(groupId);

		List<Long> nicknameTargets = new ArrayList<>(members.stream()
			.map(GroupMember::getUserId)
			.toList());
		nicknameTargets.add(group.getOwnerUserId());
		Map<Long, String> nicknames = nicknamesOf(nicknameTargets);

		List<GroupMemberResponse> memberList = members.stream()
			.map(member -> new GroupMemberResponse(
				member.getUserId(),
				nicknames.get(member.getUserId()),
				member.getRole(),
				member.getJoinedAt()
			))
			.toList();

		return GroupDetailResponse.of(
			group,
			members.size(),
			nicknames.get(group.getOwnerUserId()),
			myRole == GroupRole.OWNER,
			myRole != null,
			memberList
		);
	}

	@Transactional
	public GroupResponse join(Long userId, String groupCode) {
		Group group = groupRepository.findByGroupCode(groupCode)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.INVALID_GROUP_CODE));

		return joinAsMember(group, userId);
	}

	/**
	 * 공개 길드를 id로 바로 가입한다(코드 없이). 비공개 길드는 코드로만 입장할 수 있으므로
	 * 거부한다 — 코드 입장은 {@link #join(Long, String)}이 담당한다.
	 */
	@Transactional
	public GroupResponse joinById(Long userId, Long groupId) {
		Group group = findGroupOrThrow(groupId);

		if (group.getVisibility() != GroupVisibility.PUBLIC) {
			throw new BusinessException(
				GroupErrorCode.PRIVATE_GROUP_REQUIRES_CODE);
		}

		return joinAsMember(group, userId);
	}

	/**
	 * 중복 가입·정원 초과를 검증하고 MEMBER로 저장한다. 코드 입장과 공개 id 입장이 공유한다.
	 * 동시성 최종 방어는 (group_id, user_id) 유니크 제약이 맡는다.
	 */
	private GroupResponse joinAsMember(Group group, Long userId) {
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

		return GroupResponse.of(
			group,
			memberCount + 1,
			nickname(group.getOwnerUserId()),
			false,
			true
		);
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

		Map<Long, String> leaders = leadersOf(groups.values());

		List<GroupResponse> items = memberships.stream()
			.filter(membership -> groups.containsKey(membership.getGroupId()))
			.map(membership -> {
				Group group = groups.get(membership.getGroupId());
				return toResponse(
					group,
					leaders.get(group.getOwnerUserId()),
					membership.getRole()
				);
			})
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

	private GroupResponse toResponse(
		Group group,
		String leader,
		GroupRole myRole
	) {
		return GroupResponse.of(
			group,
			groupMemberRepository.countByGroupId(group.getId()),
			leader,
			myRole == GroupRole.OWNER,
			myRole != null
		);
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

	private Map<Long, GroupRole> myRolesByGroup(Long userId) {
		return groupMemberRepository
			.findByUserIdOrderByJoinedAtAsc(userId)
			.stream()
			.collect(Collectors.toMap(
				GroupMember::getGroupId,
				GroupMember::getRole
			));
	}

	private Map<Long, String> leadersOf(Collection<Group> groups) {
		return nicknamesOf(groups.stream()
			.map(Group::getOwnerUserId)
			.toList());
	}

	private Map<Long, String> nicknamesOf(Collection<Long> userIds) {
		return userRepository.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, User::getNickname));
	}

	private String nickname(Long userId) {
		return userRepository.findById(userId)
			.map(User::getNickname)
			.orElse(null);
	}
}
