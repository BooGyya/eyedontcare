package org.ssafy.b102.backend.group.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.group.entity.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

	boolean existsByGroupCode(String groupCode);

	boolean existsByName(String name);

	Optional<Group> findByGroupCode(String groupCode);

	Page<Group> findByNameContaining(
		String keyword,
		Pageable pageable
	);
}
