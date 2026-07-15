package net.citotech.cito.reconciliation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/statements")
public class StatementCheckController {
    private final ProviderStatementValidator validator;

    public StatementCheckController(ProviderStatementValidator validator) {
        this.validator = validator;
    }

    @PostMapping(path = "/check")
    public long check(@RequestParam("provider") String provider,
                      @RequestParam(value = "fileName", defaultValue = "statement.csv") String fileName,
                      @RequestBody String csvText) {
        return validator.validate(provider, fileName, csvText);
    }
}

