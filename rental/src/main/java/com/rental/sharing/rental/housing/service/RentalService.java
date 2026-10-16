package com.rental.sharing.rental.housing.service;

import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import com.rental.sharing.identity.api.IdentityAccess;
import com.rental.sharing.rental.housing.dto.OwnedRental;
import com.rental.sharing.rental.housing.dto.PublicRental;
import com.rental.sharing.rental.housing.dto.RentalPage;
import com.rental.sharing.rental.housing.dto.RentalQuery;
import com.rental.sharing.rental.housing.dto.RentalRequest;
import com.rental.sharing.rental.housing.mapper.RentalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalService {
    private final RentalMapper rentals;
    private final IdentityAccess identity;

    public RentalService(RentalMapper rentals, IdentityAccess identity) {
        this.rentals = rentals;
        this.identity = identity;
    }

    @Transactional
    public long create(long userId, RentalRequest request) {
        identity.requireActive(userId);
        return rentals.insert(userId, request);
    }

    @Transactional
    public void replace(long userId, long id, RentalRequest request) {
        identity.requireActive(userId);
        if (rentals.replaceOwned(id, userId, request) == 0) {
            throwChangeFailure(userId, id);
        }
    }

    @Transactional
    public void delete(long userId, long id) {
        identity.requireActive(userId);
        if (rentals.deleteOwned(id, userId) == 0) {
            throwChangeFailure(userId, id);
        }
    }

    @Transactional(readOnly = true)
    public RentalPage<PublicRental> list(long userId, RentalQuery query) {
        identity.requireActive(userId);
        long total = rentals.count(query, null);
        return new RentalPage<>(rentals.listPublished(query), query.page(), query.size(), total);
    }

    @Transactional(readOnly = true)
    public RentalPage<OwnedRental> mine(long userId, RentalQuery query) {
        identity.requireActive(userId);
        long total = rentals.count(query, userId);
        return new RentalPage<>(rentals.listOwned(query, userId), query.page(), query.size(), total);
    }

    private void throwChangeFailure(long userId, long id) {
        Long owner = rentals.findUndeletedOwner(id);
        throw new BusinessException(owner != null && owner != userId ? ErrorCode.FORBIDDEN : ErrorCode.NOT_FOUND);
    }
}
