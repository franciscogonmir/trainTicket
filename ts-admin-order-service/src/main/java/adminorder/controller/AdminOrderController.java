package adminorder.controller;

import adminorder.entity.*;
import adminorder.service.AdminOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("/api/v1/adminorderservice")
public class AdminOrderController {

    @Autowired
    AdminOrderService adminOrderService;

    @GetMapping(path = "/welcome")
    public String home(@RequestHeader HttpHeaders headers) {
        return "Welcome to [ AdminOrder Service ] !";
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/adminorder/{loginid}")
    public HttpEntity getAllOrders(@PathVariable String loginid, @RequestHeader HttpHeaders headers) {
        return ok(adminOrderService.getAllOrders(loginid, headers));
    }

    @PostMapping(value = "/adminorder")
    public HttpEntity addOrder(@RequestBody Order request, @RequestHeader HttpHeaders headers) {
        return ok(adminOrderService.addOrder(request, headers));
    }

    @PutMapping(value = "/adminorder")
    public HttpEntity updateOrder(@RequestBody Order request, @RequestHeader HttpHeaders headers) {
        return ok(adminOrderService.updateOrder(request, headers));
    }

    @DeleteMapping(value = "/adminorder/{loginid}/{orderId}/{trainNumber}")
    public HttpEntity deleteOrder(@PathVariable String loginid, @PathVariable String orderId, @PathVariable String trainNumber, @RequestHeader HttpHeaders headers) {
        return ok(adminOrderService.deleteOrder(loginid, orderId, trainNumber, headers));
    }

}
