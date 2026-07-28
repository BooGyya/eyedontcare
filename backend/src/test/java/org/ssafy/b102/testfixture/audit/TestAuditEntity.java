package org.ssafy.b102.testfixture.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

@Entity
@Table(name = "test_audit_entity")
public class TestAuditEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	protected TestAuditEntity() {
	}

	public TestAuditEntity(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public void changeName(String name) {
		this.name = name;
	}
}
