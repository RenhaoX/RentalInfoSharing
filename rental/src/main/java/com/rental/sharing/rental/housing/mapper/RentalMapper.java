package com.rental.sharing.rental.housing.mapper;

import com.rental.sharing.rental.housing.dto.OwnedRental;
import com.rental.sharing.rental.housing.dto.PublicRental;
import com.rental.sharing.rental.housing.dto.RentalQuery;
import com.rental.sharing.rental.housing.dto.RentalRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RentalMapper {
    long insert(@Param("userId") long userId, @Param("record") RentalRequest record);

    int replaceOwned(@Param("id") long id, @Param("userId") long userId,
                     @Param("record") RentalRequest record);

    int deleteOwned(@Param("id") long id, @Param("userId") long userId);

    Long findUndeletedOwner(long id);

    long count(@Param("query") RentalQuery query, @Param("userId") Long userId);

    List<PublicRental> listPublished(@Param("query") RentalQuery query);

    List<OwnedRental> listOwned(@Param("query") RentalQuery query, @Param("userId") long userId);
}
