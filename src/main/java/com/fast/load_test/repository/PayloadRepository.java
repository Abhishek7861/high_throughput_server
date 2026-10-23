package com.fast.load_test.repository;

import com.fast.load_test.entity.Payload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayloadRepository extends JpaRepository<Payload, Long> {
}
