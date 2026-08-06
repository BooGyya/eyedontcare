package org.ssafy.b102.backend.group.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.group.entity.GroupComment;

public interface GroupCommentRepository
	extends JpaRepository<GroupComment, Long> {

	List<GroupComment> findByPostIdInOrderByCreatedAtAsc(
		Collection<Long> postIds
	);

	void deleteByPostId(Long postId);
}
