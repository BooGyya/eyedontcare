package org.ssafy.b102.backend.group.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.group.entity.GroupMember;

public interface GroupMemberRepository
	extends JpaRepository<GroupMember, Long> {

	boolean existsByGroupIdAndUserId(Long groupId, Long userId);

	Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

	int countByGroupId(Long groupId);

	List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);

	List<GroupMember> findByUserIdOrderByJoinedAtAsc(Long userId);

	void deleteByGroupId(Long groupId);
}
