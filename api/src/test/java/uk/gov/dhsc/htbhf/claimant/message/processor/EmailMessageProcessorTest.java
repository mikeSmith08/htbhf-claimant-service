package uk.gov.dhsc.htbhf.claimant.message.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dhsc.htbhf.claimant.entity.Claim;
import uk.gov.dhsc.htbhf.claimant.entity.Message;
import uk.gov.dhsc.htbhf.claimant.exception.EventFailedException;
import uk.gov.dhsc.htbhf.claimant.message.MessageStatus;
import uk.gov.dhsc.htbhf.claimant.message.context.EmailMessageContext;
import uk.gov.dhsc.htbhf.claimant.message.context.MessageContextLoader;
import uk.gov.dhsc.htbhf.claimant.message.payload.EmailType;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.dhsc.htbhf.claimant.message.MessageStatus.COMPLETED;
import static uk.gov.dhsc.htbhf.claimant.message.MessageType.SEND_EMAIL;
import static uk.gov.dhsc.htbhf.claimant.service.audit.FailedEventTestUtils.verifySendEmailEventFailExceptionAndEventAreCorrect;
import static uk.gov.dhsc.htbhf.claimant.testsupport.ClaimTestDataFactory.aValidClaim;
import static uk.gov.dhsc.htbhf.claimant.testsupport.EmailPersonalisationMapTestDataFactory.buildEmailPersonalisation;
import static uk.gov.dhsc.htbhf.claimant.testsupport.MessageTestDataFactory.aValidMessageWithType;
import static uk.gov.dhsc.htbhf.claimant.testsupport.TestConstants.VALID_EMAIL_ADDRESS;

@ExtendWith(MockitoExtension.class)
class EmailMessageProcessorTest {

    private static final String REPLY_TO_ADDRESS_ID = "skjfbnsdkjfbsjhk";

    @Mock
    private NotificationClient client;
    @Mock
    private MessageContextLoader messageContextLoader;

    private EmailMessageProcessor emailMessageProcessor;


    @BeforeEach
    void init() {
        emailMessageProcessor = new EmailMessageProcessor(client, messageContextLoader, REPLY_TO_ADDRESS_ID);
    }

    @Test
    void shouldSendMessage() throws NotificationClientException {
        //Given
        Claim claim = aValidClaim();
        String templateId = "12334546";
        Map<String, Object> emailPersonalisation = buildEmailPersonalisation();
        EmailMessageContext context = EmailMessageContext.builder()
                .claim(claim)
                .templateId(templateId)
                .emailPersonalisation(emailPersonalisation)
                .emailType(EmailType.NEW_CARD)
                .build();
        given(messageContextLoader.loadEmailMessageContext(any())).willReturn(context);
        Message message = aValidMessageWithType(SEND_EMAIL);

        //When
        MessageStatus status = emailMessageProcessor.processMessage(message);

        //Then
        assertThat(status).isEqualTo(COMPLETED);
        verify(messageContextLoader).loadEmailMessageContext(message);
        verify(client).sendEmail(eq(templateId), eq(VALID_EMAIL_ADDRESS), eq(emailPersonalisation), any(String.class), eq(REPLY_TO_ADDRESS_ID));
    }

    @Test
    void shouldThrowFailedEventExceptionnWhenSendMessageFails() throws NotificationClientException {
        //Given
        Claim claim = aValidClaim();
        String templateId = "12334546";
        Map<String, Object> emailPersonalisation = buildEmailPersonalisation();
        EmailMessageContext context = EmailMessageContext.builder()
                .claim(claim)
                .templateId(templateId)
                .emailPersonalisation(emailPersonalisation)
                .emailType(EmailType.NEW_CARD)
                .build();
        given(messageContextLoader.loadEmailMessageContext(any())).willReturn(context);
        Message message = aValidMessageWithType(SEND_EMAIL);
        NotificationClientException testException = new NotificationClientException("Test exception from message send");
        given(client.sendEmail(anyString(), anyString(), any(), anyString(), anyString()))
                .willThrow(testException);

        //When
        EventFailedException thrown = catchThrowableOfType(() -> emailMessageProcessor.processMessage(message), EventFailedException.class);

        //Then
        verifySendEmailEventFailExceptionAndEventAreCorrect(claim, testException, thrown, templateId);
        verify(messageContextLoader).loadEmailMessageContext(message);
        verify(client).sendEmail(eq(templateId), eq(VALID_EMAIL_ADDRESS), eq(emailPersonalisation), any(String.class), eq(REPLY_TO_ADDRESS_ID));
    }

}