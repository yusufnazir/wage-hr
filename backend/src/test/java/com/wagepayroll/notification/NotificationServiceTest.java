package com.wagepayroll.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.wagepayroll.domain.notification.NotificationRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationService notificationService;

	@Test
	void emitRejectsNullCorrelationId() {
		assertThrows(IllegalArgumentException.class,
				() -> notificationService.emit(new NotificationEmitCommand(UUID.randomUUID(), UUID.randomUUID(),
						NotificationType.TENANT_JOINED, NotificationService.TEMPLATE_VERSION_M2_1, null, false)));
	}

	@Test
	void emitSavesRowWithCorrelation() {
		UUID tenant = UUID.randomUUID();
		UUID recipient = UUID.randomUUID();
		UUID correlation = UUID.randomUUID();
		notificationService.emit(new NotificationEmitCommand(tenant, recipient, NotificationType.TENANT_JOINED,
				NotificationService.TEMPLATE_VERSION_M2_1, correlation, false));
		verify(notificationRepository).save(any());
	}
}
