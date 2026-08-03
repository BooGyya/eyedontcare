package org.ssafy.b102.backend.group.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

public interface GroupRepository extends JpaRepository<Group, Long> {

	boolean existsByGroupCode(String groupCode);

	Optional<Group> findByGroupCode(String groupCode);

	Page<Group> findByVisibilityAndNameContaining(
		GroupVisibility visibility,
		String keyword,
		Pageable pageable
	);
}
