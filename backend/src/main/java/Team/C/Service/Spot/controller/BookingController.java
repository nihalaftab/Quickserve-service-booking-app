package Team.C.Service.Spot.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Team.C.Service.Spot.dto.BookingDTO;
import Team.C.Service.Spot.dto.BookingStatusUpdateDTO;
import Team.C.Service.Spot.dto.BookingTrackingDTO;
import Team.C.Service.Spot.model.Booking;
import Team.C.Service.Spot.model.Customer;
import Team.C.Service.Spot.model.Provider;
import Team.C.Service.Spot.model.Service;
import Team.C.Service.Spot.repositery.CustomerRepo;
import Team.C.Service.Spot.repositery.ProviderRepo;
import Team.C.Service.Spot.repositery.ServiceRepo;
import Team.C.Service.Spot.security.AESEncryptionService;
import Team.C.Service.Spot.services.BookingService;
import Team.C.Service.Spot.services.BookingTrackingService;
import Team.C.Service.Spot.services.NotificationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/booking")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class BookingController {

    private final BookingService bookingService;
    private final BookingTrackingService bookingTrackingService;
    private final NotificationService notificationService;
    private final CustomerRepo customerRepo;
    private final ProviderRepo providerRepo;
    private final ServiceRepo serviceRepo;
    private final AESEncryptionService aesEncryptionService;

    private Object convertToDTO(Booking booking) {
        if (booking == null)
            return null;

        final String customerProfileImage;
        if (booking.getCustomer() != null && booking.getCustomer().getProfileImage() != null) {
            customerProfileImage = "data:image/jpeg;base64,"
                    + java.util.Base64.getEncoder().encodeToString(booking.getCustomer().getProfileImage());
        } else {
            customerProfileImage = null;
        }

        final String providerProfileImage;
        if (booking.getProvider() != null && booking.getProvider().getProfileImage() != null) {
            providerProfileImage = "data:image/jpeg;base64,"
                    + java.util.Base64.getEncoder().encodeToString(booking.getProvider().getProfileImage());
        } else {
            providerProfileImage = null;
        }

        final String providerBookerProfileImage;
        if (booking.getProviderBooker() != null && booking.getProviderBooker().getProfileImage() != null) {
            providerBookerProfileImage = "data:image/jpeg;base64,"
                    + java.util.Base64.getEncoder().encodeToString(booking.getProviderBooker().getProfileImage());
        } else {
            providerBookerProfileImage = null;
        }

        return new HashMap<String, Object>() {
            {
                put("id", booking.getId());
                put("serviceName", booking.getServiceName());
                put("date", booking.getBookingDate());
                put("time", booking.getBookingTime());
                put("status", booking.getStatus());
                put("statusNote", booking.getStatusNote());
                put("notes", booking.getNotes());
                put("totalAmount", booking.getTotalAmount());
                put("createdAt", booking.getCreatedAt());
                put("updatedAt", booking.getUpdatedAt());
                put("acceptedAt", booking.getAcceptedAt());
                put("rejectedAt", booking.getRejectedAt());
                put("onTheWayAt", booking.getOnTheWayAt());
                put("inProgressAt", booking.getInProgressAt());
                put("completedAt", booking.getCompletedAt());
                put("cancelledAt", booking.getCancelledAt());
                put("customerId", booking.getCustomer() != null ? booking.getCustomer().getId() : null);
                put("customerName", booking.getCustomer() != null ? booking.getCustomer().getName() : null);
                put("customerPhone",
                        booking.getCustomer() != null ? aesEncryptionService.decrypt(booking.getCustomer().getPhone())
                                : null);
                put("customerEmail", booking.getCustomer() != null ? booking.getCustomer().getEmail() : null);
                put("customerProfileImage", customerProfileImage);
                put("providerBookerId",
                        booking.getProviderBooker() != null ? booking.getProviderBooker().getId() : null);
                put("providerBookerName",
                        booking.getProviderBooker() != null ? booking.getProviderBooker().getName() : null);
                put("providerBookerPhone",
                        booking.getProviderBooker() != null
                                ? aesEncryptionService.decrypt(booking.getProviderBooker().getPhone())
                                : null);
                put("providerBookerEmail",
                        booking.getProviderBooker() != null ? booking.getProviderBooker().getEmail() : null);
                put("providerBookerProfileImage", providerBookerProfileImage);
                put("providerId", booking.getProvider() != null ? booking.getProvider().getId() : null);
                put("providerName", booking.getProvider() != null ? booking.getProvider().getName() : null);
                put("providerPhone",
                        booking.getProvider() != null ? aesEncryptionService.decrypt(booking.getProvider().getPhone())
                                : null);
                put("providerEmail", booking.getProvider() != null ? booking.getProvider().getEmail() : null);
                put("providerProfileImage", providerProfileImage);
                put("serviceId", booking.getService() != null ? booking.getService().getId() : null);
            }
        };
    }

    @PostMapping("/create")
    public ResponseEntity<?> createBooking(@RequestBody BookingDTO bookingDTO) {
        try {
            if ((bookingDTO.getCustomerId() == null || bookingDTO.getCustomerId() <= 0) &&
                    (bookingDTO.getProviderBookerId() == null || bookingDTO.getProviderBookerId() <= 0)) {
                return ResponseEntity.badRequest().body("Customer ID or Provider Booker ID is required");
            }
            if (bookingDTO.getProviderId() == null || bookingDTO.getProviderId() <= 0) {
                return ResponseEntity.badRequest().body("Provider ID is required");
            }
            if (bookingDTO.getServiceId() == null || bookingDTO.getServiceId() <= 0) {
                return ResponseEntity.badRequest().body("Service ID is required");
            }
            if (bookingDTO.getBookingDate() == null || bookingDTO.getBookingDate().isEmpty()) {
                return ResponseEntity.badRequest().body("Booking date is required");
            }
            if (bookingDTO.getBookingTime() == null || bookingDTO.getBookingTime().isEmpty()) {
                return ResponseEntity.badRequest().body("Booking time is required");
            }

            Optional<Customer> customer = Optional.empty();
            if (bookingDTO.getCustomerId() != null && bookingDTO.getCustomerId() > 0) {
                customer = customerRepo.findById(bookingDTO.getCustomerId());
                if (!customer.isPresent()) {
                    return ResponseEntity.badRequest().body("Customer not found");
                }
            }

            Optional<Provider> providerBooker = Optional.empty();
            if (bookingDTO.getProviderBookerId() != null && bookingDTO.getProviderBookerId() > 0) {
                providerBooker = providerRepo.findById(bookingDTO.getProviderBookerId());
                if (!providerBooker.isPresent()) {
                    return ResponseEntity.badRequest().body("Provider Booker not found");
                }
            }

            Optional<Provider> provider = providerRepo.findById(bookingDTO.getProviderId());
            if (!provider.isPresent()) {
                return ResponseEntity.badRequest().body("Provider not found");
            }

            Optional<Service> service = serviceRepo.findById(bookingDTO.getServiceId());
            if (!service.isPresent()) {
                return ResponseEntity.badRequest().body("Service not found");
            }

            LocalDate bookingDate;
            LocalTime bookingTime;

            try {
                bookingDate = LocalDate.parse(bookingDTO.getBookingDate());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body("Invalid date format. Use YYYY-MM-DD");
            }

            try {
                bookingTime = LocalTime.parse(bookingDTO.getBookingTime());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body("Invalid time format. Use HH:MM or HH:MM:SS");
            }

            BigDecimal totalAmount = bookingDTO.getTotalAmount();
            if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                totalAmount = service.get().getPrice() != null ? BigDecimal.valueOf(service.get().getPrice())
                        : BigDecimal.ZERO;
            }

            Booking booking = Booking.builder()
                    .customer(customer.orElse(null))
                    .providerBooker(providerBooker.orElse(null))
                    .provider(provider.get())
                    .service(service.get())
                    .serviceName(
                            bookingDTO.getServiceName() != null ? bookingDTO.getServiceName() : service.get().getName())
                    .bookingDate(bookingDate)
                    .bookingTime(bookingTime)
                    .status(bookingDTO.getStatus() != null ? bookingDTO.getStatus() : "Pending")
                    .notes(bookingDTO.getNotes())
                    .totalAmount(totalAmount)
                    .build();

            Booking created = bookingService.createBooking(booking);

            // Send notification to provider about new booking
            String customerName = customer.isPresent() ? customer.get().getName()
                    : (providerBooker.isPresent() ? providerBooker.get().getName() : "Unknown");
            notificationService.notifyBookingCreated(
                    provider.get().getEmail(),
                    customerName,
                    created.getId(),
                    service.get().getName());

            // Send confirmation notification to customer/provider who made the booking
            String recipientEmail = customer.isPresent() ? customer.get().getEmail()
                    : (providerBooker.isPresent() ? providerBooker.get().getEmail() : null);
            if (recipientEmail != null) {
                notificationService.notifyCustomerBookingConfirmed(
                        recipientEmail,
                        provider.get().getName(),
                        created.getId(),
                        service.get().getName(),
                        bookingDate,
                        bookingTime);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(created));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBooking(@PathVariable Long id) {
        return bookingService.getBookingById(id)
                .map(booking -> ResponseEntity.ok(convertToDTO(booking)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<?>> getCustomerBookings(@PathVariable Long customerId) {
        List<?> bookings = bookingService.getCustomerBookings(customerId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<?>> getProviderBookings(@PathVariable Long providerId) {
        List<?> bookings = bookingService.getProviderBookings(providerId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/provider-made/{providerId}")
    public ResponseEntity<List<?>> getProviderMadeBookings(@PathVariable Long providerId) {
        List<?> bookings = bookingService.getProviderMadeBookings(providerId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<?>> getServiceBookings(@PathVariable Long serviceId) {
        List<?> bookings = bookingService.getServiceBookings(serviceId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<?>> getBookingsByStatus(@PathVariable String status) {
        List<?> bookings = bookingService.getBookingsByStatus(status)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping
    public ResponseEntity<List<?>> getAllBookings() {
        List<?> bookings = bookingService.getAllBookings()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{id}/tracking")
    public ResponseEntity<?> getBookingTracking(@PathVariable Long id) {
        return bookingTrackingService.getBookingTracking(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBookingStatus(@PathVariable Long id, @RequestBody BookingStatusUpdateDTO statusUpdate) {
        try {
            if (statusUpdate == null || statusUpdate.getStatus() == null || statusUpdate.getStatus().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Status is required");
            }
            BookingTrackingDTO updated = bookingTrackingService.updateBookingStatus(id, statusUpdate.getStatus(), statusUpdate.getNote());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to update booking status: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        try {
            Optional<Booking> existingBooking = bookingService.getBookingById(id);
            if (!existingBooking.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Booking booking = existingBooking.get();

            if (updates.containsKey("status")) {
                String newStatus = (String) updates.get("status");
                String note = updates.containsKey("statusNote") ? (String) updates.get("statusNote") : null;
                BookingTrackingDTO tracking = bookingTrackingService.updateBookingStatus(id, newStatus, note);
                
                // If only status or status with notes/dates
                if (updates.containsKey("notes")) {
                    booking.setNotes((String) updates.get("notes"));
                }
                if (updates.containsKey("bookingDate")) {
                    booking.setBookingDate(LocalDate.parse((String) updates.get("bookingDate")));
                }
                if (updates.containsKey("bookingTime")) {
                    booking.setBookingTime(LocalTime.parse((String) updates.get("bookingTime")));
                }
                if (updates.containsKey("notes") || updates.containsKey("bookingDate") || updates.containsKey("bookingTime")) {
                    bookingService.updateBookingDirect(booking);
                }
                return ResponseEntity.ok(tracking);
            }

            if (updates.containsKey("notes")) {
                booking.setNotes((String) updates.get("notes"));
            }
            if (updates.containsKey("bookingDate")) {
                booking.setBookingDate(LocalDate.parse((String) updates.get("bookingDate")));
            }
            if (updates.containsKey("bookingTime")) {
                booking.setBookingTime(LocalTime.parse((String) updates.get("bookingTime")));
            }

            Booking updated = bookingService.updateBookingDirect(booking);
            return ResponseEntity.ok(convertToDTO(updated));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to update booking: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/cancel/{id}")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        try {
            BookingTrackingDTO cancelled = bookingTrackingService.updateBookingStatus(id, "Cancelled", "Booking cancelled by customer");
            return ResponseEntity.ok(cancelled);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/complete/{id}")
    public ResponseEntity<?> completeBooking(@PathVariable Long id) {
        try {
            BookingTrackingDTO completed = bookingTrackingService.updateBookingStatus(id, "Completed", "Service completed successfully");
            return ResponseEntity.ok(completed);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable Long id) {
        if (bookingService.deleteBooking(id)) {
            return ResponseEntity.ok("Booking deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}
