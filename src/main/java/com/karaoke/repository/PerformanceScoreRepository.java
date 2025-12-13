package com.karaoke.repository;

import com.karaoke.model.PerformanceScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PerformanceScoreRepository extends JpaRepository<PerformanceScore, String> {
}
