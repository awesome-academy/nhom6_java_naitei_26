"use client";

import { Card, CardContent } from "@/components/ui/card";
import { BookingStats } from "@/types/booking-staff";

interface BookingStatsCardsProps {
  stats: BookingStats;
  isLoading?: boolean;
}

export function BookingStatsCards({ stats, isLoading }: BookingStatsCardsProps) {
  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <Card key={i} className="animate-pulse">
            <CardContent className="pt-6">
              <div className="h-8 w-12 rounded bg-muted" />
              <div className="mt-2 h-4 w-16 rounded bg-muted" />
            </CardContent>
          </Card>
        ))}
      </div>
    );
  }

  const statItems = [
    { label: "Tổng đơn", value: stats.total, color: "text-[var(--foreground)]" },
    { label: "Chờ xử lý", value: stats.pending, color: "text-yellow-600" },
    { label: "Đã xác nhận", value: stats.confirmed, color: "text-blue-600" },
    { label: "Đang ở", value: stats.checkedIn, color: "text-green-600" },
    { label: "Đã trả phòng", value: stats.checkedOut, color: "text-[var(--muted-foreground)]" },
    { label: "Đã hủy", value: stats.cancelled, color: "text-red-600" },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
      {statItems.map((stat) => (
        <Card key={stat.label}>
          <CardContent className="pt-6">
            <div className="text-2xl font-bold">{stat.value}</div>
            <p className={`text-sm ${stat.color}`}>{stat.label}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
