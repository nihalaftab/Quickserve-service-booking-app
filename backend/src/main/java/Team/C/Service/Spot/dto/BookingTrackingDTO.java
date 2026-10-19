package Team.C.Service.Spot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTrackingDTO {
    private Long bookingId;
    private Long serviceId;
    private String serviceName;
    private BigDecimal totalAmount;
    private LocalDate bookingDate;
    private LocalTime bookingTime;
    private String status;
    private String statusNote;
    private String notes;
    private int currentStepIndex;
    private Boolean isLive;

    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime onTheWayAt;
    private LocalDateTime inProgressAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime updatedAt;

    private Map<String, Object> provider;
    private Map<String, Object> customer;
    private List<TrackingStepDTO> steps;
}
