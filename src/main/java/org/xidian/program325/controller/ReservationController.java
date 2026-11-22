package org.xidian.program325.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.xidian.program325.common.ApiResponse;
import org.xidian.program325.common.Sessions;
import org.xidian.program325.pojo.Reservations.CreateRequest;
import org.xidian.program325.pojo.Reservations.Page;
import org.xidian.program325.pojo.Reservations.ReservationView;
import org.xidian.program325.pojo.Reservations.Status;
import org.xidian.program325.service.ReservationService;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping
    public ApiResponse<Page<ReservationView>> list(HttpServletRequest request,
                                                  @RequestParam(name = "status", required = false) Status status,
                                                  @RequestParam(name = "page", defaultValue = "1") int page,
                                                  @RequestParam(name = "size", defaultValue = "20") int size) {
        long userId = Sessions.requireUser(request).userId();
        return ApiResponse.ok(reservations.list(userId, null, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationView> detail(HttpServletRequest request, @PathVariable("id") long id) {
        return ApiResponse.ok(reservations.detail(Sessions.requireUser(request).userId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationView> create(HttpServletRequest request, @RequestBody CreateRequest reservation) {
        return ApiResponse.ok(reservations.create(Sessions.requireUser(request).userId(), reservation));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ReservationView> cancel(HttpServletRequest request, @PathVariable("id") long id) {
        return ApiResponse.ok(reservations.cancel(Sessions.requireUser(request).userId(), id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ReservationView> confirm(HttpServletRequest request, @PathVariable("id") long id) {
        return ApiResponse.ok(reservations.confirm(Sessions.requireUser(request).userId(), id));
    }
}
