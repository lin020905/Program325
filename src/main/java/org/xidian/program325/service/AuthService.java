package org.xidian.program325.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.xidian.program325.common.ApiException;
import org.xidian.program325.common.Inputs;
import org.xidian.program325.common.PasswordHasher;
import org.xidian.program325.mapper.UserMapper;
import org.xidian.program325.pojo.Account.LoginRequest;
import org.xidian.program325.pojo.Account.Role;
import org.xidian.program325.pojo.Account.SessionUser;
import org.xidian.program325.pojo.Account.User;

@Service
public class AuthService implements ApplicationRunner {
    private static final String DUMMY_HASH = PasswordHasher.hash("unused-dummy-password");

    private final UserMapper users;
    private final Environment environment;

    public AuthService(UserMapper users, Environment environment) {
        this.users = users;
        this.environment = environment;
    }

    public SessionUser login(LoginRequest request) {
        String username = Inputs.text(request.username(), 50, "账号");
        String password = request.password();
        if (password == null || password.isEmpty() || password.length() > 256 || request.role() == null) {
            throw ApiException.invalid("请填写有效密码并选择登录角色");
        }
        User user = users.findByUsername(username);
        boolean valid = PasswordHasher.matches(password, user == null ? DUMMY_HASH : user.passwordHash());
        if (!valid || user == null || user.role() != request.role()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号、密码或登录角色不正确");
        }
        return new SessionUser(user.userId(), user.username(), user.role());
    }

    @Override
    public void run(ApplicationArguments arguments) {
        initializeAccount("meeting.bootstrap.user", "user", Role.USER);
        initializeAccount("meeting.bootstrap.admin", "admin", Role.ADMIN);
    }

    private void initializeAccount(String prefix, String defaultName, Role role) {
        String password = environment.getProperty(prefix + ".password");
        if (password == null || password.isEmpty()) {
            return;
        }
        String username = Inputs.text(environment.getProperty(prefix + ".username", defaultName), 50, "初始账号");
        User existing = users.findByUsername(username);
        if (existing != null) {
            verifyRole(existing, role);
            return;
        }
        if (Inputs.trimWhitespace(password).isEmpty() || password.length() < 12 || password.length() > 256) {
            throw ApiException.invalid("初始密码须包含 12 至 256 个字符");
        }
        try {
            users.insert(username, PasswordHasher.hash(password), role);
        } catch (DuplicateKeyException error) {
            verifyRole(users.findByUsername(username), role);
        }
    }

    private void verifyRole(User existing, Role expected) {
        if (existing == null || existing.role() != expected) {
            throw new IllegalStateException("初始账号与现有账号的角色冲突");
        }
    }
}
