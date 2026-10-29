package kz.kayak.bookkeeper.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ManagementUiController {
    @GetMapping("/")
    String managementUi() {
        return "forward:/index.html";
    }
}
