package family.fisa.hangangpay.domain.transaction.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 멱등성 레코드. idempotency_key(UNIQUE)로 실행을 원자적으로 선점하고, 완료 응답을 response_json에 보관해 재요청 시 그대로 반환한다.
 *
 * <p>선점 INSERT는 native 쿼리로 처리하므로 이 엔티티는 조회 전용이다.
 */
@Entity
@Table(
        name = "idempotency",
        indexes = {@Index(name = "idx_idempotency_ttl_expiry", columnList = "ttl_expiry")})
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Idempotency extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 선점 키 = "{flow}:idempotency:{transactionUuid}" */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    /** 거래 식별자 */
    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    /** 요청 해시 (충돌 감지용, 없으면 null) */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    /** 거래 PK */
    @Column(name = "transaction_id")
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /** 완료 응답 snapshot(JSON). 완료 전 null. */
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    /** 만료 시각 (정리 배치용) */
    @Column(name = "ttl_expiry", nullable = false)
    private LocalDateTime ttlExpiry;
}
