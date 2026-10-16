package com.rental.sharing.rental.housing.dto;

import java.util.List;

public record RentalPage<T>(List<T> items, int page, int size, long total) {
}
