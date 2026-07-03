package family.fisa.hangangpaybank.domain.blockchain.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BlockchainErrorCode implements BaseErrorCode {
    BLOCKCHAIN_RPC_FAILED(HttpStatus.BAD_GATEWAY, "BLOCKCHAIN_RPC_FAILED", "블록체인 RPC 요청에 실패했습니다."),
    BLOCKCHAIN_TRANSACTION_REVERTED(
            HttpStatus.BAD_GATEWAY, "BLOCKCHAIN_TRANSACTION_REVERTED", "블록체인 트랜잭션이 실패했습니다."),
    BLOCKCHAIN_RECEIPT_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT, "BLOCKCHAIN_RECEIPT_TIMEOUT", "블록체인 트랜잭션 확인 시간이 초과되었습니다."),
    BLOCKCHAIN_LEDGER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "BLOCKCHAIN_LEDGER_NOT_FOUND", "블록체인 거래를 찾을 수 없습니다."),
    BLOCKCHAIN_CONTRACT_NOT_FOUND(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_CONTRACT_NOT_FOUND", "컨트랙트를 찾을 수 없습니다."),
    BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID(
            HttpStatus.BAD_REQUEST,
            "BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID",
            "컨트랙트 배포 결과가 올바르지 않습니다."),
    BLOCKCHAIN_UNAUTHORIZED(HttpStatus.FORBIDDEN, "BLOCKCHAIN_UNAUTHORIZED", "컨트랙트 호출 권한이 없습니다."),

    BLOCKCHAIN_INVALID_ADDRESS(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_ADDRESS", "유효하지 않은 주소입니다."),

    BLOCKCHAIN_INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_AMOUNT", "유효하지 않은 금액입니다."),

    BLOCKCHAIN_INVALID_INSTITUTION_ID(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_INSTITUTION_ID", "유효하지 않은 기관 ID입니다."),

    BLOCKCHAIN_MERCHANT_NOT_REGISTERED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_MERCHANT_NOT_REGISTERED", "등록되지 않은 가맹점입니다."),

    BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE", "토큰 잔액이 부족합니다."),

    BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED", "지역화폐 발행 한도를 초과했습니다."),

    BLOCKCHAIN_INSUFFICIENT_RESERVE(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INSUFFICIENT_RESERVE", "지급준비금 잔액이 부족합니다."),

    BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC(
            HttpStatus.BAD_REQUEST,
            "BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC",
            "지급준비금이 예치된 CBDC를 초과합니다."),

    BLOCKCHAIN_RESERVE_MOVE_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_RESERVE_MOVE_FAILED", "지급준비금 이동에 실패했습니다."),

    BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED", "예금토큰 발행에 실패했습니다."),

    BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED", "예금토큰 소각에 실패했습니다."),

    BLOCKCHAIN_TRANSFER_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_TRANSFER_FAILED", "토큰 이체에 실패했습니다."),

    BLOCKCHAIN_BANK_NOT_REGISTERED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_BANK_NOT_REGISTERED", "등록되지 않은 은행입니다."),

    BLOCKCHAIN_ALREADY_PROCESSED(
            HttpStatus.CONFLICT, "BLOCKCHAIN_ALREADY_PROCESSED", "이미 온체인에서 처리된 트랜잭션입니다."),

    BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND",
            "처리할 수 없는 블록체인 동기화 타입입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
