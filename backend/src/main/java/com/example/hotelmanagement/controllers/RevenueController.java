package com.example.hotelmanagement.controllers;

import com.example.hotelmanagement.dto.revenue.DailyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.MonthlyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.OccupancyMetrics;
import com.example.hotelmanagement.dto.revenue.SourceRevenueBreakdown;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.example.hotelmanagement.services.RevenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/revenue", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize(PermissionExpressions.REVENUE_READ)
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/occupancy")
    public ResponseEntity<OccupancyMetrics> getOccupancyMetrics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(revenueService.getOccupancyMetrics(from, to));
    }

    @GetMapping("/daily")
    public ResponseEntity<List<DailyRevenuePoint>> getDailyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(revenueService.getDailyRevenue(from, to));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyRevenuePoint>> getMonthlyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(revenueService.getMonthlyRevenue(from, to));
    }

    @GetMapping("/by-source")
    public ResponseEntity<List<SourceRevenueBreakdown>> getRevenueBySource(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(revenueService.getRevenueBySource(from, to));
    }

    @GetMapping("/ota-commission")
    public ResponseEntity<BigDecimal> getOtaCommissionTotal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(revenueService.getOtaCommissionTotal(from, to));
    }
}
