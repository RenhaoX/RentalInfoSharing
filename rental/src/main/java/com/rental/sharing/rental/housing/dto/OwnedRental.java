package com.rental.sharing.rental.housing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 本人记录专用投影，额外提供完整地址和发布状态，供编辑表单使用。 */
public record OwnedRental(
        long id, String city, String district, String communityName,
        String rentalPlatform, String landlordType, String roomType, BigDecimal monthlyRent,
        String depositDesc, LocalDate rentStartDate, LocalDate rentEndDate, Integer rating,
        String highlights, String pitfalls, String content,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, String address, int status) {
}
