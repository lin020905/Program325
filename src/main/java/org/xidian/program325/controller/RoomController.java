package org.xidian.program325.controller;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.xidian.program325.common.ApiResponse;
import org.xidian.program325.common.Inputs;
import org.xidian.program325.common.Sessions;
import org.xidian.program325.pojo.Rooms.Room;
import org.xidian.program325.pojo.Rooms.RoomRequest;
import org.xidian.program325.service.RoomService;

@RestController
@RequestMapping("/api")
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping("/rooms")
    public ApiResponse<List<Room>> list(
            HttpServletRequest request,
            @RequestParam(name = "attendees", required = false) Integer attendees,
            @RequestParam(name = "startTime", required = false)
            @DateTimeFormat(pattern = Inputs.LOCAL_DATE_TIME) LocalDateTime start,
            @RequestParam(name = "endTime", required = false)
            @DateTimeFormat(pattern = Inputs.LOCAL_DATE_TIME) LocalDateTime end) {
        Sessions.requireUser(request);
        return ApiResponse.ok(rooms.list(attendees, start, end));
    }

    @GetMapping("/rooms/{id}")
    public ApiResponse<Room> detail(HttpServletRequest request, @PathVariable("id") long id) {
        Sessions.requireUser(request);
        return ApiResponse.ok(rooms.detail(id));
    }

    @PostMapping("/admin/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Room> create(HttpServletRequest request, @RequestBody RoomRequest room) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(rooms.create(room));
    }

    @PutMapping("/admin/rooms/{id}")
    public ApiResponse<Room> update(HttpServletRequest request, @PathVariable("id") long id,
                                    @RequestBody RoomRequest room) {
        Sessions.requireAdmin(request);
        return ApiResponse.ok(rooms.update(id, room));
    }

    @DeleteMapping("/admin/rooms/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable("id") long id) {
        Sessions.requireAdmin(request);
        rooms.delete(id);
        return ApiResponse.ok(null);
    }
}
