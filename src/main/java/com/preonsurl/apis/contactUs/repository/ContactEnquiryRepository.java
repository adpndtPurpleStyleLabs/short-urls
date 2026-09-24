package com.preonsurl.apis.contactUs.repository;

import com.preonsurl.apis.contactUs.entity.ContactEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactEnquiryRepository extends JpaRepository<ContactEnquiry, Long> {
    List<ContactEnquiry> findAllByOrderByCreatedAtDesc();
    List<ContactEnquiry> findByEmailOrderByCreatedAtDesc(String email);
    List<ContactEnquiry> findByTopicOrderByCreatedAtDesc(String topic);
}
