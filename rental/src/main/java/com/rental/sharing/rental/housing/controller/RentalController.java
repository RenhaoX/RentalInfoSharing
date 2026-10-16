package com.rental.sharing.rental.housing.controller;

import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.identity.api.AuthenticatedUser;
import com.rental.sharing.rental.housing.dto.OwnedRental;
import com.rental.sharing.rental.housing.dto.PublicRental;
import com.rental.sharing.rental.housing.dto.RentalCreated;
import com.rental.sharing.rental.housing.dto.RentalPage;
import com.rental.sharing.rental.housing.dto.RentalQuery;
import com.rental.sharing.rental.housing.dto.RentalRequest;
import com.rental.sharing.rental.housing.service.RentalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rentals")
public class RentalController {
    private final RentalService rentals;

    public RentalController(RentalService rentals) {
        this.rentals = rentals;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RentalCreated>> create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RentalRequest request) {
        long id = rentals.create(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new RentalCreated(id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> replace(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable @Positive long id, @Valid @RequestBody RentalRequest request) {
        rentals.replace(user.userId(), id, request);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<RentalPage<PublicRental>> list(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @ModelAttribute RentalQuery query) {
        return ApiResponse.success(rentals.list(user.userId(), query));
    }

    @GetMapping("/mine")
    public ApiResponse<RentalPage<OwnedRental>> mine(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @ModelAttribute RentalQuery query) {
        return ApiResponse.success(rentals.mine(user.userId(), query));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long id) {
        rentals.delete(user.userId(), id);
        return ApiResponse.success(null);
    }
}
