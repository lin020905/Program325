package org.xidian.program325.controller;

import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xidian.program325.common.ApiResponse;
import org.xidian.program325.common.Sessions;
import org.xidian.program325.pojo.Reservations.ApprovalRequest;
import org.xidian.program325.pojo.Reservations.Page;
import org.xidian.program325.pojo.Reservations.ReservationView;
import org.xidian.program325.pojo.Reservations.Schedule;
import org.xidian.program325.pojo.Reservations.Status;
import org.xidian.program325.service.ReservationService;

@RestController
@RequestMapping("/api/admin")
public class AdminReservationController {
    private final ReservationService reservations;

    public AdminReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/reservations")
    public ApiResponse<Page<ReservationView>> list(HttpServletRequest request,
                                                  @RequestParam(name = "roomId", required = false) Long roomId,
                                                  @RequestParam(name = "status", required = false) Status status,
                                                  @RequestParam(name = "page", defaultValue = "1") int page,
                                                  @RequestParam(name = "size", defaultValue = "20") int size) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(reservations.list(null, roomId, status, page, size));
    }

    @GetMapping("/reservations/pending")
    public ApiResponse<Page<ReservationView>> pending(HttpServletRequest request,
                                                     @RequestParam(name = "page", defaultValue = "1") int page,
                                                     @RequestParam(name = "size", defaultValue = "20") int size) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(reservations.list(null, null, Status.PENDING, page, size));
    }

    @GetMapping("/reservations/{id}")
    public ApiResponse<ReservationView> detail(HttpServletRequest request, @PathVariable("id") long id) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(reservations.detail(null, id));
    }

    @PostMapping("/reservations/{id}/approval")
    public ApiResponse<ReservationView> approve(HttpServletRequest request, @PathVariable("id") long id,
                                               @RequestBody ApprovalRequest approval) {
        long adminId = Sessions.requireAdmin(request).userId();
        return ApiResponse.ok(reservations.approve(adminId, id, approval));
    }

    @GetMapping("/schedule")
    public ApiResponse<Schedule> schedule(HttpServletRequest request,
                                          @RequestParam("from") @DateTimeFormat(pattern = "uuuu-MM-dd") LocalDate from,
                                          @RequestParam(name = "days", defaultValue = "7") int days,
                                          @RequestParam(name = "roomId", required = false) Long roomId) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(reservations.schedule(from, days, roomId));
    }
}
