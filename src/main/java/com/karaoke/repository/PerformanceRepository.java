package com.karaoke.repository;

import com.karaoke.model.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceRepository extends JpaRepository<Performance, String> {
    List<Performance> findByUserId(Long userId);
    List<Performance> findBySongId(String songId);
}