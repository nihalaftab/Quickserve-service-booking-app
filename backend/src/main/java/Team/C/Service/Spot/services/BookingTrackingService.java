package Team.C.Service.Spot.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Team.C.Service.Spot.dto.BookingTrackingDTO;
import Team.C.Service.Spot.dto.TrackingStepDTO;
import Team.C.Service.Spot.model.Booking;
import Team.C.Service.Spot.model.Customer;
import Team.C.Service.Spot.model.Provider;
import Team.C.Service.Spot.repositery.BookingRepo;
import Team.C.Service.Spot.security.AESEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTrackingService {

    private final BookingRepo bookingRepo;
    private final NotificationService notificationService;
    private final AESEncryptionService aesEncryptionService;

    /**
     * Normalize status string to standard format
     */
    public String normalizeStatus(String status) {
        if (status == null) return "Pending";
        String s = status.trim().toLowerCase().replace("-", " ").replace("_", " ");
        if (s.contains("accept")) return "Accepted";
        if (s.contains("reject")) return "Rejected";
        if (s.contains("way") || s.contains("travel") || s.contains("depart")) return "On The Way";
        if (s.contains("progress") || s.contains("started") || s.contains("working")) return "In Progress";
        if (s.contains("complete")) return "Completed";
        if (s.contains("cancel")) return "Cancelled";
        if (s.contains("pend") || s.contains("request")) return "Pending";
        return status;
    }

    /**
     * Get tracking details for a booking
     */
    @Transactional(readOnly = true)
    public Optional<BookingTrackingDTO> getBookingTracking(Long bookingId) {
        return bookingRepo.findById(bookingId).map(this::buildTrackingDTO);
    }

    /**
     * Update booking status with live tracking broadcast and notifications
     */
    @Transactional
    public BookingTrackingDTO updateBookingStatus(Long bookingId, String rawStatus, String note) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with ID: " + bookingId));

        String oldStatus = booking.getStatus();
        String normalizedStatus = normalizeStatus(rawStatus);
        LocalDateTime now = LocalDateTime.now();

        booking.setStatus(normalizedStatus);
        if (note != null && !note.trim().isEmpty()) {
            booking.setStatusNote(note.trim());
        }

        // Set timestamps based on state transitions
        switch (normalizedStatus) {
            case "Accepted":
                if (booking.getAcceptedAt() == null) {
                    booking.setAcceptedAt(now);
                }
                break;
            case "Rejected":
                if (booking.getRejectedAt() == null) {
                    booking.setRejectedAt(now);
                }
                if (booking.getCancelledAt() == null) {
                    booking.setCancelledAt(now);
                }
                break;
            case "On The Way":
                if (booking.getAcceptedAt() == null) {
                    booking.setAcceptedAt(now);
                }
                if (booking.getOnTheWayAt() == null) {
                    booking.setOnTheWayAt(now);
                }
                break;
            case "In Progress":
                if (booking.getAcceptedAt() == null) {
                    booking.setAcceptedAt(now);
                }
                if (booking.getOnTheWayAt() == null) {
                    booking.setOnTheWayAt(now);
                }
                if (booking.getInProgressAt() == null) {
                    booking.setInProgressAt(now);
                }
                break;
            case "Completed":
                if (booking.getCompletedAt() == null) {
                    booking.setCompletedAt(now);
                }
                break;
            case "Cancelled":
                if (booking.getCancelledAt() == null) {
                    booking.setCancelledAt(now);
                }
                break;
            default:
                break;
        }

        Booking saved = bookingRepo.save(booking);
        BookingTrackingDTO trackingDTO = buildTrackingDTO(saved);

        // Send notifications if status changed
        if (!normalizedStatus.equalsIgnoreCase(oldStatus)) {
            sendNotificationsForStatus(saved, oldStatus, normalizedStatus, note);
        }

        // Broadcast WebSocket tracking update to topic and user queues
        String customerEmail = getCustomerEmail(saved);
        String providerEmail = saved.getProvider() != null ? saved.getProvider().getEmail() : null;
        notificationService.broadcastBookingTracking(saved.getId(), trackingDTO, customerEmail, providerEmail);

        return trackingDTO;
    }

    private String getCustomerEmail(Booking booking) {
        if (booking.getCustomer() != null) {
            return booking.getCustomer().getEmail();
        } else if (booking.getProviderBooker() != null) {
            return booking.getProviderBooker().getEmail();
        }
        return null;
    }

    private String getCustomerName(Booking booking) {
        if (booking.getCustomer() != null) {
            return booking.getCustomer().getName();
        } else if (booking.getProviderBooker() != null) {
            return booking.getProviderBooker().getName();
        }
        return "Customer";
    }

    private void sendNotificationsForStatus(Booking booking, String oldStatus, String newStatus, String note) {
        String customerEmail = getCustomerEmail(booking);
        String customerName = getCustomerName(booking);
        String providerName = booking.getProvider() != null ? booking.getProvider().getName() : "Provider";
        String providerEmail = booking.getProvider() != null ? booking.getProvider().getEmail() : null;
        String serviceName = booking.getServiceName();

        switch (newStatus) {
            case "Accepted":
                if (customerEmail != null) {
                    notificationService.notifyBookingAccepted(
                            customerEmail,
                            providerName,
                            booking.getId(),
                            serviceName,
                            booking.getBookingDate(),
                            booking.getBookingTime()
                    );
                }
                break;

            case "Rejected":
                if (customerEmail != null) {
                    notificationService.notifyBookingRejected(
                            customerEmail,
                            providerName,
                            booking.getId(),
                            serviceName,
                            note
                    );
                }
                break;

            case "On The Way":
                if (customerEmail != null) {
                    notificationService.notifyProviderOnTheWay(
                            customerEmail,
                            providerName,
                            booking.getId(),
                            serviceName
                    );
                }
                break;

            case "In Progress":
                if (customerEmail != null) {
                    notificationService.notifyServiceInProgress(
                            customerEmail,
                            providerName,
                            booking.getId(),
                            serviceName
                    );
                }
                break;

            case "Completed":
                if (customerEmail != null) {
                    notificationService.notifyBookingCompleted(
                            customerEmail,
                            providerName,
                            booking.getId(),
                            serviceName
                    );
                }
                break;

            case "Cancelled":
                if (customerEmail != null) {
                    notificationService.notifyBookingCancelled(
                            customerEmail,
                            "CUSTOMER",
                            providerName,
                            booking.getId(),
                            serviceName
                    );
                }
                if (providerEmail != null) {
                    notificationService.notifyBookingCancelled(
                            providerEmail,
                            "SERVICE_PROVIDER",
                            customerName,
                            booking.getId(),
                            serviceName
                    );
                }
                break;

            default:
                break;
        }
    }

    /**
     * Build rich BookingTrackingDTO with timeline steps
     */
    public BookingTrackingDTO buildTrackingDTO(Booking booking) {
        if (booking == null) return null;

        String status = booking.getStatus() != null ? booking.getStatus() : "Pending";
        String normStatus = normalizeStatus(status);

        // Step index calculation
        // 0: Booking requested (Pending)
        // 1: Provider accepted
        // 2: Provider on the way
        // 3: Service in progress
        // 4: Service completed
        // -1: Rejected or Cancelled
        int currentStepIndex;
        switch (normStatus) {
            case "Pending":
                currentStepIndex = 0;
                break;
            case "Accepted":
                currentStepIndex = 1;
                break;
            case "On The Way":
                currentStepIndex = 2;
                break;
            case "In Progress":
                currentStepIndex = 3;
                break;
            case "Completed":
                currentStepIndex = 4;
                break;
            case "Rejected":
            case "Cancelled":
            default:
                currentStepIndex = -1;
                break;
        }

        Boolean isLive = "Accepted".equals(normStatus) || "On The Way".equals(normStatus) || "In Progress".equals(normStatus);

        // Build Steps Timeline
        List<TrackingStepDTO> steps = new ArrayList<>();

        // Step 1: Booking requested
        steps.add(TrackingStepDTO.builder()
                .stepNumber(1)
                .key("REQUESTED")
                .title("Booking Requested")
                .description("Service appointment submitted and waiting for provider confirmation")
                .timestamp(booking.getCreatedAt())
                .state("completed")
                .build());

        // Step 2: Provider accepted or rejected
        if ("Rejected".equals(normStatus)) {
            steps.add(TrackingStepDTO.builder()
                    .stepNumber(2)
                    .key("REJECTED")
                    .title("Provider Rejected")
                    .description(booking.getStatusNote() != null && !booking.getStatusNote().isEmpty()
                            ? booking.getStatusNote()
                            : "Provider is unable to fulfill this request")
                    .timestamp(booking.getRejectedAt() != null ? booking.getRejectedAt() : booking.getUpdatedAt())
                    .state("rejected")
                    .build());
        } else {
            String step2State;
            if ("Pending".equals(normStatus)) {
                step2State = "current";
            } else if ("Cancelled".equals(normStatus) && booking.getAcceptedAt() == null) {
                step2State = "cancelled";
            } else {
                step2State = "completed";
            }

            steps.add(TrackingStepDTO.builder()
                    .stepNumber(2)
                    .key("ACCEPTED")
                    .title("Provider Accepted")
                    .description("Provider accepted the request and confirmed the schedule")
                    .timestamp(booking.getAcceptedAt())
                    .state(step2State)
                    .build());
        }

        // Step 3: Provider on the way
        String step3State;
        if ("Rejected".equals(normStatus) || "Cancelled".equals(normStatus)) {
            step3State = "cancelled";
        } else if ("Pending".equals(normStatus) || "Accepted".equals(normStatus)) {
            step3State = "upcoming";
        } else if ("On The Way".equals(normStatus)) {
            step3State = "current";
        } else {
            step3State = "completed";
        }

        steps.add(TrackingStepDTO.builder()
                .stepNumber(3)
                .key("ON_THE_WAY")
                .title("Provider On The Way")
                .description("Provider is en route to the service location")
                .timestamp(booking.getOnTheWayAt())
                .state(step3State)
                .build());

        // Step 4: Service in progress
        String step4State;
        if ("Rejected".equals(normStatus) || "Cancelled".equals(normStatus)) {
            step4State = "cancelled";
        } else if ("In Progress".equals(normStatus)) {
            step4State = "current";
        } else if ("Completed".equals(normStatus)) {
            step4State = "completed";
        } else {
            step4State = "upcoming";
        }

        steps.add(TrackingStepDTO.builder()
                .stepNumber(4)
                .key("IN_PROGRESS")
                .title("Service In Progress")
                .description("Provider has arrived and is actively performing the service")
                .timestamp(booking.getInProgressAt())
                .state(step4State)
                .build());

        // Step 5: Service completed
        String step5State;
        if ("Rejected".equals(normStatus) || "Cancelled".equals(normStatus)) {
            step5State = "cancelled";
        } else if ("Completed".equals(normStatus)) {
            step5State = "completed";
        } else {
            step5State = "upcoming";
        }

        steps.add(TrackingStepDTO.builder()
                .stepNumber(5)
                .key("COMPLETED")
                .title("Service Completed")
                .description("Service has been completed successfully")
                .timestamp(booking.getCompletedAt())
                .state(step5State)
                .build());

        // Provider Map
        Map<String, Object> providerMap = new HashMap<>();
        Provider provider = booking.getProvider();
        if (provider != null) {
            String pImage = null;
            if (provider.getProfileImage() != null) {
                pImage = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(provider.getProfileImage());
            }
            providerMap.put("id", provider.getId());
            providerMap.put("name", provider.getName());
            providerMap.put("email", provider.getEmail());
            providerMap.put("phone", provider.getPhone() != null ? aesEncryptionService.decrypt(provider.getPhone()) : null);
            providerMap.put("profileImage", pImage);
            providerMap.put("serviceType", provider.getServiceType());
            providerMap.put("doorNo", provider.getDoorNo());
            providerMap.put("addressLine", provider.getAddressLine());
            providerMap.put("city", provider.getCity());
            providerMap.put("state", provider.getState());
            providerMap.put("pincode", provider.getPincode());
            providerMap.put("latitude", provider.getLatitude());
            providerMap.put("longitude", provider.getLongitude());
        }

        // Customer Map
        Map<String, Object> customerMap = new HashMap<>();
        Customer customer = booking.getCustomer();
        Provider providerBooker = booking.getProviderBooker();

        if (customer != null) {
            String cImage = null;
            if (customer.getProfileImage() != null) {
                cImage = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(customer.getProfileImage());
            }
            customerMap.put("id", customer.getId());
            customerMap.put("name", customer.getName());
            customerMap.put("email", customer.getEmail());
            customerMap.put("phone", customer.getPhone() != null ? aesEncryptionService.decrypt(customer.getPhone()) : null);
            customerMap.put("profileImage", cImage);
            customerMap.put("doorNo", customer.getDoorNo());
            customerMap.put("addressLine", customer.getAddressLine());
            customerMap.put("city", customer.getCity());
            customerMap.put("state", customer.getState());
            customerMap.put("pincode", customer.getPincode());
            customerMap.put("latitude", customer.getLatitude());
            customerMap.put("longitude", customer.getLongitude());
        } else if (providerBooker != null) {
            String pbImage = null;
            if (providerBooker.getProfileImage() != null) {
                pbImage = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(providerBooker.getProfileImage());
            }
            customerMap.put("id", providerBooker.getId());
            customerMap.put("name", providerBooker.getName());
            customerMap.put("email", providerBooker.getEmail());
            customerMap.put("phone", providerBooker.getPhone() != null ? aesEncryptionService.decrypt(providerBooker.getPhone()) : null);
            customerMap.put("profileImage", pbImage);
            customerMap.put("doorNo", providerBooker.getDoorNo());
            customerMap.put("addressLine", providerBooker.getAddressLine());
            customerMap.put("city", providerBooker.getCity());
            customerMap.put("state", providerBooker.getState());
            customerMap.put("pincode", providerBooker.getPincode());
            customerMap.put("latitude", providerBooker.getLatitude());
            customerMap.put("longitude", providerBooker.getLongitude());
        }

        return BookingTrackingDTO.builder()
                .bookingId(booking.getId())
                .serviceId(booking.getService() != null ? booking.getService().getId() : null)
                .serviceName(booking.getServiceName())
                .totalAmount(booking.getTotalAmount())
                .bookingDate(booking.getBookingDate())
                .bookingTime(booking.getBookingTime())
                .status(normStatus)
                .statusNote(booking.getStatusNote())
                .notes(booking.getNotes())
                .currentStepIndex(currentStepIndex)
                .isLive(isLive)
                .createdAt(booking.getCreatedAt())
                .acceptedAt(booking.getAcceptedAt())
                .rejectedAt(booking.getRejectedAt())
                .onTheWayAt(booking.getOnTheWayAt())
                .inProgressAt(booking.getInProgressAt())
                .completedAt(booking.getCompletedAt())
                .cancelledAt(booking.getCancelledAt())
                .updatedAt(booking.getUpdatedAt())
                .provider(providerMap)
                .customer(customerMap)
                .steps(steps)
                .build();
    }
}
