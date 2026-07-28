package org.ssafy.b102.testfixture.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAuditEntityRepository extends JpaRepository<TestAuditEntity, Long> {
}
