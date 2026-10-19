package Team.C.Service.Spot.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Team.C.Service.Spot.dto.NotificationDTO;
import Team.C.Service.Spot.dto.NotificationRequest;
import Team.C.Service.Spot.model.Notification;
import Team.C.Service.Spot.repositery.NotificationRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepo notificationRepo;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Create and send a notification to a user
     */
    @Transactional
    public NotificationDTO createNotification(NotificationRequest request) {
        Notification notification = Notification.builder()
                .recipientEmail(request.getRecipientEmail())
                .recipientRole(request.getRecipientRole())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .relatedEntityId(request.getRelatedEntityId())
                .relatedEntityType(request.getRelatedEntityType())
                .actionUrl(request.getActionUrl())
                .senderName(request.getSenderName())
                .priority(request.getPriority() != null ? request.getPriority() : "NORMAL")
                .isRead(false)
                .build();

        Notification saved = notificationRepo.save(notification);
        NotificationDTO dto = convertToDTO(saved);

        // Send real-time notification via WebSocket
        sendRealTimeNotification(request.getRecipientEmail(), dto);

        log.info("Notification created and sent to {}: {}", request.getRecipientEmail(), request.getTitle());

        return dto;
    }

    /**
     * Send real-time notification via WebSocket
     */
    private void sendRealTimeNotification(String recipientEmail, NotificationDTO notification) {
        try {
            log.info("Attempting to send real-time notification to: {}", recipientEmail);
            log.debug("Notification content: {}", notification);

            // Send to user-specific channel
            messagingTemplate.convertAndSendToUser(
                recipientEmail,
                "/queue/notifications",
                notification
            );

            log.info("✅ Real-time notification sent successfully to user: {}", recipientEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send real-time notification to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    /**
     * Get all notifications for a user
     */
    public List<NotificationDTO> getUserNotifications(String email) {
        List<Notification> notifications = notificationRepo.findByRecipientEmailOrderByCreatedAtDesc(email);
        return notifications.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notifications for a user
     */
    public List<NotificationDTO> getUnreadNotifications(String email) {
        List<Notification> notifications = notificationRepo.findByRecipientEmailAndIsReadFalseOrderByCreatedAtDesc(email);
        return notifications.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notification count
     */
    public Long getUnreadCount(String email) {
        return notificationRepo.countByRecipientEmailAndIsReadFalse(email);
    }

    /**
     * Mark a notification as read
     */
    @Transactional
    public NotificationDTO markAsRead(Long notificationId) {
        Notification notification = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());

        Notification updated = notificationRepo.save(notification);
        return convertToDTO(updated);
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public int markAllAsRead(String email) {
        return notificationRepo.markAllAsRead(email, LocalDateTime.now());
    }

    /**
     * Delete a notification
     */
    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepo.deleteById(notificationId);
    }

    /**
     * Get recent notifications (last 7 days)
     */
    public List<NotificationDTO> getRecentNotifications(String email, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Notification> notifications = notificationRepo.findRecentNotifications(email, since);
        return notifications.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Cleanup old read notifications (older than 30 days)
     */
    @Transactional
    public int cleanupOldNotifications(int daysOld) {
        LocalDateTime before = LocalDateTime.now().minusDays(daysOld);
        return notificationRepo.deleteOldReadNotifications(before);
    }

    /**
     * Convert Notification entity to DTO
     */
    private NotificationDTO convertToDTO(Notification notification) {
        return NotificationDTO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.getIsRead())
                .relatedEntityId(notification.getRelatedEntityId())
                .relatedEntityType(notification.getRelatedEntityType())
                .actionUrl(notification.getActionUrl())
                .senderName(notification.getSenderName())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .priority(notification.getPriority())
                .build();
    }

    // ==================== Helper Methods for Common Notification Types ====================

    /**
     * Create booking notification for provider
     */
    public void notifyBookingCreated(String providerEmail, String customerName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(providerEmail)
                .recipientRole("SERVICE_PROVIDER")
                .title("New Booking Request")
                .message(customerName + " has booked your service: " + serviceName)
                .type("BOOKING_CREATED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/provider-bookings")
                .senderName(customerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create booking confirmation notification for customer
     */
    public void notifyBookingConfirmed(String customerEmail, String providerName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Booking Confirmed")
                .message("Your booking for " + serviceName + " with " + providerName + " has been confirmed")
                .type("BOOKING_CONFIRMED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/customer-bookings")
                .senderName(providerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create booking confirmation notification for customer when they make a booking
     */
    public void notifyCustomerBookingConfirmed(String customerEmail, String providerName, Long bookingId, 
                                               String serviceName, java.time.LocalDate date, java.time.LocalTime time) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Booking Request Received")
                .message("Your booking request for " + serviceName + " with " + providerName + " on " + 
                         date + " at " + time + " has been submitted successfully")
                .type("BOOKING_CREATED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/customer-bookings")
                .senderName(providerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create booking accepted notification for customer when provider accepts
     */
    public void notifyBookingAccepted(String customerEmail, String providerName, Long bookingId, 
                                      String serviceName, java.time.LocalDate date, java.time.LocalTime time) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Booking Accepted! 🎉")
                .message(providerName + " has accepted your booking for " + serviceName + " on " + 
                         date + " at " + time)
                .type("BOOKING_ACCEPTED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/track-booking/" + bookingId)
                .senderName(providerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create booking rejected notification for customer when provider rejects
     */
    public void notifyBookingRejected(String customerEmail, String providerName, Long bookingId, 
                                      String serviceName, String reason) {
        String msg = providerName + " was unable to accept your booking for " + serviceName +
                (reason != null && !reason.trim().isEmpty() ? ": " + reason : ". You can easily discover and book other available providers.");
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Booking Update: Request Rejected")
                .message(msg)
                .type("BOOKING_REJECTED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/track-booking/" + bookingId)
                .senderName(providerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create notification when provider is on the way
     */
    public void notifyProviderOnTheWay(String customerEmail, String providerName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Provider On The Way! 🚗")
                .message(providerName + " has departed and is on the way to your location for " + serviceName + ". Track live status now!")
                .type("PROVIDER_ON_THE_WAY")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/track-booking/" + bookingId)
                .senderName(providerName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create notification when service is in progress
     */
    public void notifyServiceInProgress(String customerEmail, String providerName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Service In Progress 🔧")
                .message(providerName + " has arrived and begun work on your service: " + serviceName + ".")
                .type("SERVICE_IN_PROGRESS")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/track-booking/" + bookingId)
                .senderName(providerName)
                .priority("NORMAL")
                .build();

        createNotification(request);
    }

    /**
     * Create booking cancellation notification
     */
    public void notifyBookingCancelled(String recipientEmail, String recipientRole, String senderName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(recipientEmail)
                .recipientRole(recipientRole)
                .title("Booking Cancelled")
                .message(senderName + " has cancelled the booking for " + serviceName)
                .type("BOOKING_CANCELLED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl(recipientRole.equals("CUSTOMER") ? "/track-booking/" + bookingId : "/provider-bookings")
                .senderName(senderName)
                .priority("HIGH")
                .build();

        createNotification(request);
    }

    /**
     * Create booking completion notification
     */
    public void notifyBookingCompleted(String customerEmail, String providerName, Long bookingId, String serviceName) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(customerEmail)
                .recipientRole("CUSTOMER")
                .title("Service Completed 🎉")
                .message("Your service " + serviceName + " with " + providerName + " has been completed. Please rate your experience!")
                .type("BOOKING_COMPLETED")
                .relatedEntityId(bookingId)
                .relatedEntityType("BOOKING")
                .actionUrl("/track-booking/" + bookingId)
                .senderName(providerName)
                .priority("NORMAL")
                .build();

        createNotification(request);
    }

    /**
     * Broadcast real-time live booking tracking payload via WebSockets
     */
    public void broadcastBookingTracking(Long bookingId, Object trackingPayload, String customerEmail, String providerEmail) {
        try {
            // Broadcast to the booking topic (for any client currently viewing the live tracker page)
            messagingTemplate.convertAndSend("/topic/booking/" + bookingId, trackingPayload);
            log.info("📡 Real-time tracking broadcast sent to /topic/booking/{}", bookingId);

            // Also broadcast directly to user queues
            if (customerEmail != null && !customerEmail.trim().isEmpty()) {
                messagingTemplate.convertAndSendToUser(customerEmail, "/queue/booking-tracking", trackingPayload);
            }
            if (providerEmail != null && !providerEmail.trim().isEmpty()) {
                messagingTemplate.convertAndSendToUser(providerEmail, "/queue/booking-tracking", trackingPayload);
            }
        } catch (Exception e) {
            log.error("❌ Failed to broadcast live tracking update for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }

    /**
     * Create review received notification for provider
     */
    public void notifyReviewReceived(String providerEmail, String customerName, Long reviewId, int rating, String serviceName) {
        String stars = "⭐".repeat(Math.min(rating, 5)); // Generate star emoji based on rating
        NotificationRequest request = NotificationRequest.builder()
                .recipientEmail(providerEmail)
                .recipientRole("SERVICE_PROVIDER")
                .title("New Review Received")
                .message(customerName + " rated your service '" + serviceName + "' " + rating + " stars " + stars)
                .type("REVIEW_RECEIVED")
                .relatedEntityId(reviewId)
                .relatedEntityType("REVIEW")
                .actionUrl("/provider-profile")
                .senderName(customerName)
                .priority("NORMAL")
                .build();

        createNotification(request);
    }
}

