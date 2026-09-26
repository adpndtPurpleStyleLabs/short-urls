package com.preonsurl.apis.billing.repository;

import com.preonsurl.apis.billing.entity.UserBillingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingAddressRepository extends JpaRepository<UserBillingAddress, Long> {

    Optional<UserBillingAddress> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
