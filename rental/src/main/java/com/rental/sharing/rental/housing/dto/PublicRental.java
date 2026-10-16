package com.rental.sharing.rental.housing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 公共列表专用投影，不包含详细地址、状态或用户信息。 */
public record PublicRental(
        long id, String city, String district, String communityName,
        String rentalPlatform, String landlordType, String roomType, BigDecimal monthlyRent,
        String depositDesc, LocalDate rentStartDate, LocalDate rentEndDate, Integer rating,
        String highlights, String pitfalls, String content,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
