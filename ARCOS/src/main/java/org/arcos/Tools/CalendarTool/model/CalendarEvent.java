package org.arcos.Tools.CalendarTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {
    private String id;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private boolean allDay;
}
