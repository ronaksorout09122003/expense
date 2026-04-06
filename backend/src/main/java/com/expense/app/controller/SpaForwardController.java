package com.expense.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/transactions", "/ledger", "/masters", "/planner", "/reports", "/household", "/history", "/settings"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
