package com.deep.WIMB.controller;

import com.deep.WIMB.dto.ActiveRideResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/active/all-buses")
    public String getAllActiveRides() {
        return "admin";
    }
}
