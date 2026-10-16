package com.zhijian.risk.web;

import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 注册、登录、当前用户与改密。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResult<Dtos.AuthResp> register(@Valid @RequestBody Dtos.RegisterReq req) {
        return ApiResult.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResult<Dtos.AuthResp> login(@Valid @RequestBody Dtos.LoginReq req) {
        return ApiResult.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ApiResult<Dtos.AuthResp> me() {
        UserPrincipal p = AuthSupport.requireUser();
        return ApiResult.ok(authService.me(p.getUserId(), p.getOrgId(), p.getOrgRole()));
    }

    @PostMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody Dtos.ChangePasswordReq req) {
        authService.changePassword(AuthSupport.requireUser().getUserId(), req);
        return ApiResult.ok(null);
    }

    @PostMapping("/switch-org")
    public ApiResult<Dtos.AuthResp> switchOrg(@Valid @RequestBody Dtos.SwitchOrgReq req) {
        UserPrincipal p = AuthSupport.requireUser();
        return ApiResult.ok(authService.switchOrg(p.getUserId(), req.orgId()));
    }
}
