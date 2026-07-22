package org.ssafy.b102.backend.global.common.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.ssafy.b102.backend.global.config.JpaAuditingConfig;
import org.ssafy.b102.testfixture.audit.TestAuditEntity;
import org.ssafy.b102.testfixture.audit.TestAuditEntityRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@EntityScan(basePackageClasses = TestAuditEntity.class)
@EnableJpaRepositories(basePackageClasses = TestAuditEntityRepository.class)
class BaseTimeEntityIntegrationTest {

	@Autowired
	private TestAuditEntityRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void saveAutomaticallySetsCreatedAtAndUpdatedAt() {
		TestAuditEntity entity = repository.saveAndFlush(new TestAuditEntity("before"));

		assertThat(entity.getCreatedAt()).isNotNull();
		assertThat(entity.getUpdatedAt()).isNotNull();
		assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
	}

	@Test
	void updateChangesOnlyUpdatedAt() throws InterruptedException {
		TestAuditEntity entity = repository.saveAndFlush(new TestAuditEntity("before"));
		Instant createdAt = entity.getCreatedAt();
		Instant firstUpdatedAt = entity.getUpdatedAt();

		Thread.sleep(10);
		entity.changeName("after");
		repository.saveAndFlush(entity);
		entityManager.clear();

		TestAuditEntity updatedEntity = repository.findById(entity.getId()).orElseThrow();
		assertThat(updatedEntity.getCreatedAt()).isCloseTo(createdAt, within(1, ChronoUnit.MICROS));
		assertThat(updatedEntity.getUpdatedAt()).isAfter(firstUpdatedAt);
	}
}
