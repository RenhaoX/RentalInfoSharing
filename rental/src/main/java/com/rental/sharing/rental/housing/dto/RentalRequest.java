package com.rental.sharing.rental.housing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 新增和完整替换共用的可编辑字段；归属、状态及审计字段由服务端维护。 */
public record RentalRequest(
        @NotBlank(message = "城市不能为空") @Size(max = 32, message = "城市最多32个字符") String city,
        @Size(max = 32, message = "区县最多32个字符") String district,
        @NotBlank(message = "详细地址不能为空") @Size(max = 255, message = "详细地址最多255个字符") String address,
        @Size(max = 64, message = "小区名称最多64个字符") String communityName,
        @Size(max = 64, message = "租赁平台最多64个字符") String rentalPlatform,
        @Size(max = 16, message = "房东类型最多16个字符") String landlordType,
        @Size(max = 16, message = "房间类型最多16个字符") String roomType,
        @DecimalMin(value = "0", message = "月租金不能为负")
        @Digits(integer = 8, fraction = 2, message = "月租金最多8位整数和2位小数") BigDecimal monthlyRent,
        @Size(max = 32, message = "押付说明最多32个字符") String depositDesc,
        LocalDate rentStartDate,
        LocalDate rentEndDate,
        @Min(value = 1, message = "评分范围为1至5") @Max(value = 5, message = "评分范围为1至5") Integer rating,
        @Size(max = 2000, message = "优点最多2000个字符") String highlights,
        @Size(max = 2000, message = "注意事项最多2000个字符") String pitfalls,
        @Size(max = 10000, message = "租住经验最多10000个字符") String content) {

    public RentalRequest {
        city = normalize(city);
        district = normalize(district);
        address = normalize(address);
        communityName = normalize(communityName);
        rentalPlatform = normalize(rentalPlatform);
        landlordType = normalize(landlordType);
        roomType = normalize(roomType);
        depositDesc = normalize(depositDesc);
        highlights = normalize(highlights);
        pitfalls = normalize(pitfalls);
        content = normalize(content);
    }

    @JsonIgnore
    @AssertTrue(message = "搬离日期不能早于入住日期")
    public boolean isRentalPeriodValid() {
        return rentStartDate == null || rentEndDate == null || !rentEndDate.isBefore(rentStartDate);
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
