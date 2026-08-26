"use client";

import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Calendar, Search, X } from "lucide-react";

export interface BookingFilterValues {
  search: string;
  status: string;
  source: string;
  checkInFrom: string;
  checkInTo: string;
}

interface BookingFiltersProps {
  filters: BookingFilterValues;
  onFiltersChange: (filters: BookingFilterValues) => void;
  onSearch: (filters?: BookingFilterValues) => void;
  sources?: { value: string; label: string }[];
}

const STATUS_OPTIONS = [
  { value: "all", label: "Tất cả trạng thái" },
  { value: "PENDING", label: "Chờ xử lý" },
  { value: "CONFIRMED", label: "Đã xác nhận" },
  { value: "CHECKED_IN", label: "Đã nhận phòng" },
  { value: "CHECKED_OUT", label: "Đã trả phòng" },
  { value: "CANCELLED", label: "Đã hủy" },
  { value: "NO_SHOW", label: "Không đến" },
];

const SOURCE_OPTIONS = [
  { value: "all", label: "Tất cả nguồn" },
  { value: "WEBSITE", label: "Website" },
  { value: "WALK_IN", label: "Walk-in" },
  { value: "PHONE", label: "Điện thoại" },
  { value: "BOOKING_COM", label: "Booking.com" },
  { value: "AGODA", label: "Agoda" },
];

const EMPTY_FILTERS: BookingFilterValues = {
  search: "",
  status: "all",
  source: "all",
  checkInFrom: "",
  checkInTo: "",
};

function formatDateInput(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  const parts = [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)]
    .filter(Boolean);

  return parts.join("/");
}

function isValidDateInput(value: string): boolean {
  if (!value) return true;

  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return false;

  const [, day, month, year] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day));

  return (
    date.getFullYear() === Number(year) &&
    date.getMonth() === Number(month) - 1 &&
    date.getDate() === Number(day)
  );
}

function DateFilterInput({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
}) {
  const isInvalid = Boolean(value) && !isValidDateInput(value);

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <div className="relative">
        <Calendar
          aria-hidden="true"
          className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
        />
        <Input
          aria-label={label}
          aria-invalid={isInvalid}
          inputMode="numeric"
          maxLength={10}
          placeholder="dd/mm/yyyy"
          value={value}
          onChange={(event) => onChange(formatDateInput(event.target.value))}
          className="pl-10"
        />
      </div>
      {isInvalid && (
        <p className="text-xs text-destructive">Nhập ngày theo dạng dd/mm/yyyy</p>
      )}
    </div>
  );
}

export function BookingFilters({
  filters,
  onFiltersChange,
  onSearch,
  sources = SOURCE_OPTIONS,
}: BookingFiltersProps) {
  const handleSearchKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") {
      onSearch(filters);
    }
  };

  const hasActiveFilters =
    filters.search ||
    filters.status !== "all" ||
    filters.source !== "all" ||
    filters.checkInFrom ||
    filters.checkInTo;
  const datesAreValid =
    isValidDateInput(filters.checkInFrom) && isValidDateInput(filters.checkInTo);

  return (
    <section className="rounded-lg border bg-card p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold">Bộ lọc đặt phòng</h2>
        </div>
        {hasActiveFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              onFiltersChange(EMPTY_FILTERS);
              onSearch(EMPTY_FILTERS);
            }}
          >
            <X data-icon="inline-start" />
            Xóa lọc
          </Button>
        )}
      </div>

      <div className="grid gap-3 lg:grid-cols-[minmax(240px,1fr)_180px_180px_minmax(260px,1fr)_auto] lg:items-start">
        <div className="relative">
          <Search
            aria-hidden="true"
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            aria-label="Tìm kiếm booking"
            placeholder="Mã booking, tên, email, SĐT..."
            value={filters.search}
            onChange={(event) =>
              onFiltersChange({ ...filters, search: event.target.value })
            }
            onKeyDown={handleSearchKeyDown}
            className="pl-10"
          />
        </div>

        <Select
          value={filters.status}
          onValueChange={(value) => onFiltersChange({ ...filters, status: value })}
        >
          <SelectTrigger aria-label="Lọc theo trạng thái">
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.source}
          onValueChange={(value) => onFiltersChange({ ...filters, source: value })}
        >
          <SelectTrigger aria-label="Lọc theo nguồn booking">
            <SelectValue placeholder="Nguồn booking" />
          </SelectTrigger>
          <SelectContent>
            {sources.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="grid min-w-0 grid-cols-2 gap-2">
          <DateFilterInput
            label="Từ ngày nhận phòng"
            value={filters.checkInFrom}
            onChange={(value) => onFiltersChange({ ...filters, checkInFrom: value })}
          />
          <DateFilterInput
            label="Đến ngày nhận phòng"
            value={filters.checkInTo}
            onChange={(value) => onFiltersChange({ ...filters, checkInTo: value })}
          />
        </div>

        <Button
          onClick={() => onSearch(filters)}
          disabled={!datesAreValid}
          className="w-full lg:w-auto"
        >
          <Search data-icon="inline-start" />
          Tìm kiếm
        </Button>
      </div>
    </section>
  );
}
