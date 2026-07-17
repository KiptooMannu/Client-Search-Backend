package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.common.MessageRepository;
import com.kazikonnect.backend.features.common.NotificationRepository;
import com.kazikonnect.backend.features.dispute.DisputeRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobRequestControllerMethodTest {

    @Test
    void cancelEndpointAcceptsPostRequests() throws Exception {
        System.setProperty("net.bytebuddy.experimental", "true");

        JobRequestRepository jobRequestRepository = Mockito.mock(JobRequestRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        WorkerProfileRepository workerProfileRepository = Mockito.mock(WorkerProfileRepository.class);
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        PaymentService paymentService = Mockito.mock(PaymentService.class);
        EscrowPaymentRepository escrowPaymentRepository = Mockito.mock(EscrowPaymentRepository.class);
        DisputeRepository disputeRepository = Mockito.mock(DisputeRepository.class);

        JobRequestController controller = new JobRequestController(
                jobRequestRepository,
                userRepository,
                workerProfileRepository,
                notificationRepository,
                messageRepository,
                paymentService,
                escrowPaymentRepository,
                disputeRepository
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        UUID jobId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.CLIENT);
        actor.setEmail("client@example.com");
        actor.setUsername("client@example.com");

        JobRequest job = new JobRequest();
        job.setId(jobId);
        job.setClient(actor);
        job.setStatus(JobStatus.ACCEPTED);
        job.setEscrowFunded(false);

        when(jobRequestRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(userRepository.findByUsername("client@example.com")).thenReturn(Optional.of(actor));
        when(jobRequestRepository.save(any(JobRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/jobs/{jobId}/cancel", jobId)
                        .principal(() -> "client@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
