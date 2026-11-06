package kz.kayak.bookkeeper.api.dto;

/** A sparse positive attendance mark; guest is the saved role for this session. */
public record AttendanceReportVisitView(Long sessionId, boolean guest) {}
