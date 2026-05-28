package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.wallet.WalletRepository;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.wallet.WalletTransactionRepository;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceCallbackTest {

    @Mock
    private JobRequestRepository jobRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EscrowPaymentRepository escrowPaymentRepository;

    @Mock
    private WebhookProcessedLogRepository webhookProcessedLogRepository;

    @Mock
    private PaymentAuditLogRepository paymentAuditLogRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private MpesaService mpesaService;

    private WalletService walletService;

    private PaymentService paymentService;

    private EscrowPayment payment;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, walletTransactionRepository);
        mpesaService = new MpesaService() {
            @Override
            public boolean isAcceptedCallbackSource(String remoteIp) {
                return true;
            }
        };
        paymentService = new PaymentService(
                jobRequestRepository,
                userRepository,
                escrowPaymentRepository,
                webhookProcessedLogRepository,
                paymentAuditLogRepository,
                walletService,
                mpesaService);

        JobRequest job = new JobRequest();
        job.setId(UUID.randomUUID());

        payment = EscrowPayment.builder()
                .id(UUID.randomUUID())
                .jobRequest(job)
                .status(EscrowPaymentStatus.PENDING)
                .amount(1200.0)
                .phoneNumber("254700000000")
                .checkoutRequestId("TEST-CHECKOUT-123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("null")
    @Test
    void handleMpesaCallbackShouldHoldSuccessfulPaymentInEscrow() {
        MpesaCallbackRequest.Item receipt = new MpesaCallbackRequest.Item("MpesaReceiptNumber", "ABC123");
        MpesaCallbackRequest.Item amount = new MpesaCallbackRequest.Item("Amount", 1200.0);
        MpesaCallbackRequest.Item phone = new MpesaCallbackRequest.Item("PhoneNumber", "254700000000");
        MpesaCallbackRequest.Item transactionDate = new MpesaCallbackRequest.Item("TransactionDate", "20250101120000");
        MpesaCallbackRequest.CallbackMetadata metadata = new MpesaCallbackRequest.CallbackMetadata(List.of(receipt, amount, phone, transactionDate));
        MpesaCallbackRequest.StkCallback stkCallback = new MpesaCallbackRequest.StkCallback(
                "MERCHANT_REQ_ID", java.util.Objects.requireNonNull(payment.getCheckoutRequestId()), 0,
                "The service request is processed successfully.", metadata);
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body(stkCallback);
        MpesaCallbackRequest callbackRequest = new MpesaCallbackRequest(body);

        when(webhookProcessedLogRepository.existsById(java.util.Objects.requireNonNull(payment.getCheckoutRequestId()))).thenReturn(false);
        when(escrowPaymentRepository.findByCheckoutRequestIdForUpdate(java.util.Objects.requireNonNull(payment.getCheckoutRequestId())))
                .thenReturn(Optional.of(payment));

        paymentService.handleMpesaCallback(callbackRequest, "127.0.0.1");

        assertEquals(EscrowPaymentStatus.ESCROWED, payment.getStatus());
        assertEquals("Payment captured and held in escrow.", payment.getMessage());
        assertNull(payment.getFailureReason());
        assertEquals("ABC123", payment.getMpesaReceiptNumber());
        assertNotNull(payment.getTransactionDate());
        
        verify(paymentAuditLogRepository).save(any(PaymentAuditLog.class));
        verify(webhookProcessedLogRepository).save(any(WebhookProcessedLog.class));
    }
}