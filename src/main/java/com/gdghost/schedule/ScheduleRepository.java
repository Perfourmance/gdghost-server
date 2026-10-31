package com.gdghost.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
