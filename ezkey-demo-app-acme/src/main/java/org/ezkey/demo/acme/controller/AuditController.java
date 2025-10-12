/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuditController
 * Description: Controller for displaying audit logs in the demo application
 */

package org.ezkey.demo.acme.controller;

import org.ezkey.demo.acme.dto.AuditLogDto;
import org.ezkey.demo.acme.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for displaying audit logs.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
@RequestMapping("/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        
        AuditService.AuditLogsResult result = auditService.getAuditLogs(page, size);
        
        model.addAttribute("auditLogs", result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("pageSize", size);
        
        return "audit-logs/list";
    }
}
