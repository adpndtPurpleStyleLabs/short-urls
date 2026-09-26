package com.preonsurl.apis.billing.repository;

import com.preonsurl.apis.billing.entity.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, Long> {

    List<BillingInvoice> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BillingInvoice> findByInvoiceNumber(String invoiceNumber);

    Optional<BillingInvoice> findByRazorpayOrderId(String razorpayOrderId);

    Optional<BillingInvoice> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
