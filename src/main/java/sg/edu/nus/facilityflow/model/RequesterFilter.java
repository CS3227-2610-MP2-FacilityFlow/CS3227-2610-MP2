package sg.edu.nus.facilityflow.model;

import java.time.LocalDate;

public record RequesterFilter(String query, RequestStatus status, String category,
                              LocalDate from, LocalDate through) { }
