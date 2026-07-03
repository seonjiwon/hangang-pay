package family.fisa.hangangpaybank.domain.blockchain.service.v1;

import family.fisa.hangangpaybank.domain.blockchain.service.BlockchainTransactionKeyConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.SubmittedBlockchainTx;
import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.blockchain.repository.ContractRepository;
import family.fisa.hangangpaybank.global.crypto.WalletKeyCipher;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
class ContractCallServiceV1Test {

    private static final String PRIVATE_KEY =
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    private static final String WALLET_ADDRESS = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    private static final String RPC_ENDPOINT = "http://localhost:8545";
    private static final String LOCAL_CURRENCY_ADDRESS =
            "0x0000000000000000000000000000000000000001";
    private static final String USER_ADDRESS = "0x0000000000000000000000000000000000000002";
    private static final String MERCHANT_ADDRESS = "0x0000000000000000000000000000000000000003";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock private ContractRepository contractRepository;
    @Mock private WalletKeyCipher walletKeyCipher;

    @Spy
    private BlockchainTransactionKeyConverter keyConverter =
            new BlockchainTransactionKeyConverter();

    @Spy @InjectMocks private ContractCallServiceV1 contractCallService;

    @Test
    @DisplayName("충전 호출 시 LocalCurrency owner 기관의 credentials와 컨트랙트 주소로 트랜잭션을 보낸다")
    void chargeUsesOwnerInstitutionSigningInfo() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        TransactionReceipt receipt = new TransactionReceipt();

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY)).willReturn(credentials);
        doReturn(receipt)
                .when(contractCallService)
                .sendFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        any(Function.class));

        TransactionReceipt result =
                contractCallService.charge(3L, USER_ADDRESS, BigInteger.valueOf(10_000));

        assertThat(result).isSameAs(receipt);

        ArgumentCaptor<Function> functionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(contractCallService)
                .sendFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        functionCaptor.capture());

        assertThat(functionCaptor.getValue().getName()).isEqualTo("charge");
    }

    @Test
    @DisplayName("대상 컨트랙트가 배포되지 않았으면 예외를 던진다")
    void throwsWhenContractNotDeployed() {
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                contractCallService.pay(
                                        USER_ADDRESS,
                                        "0x0000000000000000000000000000000000000003",
                                        BigInteger.valueOf(10_000)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("잔액 조회 시 대상 컨트랙트가 배포되지 않았으면 예외를 던진다")
    void getBalanceThrowsWhenContractNotDeployed() {
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.DEPOSIT_TOKEN))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> contractCallService.getBalance(USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("balanceOf eth_call 결과를 컨트랙트 최소 단위 잔액으로 디코딩한다")
    void readsBalanceFromContract() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        BigInteger expectedBalance = new BigInteger("12345000000000000000000");
        ethCall.setResult(Numeric.toHexStringWithPrefixZeroPadded(expectedBalance, 64));

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        BigInteger result =
                contractCallService.readBalance(
                        web3j, WALLET_ADDRESS, LOCAL_CURRENCY_ADDRESS, USER_ADDRESS);

        assertThat(result).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("balanceOf eth_call이 RPC 에러를 반환하면 RPC 실패 예외로 매핑한다")
    void readBalanceMapsEthCallErrorToRpcFailed() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        Response.Error error = new Response.Error(-32000, "execution failed");
        error.setData("0xdeadbeef");
        ethCall.setError(error);

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.readBalance(
                                        web3j,
                                        WALLET_ADDRESS,
                                        LOCAL_CURRENCY_ADDRESS,
                                        USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
    }

    @Test
    @DisplayName("balanceOf eth_call 응답을 디코딩할 수 없으면 RPC 실패 예외로 매핑한다")
    void readBalanceMapsEmptyDecodeResultToRpcFailed() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.readBalance(
                                        web3j,
                                        WALLET_ADDRESS,
                                        LOCAL_CURRENCY_ADDRESS,
                                        USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Unauthorized(), BLOCKCHAIN_UNAUTHORIZED",
        "InvalidAddress(), BLOCKCHAIN_INVALID_ADDRESS",
        "InvalidAmount(), BLOCKCHAIN_INVALID_AMOUNT",
        "InvalidInstitutionId(), BLOCKCHAIN_INVALID_INSTITUTION_ID",
        "MerchantNotRegistered(), BLOCKCHAIN_MERCHANT_NOT_REGISTERED",
        "'ERC20InsufficientBalance(address,uint256,uint256)', BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE",
        "IssuanceLimitExceeded(), BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED",
        "InsufficientReserve(), BLOCKCHAIN_INSUFFICIENT_RESERVE",
        "ReserveExceedsLockedCbdc(), BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC",
        "ReserveMoveFailed(), BLOCKCHAIN_RESERVE_MOVE_FAILED",
        "DepositTokenMintFailed(), BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED",
        "DepositTokenBurnFailed(), BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED",
        "TransferFailed(), BLOCKCHAIN_TRANSFER_FAILED",
        "BankNotRegistered(), BLOCKCHAIN_BANK_NOT_REGISTERED"
    })
    @DisplayName("eth_call에서 컨트랙트 커스텀 에러 selector가 오면 도메인 에러 코드로 매핑한다")
    void mapsCustomContractErrorFromEthCall(String errorSignature, BlockchainErrorCode expectedCode)
            throws Exception {
        Web3j web3j = mock(Web3j.class);
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        Response.Error error = new Response.Error(-32000, "execution reverted");
        error.setData(selector(errorSignature));
        ethCall.setError(error);

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.sendFunctionTransaction(
                                        web3j,
                                        credentials,
                                        LOCAL_CURRENCY_ADDRESS,
                                        BigInteger.valueOf(300_000),
                                        emptyFunction()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @Test
    @DisplayName("eth_call 에러가 알 수 없는 selector면 일반 트랜잭션 실패로 매핑한다")
    void mapsUnknownEthCallErrorToTransactionReverted() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        Response.Error error = new Response.Error(-32000, "execution reverted");
        error.setData("0xdeadbeef");
        ethCall.setError(error);

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.sendFunctionTransaction(
                                        web3j,
                                        credentials,
                                        LOCAL_CURRENCY_ADDRESS,
                                        BigInteger.valueOf(300_000),
                                        emptyFunction()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_TRANSACTION_REVERTED);
    }

    @Test
    @DisplayName("isMerchant - merchants eth_call이 true를 반환하면 true를 리턴한다")
    void isMerchantReturnsTrueWhenEthCallDecodesTrue() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        ethCall.setResult(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ONE, 64));

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        boolean result =
                contractCallService.readMerchant(
                        web3j, WALLET_ADDRESS, LOCAL_CURRENCY_ADDRESS, USER_ADDRESS);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isMerchant - merchants eth_call이 false를 반환하면 false를 리턴한다")
    void isMerchantReturnsFalseWhenEthCallDecodesFalse() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        ethCall.setResult(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 64));

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        boolean result =
                contractCallService.readMerchant(
                        web3j, WALLET_ADDRESS, LOCAL_CURRENCY_ADDRESS, USER_ADDRESS);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isMerchant - merchants eth_call이 RPC 에러를 반환하면 RPC 실패 예외로 매핑한다")
    void readMerchantMapsEthCallErrorToRpcFailed() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        Response.Error error = new Response.Error(-32000, "execution failed");
        error.setData("0xdeadbeef");
        ethCall.setError(error);

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.readMerchant(
                                        web3j,
                                        WALLET_ADDRESS,
                                        LOCAL_CURRENCY_ADDRESS,
                                        USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
    }

    @Test
    @DisplayName("isMerchant - merchants eth_call 응답을 디코딩할 수 없으면 RPC 실패 예외로 매핑한다")
    void readMerchantMapsEmptyDecodeToRpcFailed() throws Exception {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> ethCallRequest = mockEthCallRequest();
        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");

        doReturn(ethCallRequest)
                .when(web3j)
                .ethCall(any(Transaction.class), eq(DefaultBlockParameterName.LATEST));
        given(ethCallRequest.send()).willReturn(ethCall);

        assertThatThrownBy(
                        () ->
                                contractCallService.readMerchant(
                                        web3j,
                                        WALLET_ADDRESS,
                                        LOCAL_CURRENCY_ADDRESS,
                                        USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
    }

    @Test
    @DisplayName("isMerchant - 컨트랙트가 배포되지 않았으면 예외를 던진다")
    void isMerchantThrowsWhenContractNotDeployed() {
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> contractCallService.isMerchant(USER_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("isMerchant - sendFunctionTransaction을 호출하지 않는다 (simulateOrThrow 경로 진입 없음)")
    void isMerchantDoesNotCallSendFunctionTransaction() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        doReturn(true)
                .when(contractCallService)
                .readMerchant(
                        any(Web3j.class), any(), eq(LOCAL_CURRENCY_ADDRESS), eq(USER_ADDRESS));

        contractCallService.isMerchant(USER_ADDRESS);

        verify(contractCallService, org.mockito.Mockito.never())
                .sendFunctionTransaction(
                        any(Web3j.class),
                        any(org.web3j.crypto.Credentials.class),
                        any(),
                        any(),
                        any(Function.class));
    }

    @Test
    @DisplayName(
            "submitPayment - pay(bytes32,address,address,uint256) Function 인코딩으로 submitFunctionTransaction을 호출한다")
    void submitPaymentEncodesPayFunctionWithBytes32Uuid() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        SubmittedBlockchainTx submittedTx = new SubmittedBlockchainTx("0xabc");

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY)).willReturn(credentials);
        doReturn(submittedTx)
                .when(contractCallService)
                .submitFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        any(Function.class));

        SubmittedBlockchainTx result =
                contractCallService.submitPayment(
                        TRANSACTION_UUID,
                        USER_ADDRESS,
                        MERCHANT_ADDRESS,
                        BigInteger.valueOf(10_000));

        assertThat(result).isSameAs(submittedTx);

        ArgumentCaptor<Function> functionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(contractCallService)
                .submitFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        functionCaptor.capture());

        Function captured = functionCaptor.getValue();
        assertThat(captured.getName()).isEqualTo("pay");
        assertThat(captured.getInputParameters().get(0)).isInstanceOf(Bytes32.class);
    }

    @Test
    @DisplayName(
            "submitCancelPayment - cancelPayment(bytes32,address,address,uint256) Function 인코딩으로 submitFunctionTransaction을 호출한다")
    void submitCancelPaymentEncodesCancelPaymentFunctionWithBytes32Uuid() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();
        Credentials credentials = Credentials.create(PRIVATE_KEY);
        SubmittedBlockchainTx submittedTx = new SubmittedBlockchainTx("0xdef");

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY)).willReturn(credentials);
        doReturn(submittedTx)
                .when(contractCallService)
                .submitFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        any(Function.class));

        SubmittedBlockchainTx result =
                contractCallService.submitCancelPayment(
                        TRANSACTION_UUID,
                        MERCHANT_ADDRESS,
                        USER_ADDRESS,
                        BigInteger.valueOf(10_000));

        assertThat(result).isSameAs(submittedTx);

        ArgumentCaptor<Function> functionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(contractCallService)
                .submitFunctionTransaction(
                        any(Web3j.class),
                        eq(credentials),
                        eq(LOCAL_CURRENCY_ADDRESS),
                        eq(BigInteger.valueOf(300_000)),
                        functionCaptor.capture());

        Function captured = functionCaptor.getValue();
        assertThat(captured.getName()).isEqualTo("cancelPayment");
        assertThat(captured.getInputParameters().get(0)).isInstanceOf(Bytes32.class);
    }

    @Test
    @DisplayName("submitPayment - waitForReceipt와 sendFunctionTransaction을 호출하지 않는다")
    void submitPaymentDoesNotWaitForReceiptOrCallSendFunctionTransaction() throws Exception {
        Institution ownerInstitution = ownerInstitution();
        Contract localCurrency =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address(LOCAL_CURRENCY_ADDRESS)
                        .institution(ownerInstitution)
                        .build();
        Credentials credentials = Credentials.create(PRIVATE_KEY);

        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(localCurrency));
        given(walletKeyCipher.decryptCredentials(PRIVATE_KEY)).willReturn(credentials);
        doReturn(new SubmittedBlockchainTx("0xabc"))
                .when(contractCallService)
                .submitFunctionTransaction(any(), any(), any(), any(), any());

        contractCallService.submitPayment(
                TRANSACTION_UUID, USER_ADDRESS, MERCHANT_ADDRESS, BigInteger.valueOf(10_000));

        verify(contractCallService, org.mockito.Mockito.never())
                .waitForReceipt(any(Web3j.class), any(String.class));
        verify(contractCallService, org.mockito.Mockito.never())
                .sendFunctionTransaction(any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private static Request<?, EthCall> mockEthCallRequest() {
        return mock(Request.class);
    }

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10);
    }

    private static Function emptyFunction() {
        return new Function("test", List.of(), List.of());
    }

    private static Institution ownerInstitution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("BoK")
                .institutionName("한국은행")
                .operatorWalletAddress(WALLET_ADDRESS)
                .operatorEncryptedPrivateKey(PRIVATE_KEY)
                .rpcEndpoint(RPC_ENDPOINT)
                .build();
    }
}
