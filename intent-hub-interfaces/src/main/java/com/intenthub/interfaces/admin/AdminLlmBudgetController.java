package com.intenthub.interfaces.admin;

import com.intenthub.application.llm.LlmBudgetAppService;
import com.intenthub.application.llm.LlmBudgetUsage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/llm/budget-usage")
public class AdminLlmBudgetController {
    private final LlmBudgetAppService budgetAppService;

    public AdminLlmBudgetController(LlmBudgetAppService budgetAppService) {
        this.budgetAppService = budgetAppService;
    }

    @GetMapping
    public LlmBudgetUsage dailyUsage(
            @RequestParam String tenantId,
            @RequestParam String sceneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate usageDate
    ) {
        return budgetAppService.dailyUsage(tenantId, sceneId, usageDate);
    }
}
