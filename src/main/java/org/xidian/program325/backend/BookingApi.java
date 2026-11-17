package org.xidian.program325.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;
import static org.springframework.http.HttpStatus.*;
import static org.xidian.program325.backend.ApiError.require;
import static org.xidian.program325.backend.Models.*;

@RestController
@RequestMapping("/api")
public class BookingApi {
    private static final String SESSION_USER = "identity";
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String dummyHash = passwords.encode("unused-login-placeholder");
    private final BookingMapper mapper;
    private final BookingService service;

    public BookingApi(BookingMapper mapper, BookingService service) {
        this.mapper = mapper;
        this.service = service;
    }

    public record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) {
            return new Result<>(0, "成功", data);
        }
    }

    @PostMapping("/login")
    public Result<Identity> login(@RequestBody Login input, HttpServletRequest request) {
        String username = BookingService.requiredText(input.username(), 50, "账号");
        String password = input.password();
        require(password != null && !password.isEmpty()
                && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
                BAD_REQUEST, "密码不能为空且最多 72 字节");
        Account account = mapper.account(username);
        String hash = account == null ? dummyHash : account.passwordHash();
        if (hash.startsWith("{bcrypt}")) {
            hash = hash.substring("{bcrypt}".length());
        }
        boolean matches = passwords.matches(password, hash);
        require(matches && account != null, UNAUTHORIZED, "账号或密码错误");
        Identity identity = new Identity(account.userId(), account.username(), account.role());
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession().setAttribute(SESSION_USER, identity);
        return Result.ok(identity);
    }

    @GetMapping("/me")
    public Result<Identity> me(HttpServletRequest request) {
        return Result.ok(user(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        return Result.ok(null);
    }

    @GetMapping("/rooms")
    public Result<List<Room>> rooms(HttpServletRequest request,
            @RequestParam(name = "start_time", required = false) @DateTimeFormat(iso = DATE_TIME) LocalDateTime start,
            @RequestParam(name = "end_time", required = false) @DateTimeFormat(iso = DATE_TIME) LocalDateTime end,
            @RequestParam(name = "attendees", required = false) Integer attendees) {
        user(request);
        return Result.ok(service.rooms(start, end, attendees));
    }

    @GetMapping("/rooms/{id}")
    public Result<Room> room(HttpServletRequest request, @PathVariable("id") long id) {
        user(request);
        return Result.ok(service.room(id));
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Void> createRoom(HttpServletRequest request, @RequestBody RoomInput input) {
        service.createRoom(user(request), input);
        return Result.ok(null);
    }

    @PutMapping("/rooms/{id}")
    public Result<Room> updateRoom(HttpServletRequest request, @PathVariable("id") long id,
                                   @RequestBody RoomInput input) {
        return Result.ok(service.updateRoom(user(request), id, input));
    }

    @DeleteMapping("/rooms/{id}")
    public Result<Void> deleteRoom(HttpServletRequest request, @PathVariable("id") long id) {
        service.deleteRoom(user(request), id);
        return Result.ok(null);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Reservation> create(HttpServletRequest request, @RequestBody Request input) {
        return new Result<>(0, "预约已提交，等待审批", service.create(user(request), input));
    }

    @GetMapping("/reservations/me")
    public Result<List<Reservation>> mine(HttpServletRequest request) {
        return Result.ok(service.mine(user(request)));
    }

    @GetMapping("/reservations")
    public Result<List<Reservation>> all(HttpServletRequest request,
            @RequestParam(name = "room_id", required = false) Long roomId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DATE_TIME) LocalDateTime from,
            @RequestParam(name = "until", required = false) @DateTimeFormat(iso = DATE_TIME) LocalDateTime until) {
        return Result.ok(service.all(user(request), roomId, status, from, until));
    }

    @GetMapping("/reservations/{id}")
    public Result<Reservation> detail(HttpServletRequest request, @PathVariable("id") long id) {
        return Result.ok(service.detail(user(request), id));
    }

    @PostMapping("/reservations/{id}/cancel")
    public Result<Reservation> cancel(HttpServletRequest request, @PathVariable("id") long id) {
        return Result.ok(service.cancel(user(request), id));
    }

    @PostMapping("/reservations/{id}/approval")
    public Result<Reservation> review(HttpServletRequest request, @PathVariable("id") long id,
                                     @RequestBody Review input) {
        return Result.ok(service.review(user(request), id, input));
    }

    private static Identity user(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Identity identity = session == null ? null : (Identity) session.getAttribute(SESSION_USER);
        require(identity != null, UNAUTHORIZED, "请先登录");
        return identity;
    }
}
