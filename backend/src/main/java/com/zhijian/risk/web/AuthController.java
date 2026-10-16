package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关 HTTP 接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册并返回登录态。
     * @param req req
     */
    @PostMapping("/register")
    public Dtos.AuthResp register(@Valid @RequestBody Dtos.RegisterReq req) {
        log.info("register start");

        return authService.register(req);
    }

    /**
     * 用户登录，可选绑定组织。
     * @param req req
     */
    @PostMapping("/login")
    public Dtos.AuthResp login(@Valid @RequestBody Dtos.LoginReq req) {
        log.info("login start");

        return authService.login(req);
    }

    /**
     * 返回当前登录用户信息。
     */
    @GetMapping("/me")
    public Dtos.AuthResp me() {
        log.debug("me start");

        UserPrincipal p = AuthSupport.requireUser();
        return authService.me(p.getUserId(), p.getOrgId(), p.getOrgRole());
    }

    /**
     * 切换当前组织并重签 JWT。
     * @param req req
     */
    /**
     * 修改当前用户密码。
     */
    @PostMapping("/password")
    public void changePassword(@Valid @RequestBody Dtos.ChangePasswordReq req) {
        log.info("changePassword start");
        authService.changePassword(AuthSupport.requireUser().getUserId(), req);
    }

    @PostMapping("/switch-org")
    public Dtos.AuthResp switchOrg(@Valid @RequestBody Dtos.SwitchOrgReq req) {
        log.info("switchOrg start");

        UserPrincipal p = AuthSupport.requireUser();
        return authService.switchOrg(p.getUserId(), req.orgId());
    }
}
