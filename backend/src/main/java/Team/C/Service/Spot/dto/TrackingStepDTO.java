package Team.C.Service.Spot.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingStepDTO {
    private int stepNumber;
    private String key;
    private String title;
    private String description;
    private LocalDateTime timestamp;
    private String state; // "completed", "current", "upcoming", "rejected", "cancelled"
}
