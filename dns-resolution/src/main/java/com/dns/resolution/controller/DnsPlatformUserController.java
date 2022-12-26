package com.dns.resolution.controller;

import com.dns.common.core.domain.AjaxResult;
import com.dns.resolution.annotation.Auth;
import com.dns.resolution.domain.req.UserBody;
import com.dns.resolution.service.DnsPlatformUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/web/user")
public class DnsPlatformUserController {

    @Autowired
    private DnsPlatformUserService dnsPlatformUserService;


    @PostMapping("/register/check")
    public AjaxResult registerCheck(@RequestBody UserBody userBody) {
        return AjaxResult.success(dnsPlatformUserService.registerCheckService(userBody));
    }

    @PostMapping("/register")
    public AjaxResult register(@RequestBody UserBody userBody) {
        return AjaxResult.success(dnsPlatformUserService.registerService(userBody));
    }

    @PostMapping("/reset/check")
    public AjaxResult resetCheck(@RequestBody UserBody userBody) {
        return AjaxResult.success(dnsPlatformUserService.resetCheckService(userBody));
    }

    @PostMapping("/reset")
    public AjaxResult reset(@RequestBody UserBody userBody) {
        return AjaxResult.success(dnsPlatformUserService.resetService(userBody));
    }

    @PostMapping("/login")
    public AjaxResult login(@RequestBody UserBody userBody) {
        return AjaxResult.success(dnsPlatformUserService.login(userBody));
    }
}
