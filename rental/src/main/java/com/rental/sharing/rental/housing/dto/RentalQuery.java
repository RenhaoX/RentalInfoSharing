package com.rental.sharing.rental.housing.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RentalQuery(
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 32) String city,
        @Size(max = 32) String district,
        @Size(max = 64) String rentalPlatform,
        @Size(max = 16) String landlordType,
        @Size(max = 16) String roomType,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minRent,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal maxRent,
        @Min(1) @Max(5) Integer minRating,
        @Size(max = 10000) String keyword) {

    public RentalQuery {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
        city = RentalRequest.normalize(city);
        district = RentalRequest.normalize(district);
        rentalPlatform = RentalRequest.normalize(rentalPlatform);
        landlordType = RentalRequest.normalize(landlordType);
        roomType = RentalRequest.normalize(roomType);
        keyword = RentalRequest.normalize(keyword);
    }

    @AssertTrue(message = "最低租金不能高于最高租金")
    public boolean isRentRangeValid() {
        return minRent == null || maxRent == null || minRent.compareTo(maxRent) <= 0;
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    /** 使用 ! 作为 LIKE 转义字符；百分号、下划线与反斜杠均按原文匹配。 */
    public String keywordPattern() {
        return keyword == null ? null : "%" + keyword.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
    }
}
