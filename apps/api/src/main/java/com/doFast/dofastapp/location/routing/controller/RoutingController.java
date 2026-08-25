package com.doFast.dofastapp.location.routing.controller;

import com.doFast.dofastapp.location.routing.dto.RouteQuoteRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteResponse;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    private final RouteQuoteService routeQuoteService;

    public RoutingController(RouteQuoteService routeQuoteService) {
        this.routeQuoteService = routeQuoteService;
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteQuoteResponse createQuote(
            @RequestBody @Valid RouteQuoteRequest request,
            @AuthenticationPrincipal User user
    ) {
        return routeQuoteService.createQuote(request, user);
    }

    @GetMapping("/quotes/{id}")
    public RouteQuoteResponse getQuote(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return routeQuoteService.getQuote(id, user);
    }
}
