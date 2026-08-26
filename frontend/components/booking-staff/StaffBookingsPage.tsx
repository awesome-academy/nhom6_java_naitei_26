"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Badge, getBookingStatusVariant, getPaymentStatusVariant } from "@/components/ui/badge";
import { DataTable } from "@/components/ui/dataTable";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { BookingFilters, BookingFilterValues } from "./BookingFilters";
import { BookingStatsCards } from "./BookingStatsCards";
import { RoomAssignmentModal } from "./RoomAssignmentModal";
import { FolioPanel } from "@/components/admin/bookings/folio-panel";
import { InvoicePanel } from "@/components/admin/bookings/invoice-panel";
import {
  getBookings,
  getBookingDetail,
  confirmBooking,
  checkInBooking,
  checkOutBooking,
  cancelBooking,
} from "@/lib/api/booking-staff-api";
import { getActiveServiceItems } from "@/lib/api/folio";
import {
  BookingListItem,
  BookingStaffDetail,
  BookingListFilterRequest,
  BookingStatus,
  FolioChargeResponse,
} from "@/types/booking-staff";
import type { InvoiceResponse } from "@/types/invoice";
import type { ServiceItemOption } from "@/types/folio";
import { useAuth } from "@/lib/auth-context";
import {
  AlertCircle,
  Ban,
  CheckCircle2,
  Loader2,
  LogIn,
  LogOut,
  Plus,
} from "lucide-react";
import { format } from "date-fns";
import { vi } from "date-fns/locale";
import { toast } from "sonner";

// Status labels
const STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ xử lý",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đã nhận phòng",
  CHECKED_OUT: "Đã trả phòng",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
};

const PAYMENT_LABELS: Record<string, string> = {
  UNPAID: "Chưa thanh toán",
  PARTIALLY_PAID: "Thanh toán một phần",
  PAID: "Đã thanh toán",
  REFUNDED: "Đã hoàn tiền",
};

// Format currency
function formatCurrency(amount: number, currency: string = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
  }).format(amount);
}

// Format date
function formatDate(dateStr: string | null): string {
  if (!dateStr) return "-";
  return format(new Date(dateStr), "dd/MM/yyyy", { locale: vi });
}

// Format datetime
function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return "-";
  return format(new Date(dateStr), "dd/MM/yyyy HH:mm", { locale: vi });
}

function toIsoDate(value: string): string | null {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;

  const [, day, month, year] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day));
  if (
    date.getFullYear() !== Number(year) ||
    date.getMonth() !== Number(month) - 1 ||
    date.getDate() !== Number(day)
  ) {
    return null;
  }

  return `${year}-${month}-${day}`;
}

const EMPTY_FILTERS: BookingFilterValues = {
  search: "",
  status: "all",
  source: "all",
  checkInFrom: "",
  checkInTo: "",
};

export function StaffBookingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const canManageFolio = user?.permissions.includes("invoice:issue") ?? false;
  const canIssueInvoice = user?.permissions.includes("invoice:issue") ?? false;
  const canVoidInvoice = user?.permissions.includes("invoice:void") ?? false;
  const [loadError, setLoadError] = useState<string | null>(null);

  // State
  const [isLoading, setIsLoading] = useState(true);
  const [bookings, setBookings] = useState<BookingListItem[]>([]);
  const [stats, setStats] = useState({
    total: 0,
    pending: 0,
    confirmed: 0,
    checkedIn: 0,
    checkedOut: 0,
    cancelled: 0,
  });
  const [pagination, setPagination] = useState({
    page: 0,
    size: 20,
    totalItems: 0,
    totalPages: 0,
  });

  // Auth redirect
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/admin/login?redirect=%2Fadmin%2Fbookings");
    }
  }, [isAuthLoading, isAuthenticated, router]);

  // Filters
  const [filters, setFilters] = useState<BookingFilterValues>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] =
    useState<BookingFilterValues>(EMPTY_FILTERS);

  // Detail drawer
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [selectedBooking, setSelectedBooking] = useState<BookingStaffDetail | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [serviceItems, setServiceItems] = useState<ServiceItemOption[]>([]);
  const [isLoadingServiceItems, setIsLoadingServiceItems] = useState(false);
  const [serviceItemsError, setServiceItemsError] = useState<string | null>(null);

  // Room assignment modal
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [assigningRoomId, setAssigningRoomId] = useState<number | null>(null);
  const [assigningRoomType, setAssigningRoomType] = useState<string>("");

  // Cancel modal
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

  // Action loading states
  const [isActionLoading, setIsActionLoading] = useState(false);

  // Load bookings
  const loadBookings = useCallback(async () => {
    if (!isAuthenticated) {
      return false;
    }
    setIsLoading(true);
    setLoadError(null);
    try {
      const filterRequest: BookingListFilterRequest = {
        page: pagination.page,
        size: pagination.size,
      };

      if (appliedFilters.search) filterRequest.search = appliedFilters.search;
      if (appliedFilters.status !== "all") {
        filterRequest.status = [appliedFilters.status as BookingStatus];
      }
      if (appliedFilters.source !== "all") filterRequest.source = appliedFilters.source;

      const checkInFrom = toIsoDate(appliedFilters.checkInFrom);
      const checkInTo = toIsoDate(appliedFilters.checkInTo);
      if (checkInFrom) filterRequest.checkInFrom = checkInFrom;
      if (checkInTo) filterRequest.checkInTo = checkInTo;

      const response = await getBookings(filterRequest);
      setBookings(response.items);
      setStats(response.stats);
      setPagination({
        page: response.page,
        size: response.size,
        totalItems: response.totalItems,
        totalPages: response.totalPages,
      });
      return true;
    } catch (error) {
      console.error("Failed to load bookings:", error);
      setLoadError("Có lỗi khi tải danh sách đặt phòng");
      return false;
    } finally {
      setIsLoading(false);
    }
  }, [pagination.page, pagination.size, appliedFilters, isAuthenticated]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadBookings();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadBookings]);

  const loadServiceItems = useCallback(async () => {
    if (!isAuthenticated || !canManageFolio) {
      setServiceItems([]);
      setServiceItemsError(null);
      return;
    }

    setIsLoadingServiceItems(true);
    setServiceItemsError(null);
    try {
      setServiceItems(await getActiveServiceItems());
    } catch (error) {
      console.error("Failed to load service items:", error);
      setServiceItemsError(
        "Không thể tải danh mục dịch vụ. Dữ liệu Folio vẫn có thể xem ở chế độ chỉ đọc."
      );
    } finally {
      setIsLoadingServiceItems(false);
    }
  }, [canManageFolio, isAuthenticated]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadServiceItems();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadServiceItems]);

  // Load booking detail
  const openBookingDetail = async (publicId: string) => {
    setIsLoadingDetail(true);
    setIsDetailOpen(true);
    try {
      const detail = await getBookingDetail(publicId);
      setSelectedBooking(detail);
    } catch (error) {
      console.error("Failed to load booking detail:", error);
      setIsDetailOpen(false);
    } finally {
      setIsLoadingDetail(false);
    }
  };

  const refreshFolioAfterMutation = useCallback(async (bookingPublicId: string) => {
    const [detailResult, listResult] = await Promise.allSettled([
      getBookingDetail(bookingPublicId),
      loadBookings(),
    ]);

    if (detailResult.status === "fulfilled") {
      setSelectedBooking((current) =>
        current?.publicId === bookingPublicId ? detailResult.value : current
      );
    }

    const listRefreshFailed = listResult.status === "rejected" || !listResult.value;
    if (detailResult.status === "rejected" || listRefreshFailed) {
      console.error("Failed to refresh Folio aggregates after mutation", {
        bookingPublicId,
        detailError: detailResult.status === "rejected" ? detailResult.reason : null,
        listError: listResult.status === "rejected" ? listResult.reason : null,
      });
      toast.warning(
        "Khoản phát sinh đã được cập nhật, nhưng tổng tiền chưa thể đồng bộ. Vui lòng tải lại."
      );
    }
  }, [loadBookings]);

  const handleFolioChargeChanged = useCallback((charge: FolioChargeResponse) => {
    setSelectedBooking((current) => {
      if (!current || current.publicId !== charge.bookingPublicId) return current;

      const exists = current.folioCharges.some((item) => item.id === charge.id);
      const folioCharges = exists
        ? current.folioCharges.map((item) => item.id === charge.id ? charge : item)
        : [...current.folioCharges, charge];

      return { ...current, folioCharges };
    });
    void refreshFolioAfterMutation(charge.bookingPublicId);
  }, [refreshFolioAfterMutation]);

  const refreshInvoiceAfterMutation = useCallback(async (bookingPublicId: string) => {
    const [detailResult, listResult] = await Promise.allSettled([
      getBookingDetail(bookingPublicId),
      loadBookings(),
    ]);

    if (detailResult.status === "fulfilled") {
      setSelectedBooking((current) =>
        current?.publicId === bookingPublicId ? detailResult.value : current
      );
    }

    const listRefreshFailed = listResult.status === "rejected" || !listResult.value;
    if (detailResult.status === "rejected" || listRefreshFailed) {
      console.error("Failed to refresh invoice after mutation", {
        bookingPublicId,
        detailError: detailResult.status === "rejected" ? detailResult.reason : null,
        listError: listResult.status === "rejected" ? listResult.reason : null,
      });
      toast.warning(
        "Hóa đơn đã được cập nhật, nhưng dữ liệu booking chưa thể đồng bộ. Vui lòng tải lại."
      );
    }
  }, [loadBookings]);

  const handleInvoiceChanged = useCallback((invoice: InvoiceResponse, refresh = false) => {
    setSelectedBooking((current) => {
      if (!current || current.publicId !== invoice.bookingPublicId) return current;

      const exists = current.invoices.some((item) => item.publicId === invoice.publicId);
      const invoices = exists
        ? current.invoices.map((item) => item.publicId === invoice.publicId ? invoice : item)
        : [...current.invoices, invoice];
      return { ...current, invoices };
    });

    if (refresh) {
      void refreshInvoiceAfterMutation(invoice.bookingPublicId);
    }
  }, [refreshInvoiceAfterMutation]);

  // Confirm booking
  const handleConfirm = async () => {
    if (!selectedBooking) return;
    setIsActionLoading(true);
    try {
      await confirmBooking(selectedBooking.publicId);
      await loadBookings();
      await openBookingDetail(selectedBooking.publicId);
    } catch (error) {
      console.error("Failed to confirm booking:", error);
      alert("Không thể xác nhận booking. Vui lòng thử lại.");
    } finally {
      setIsActionLoading(false);
    }
  };

  // Check-in
  const handleCheckIn = async () => {
    if (!selectedBooking) return;
    if (!confirm("Xác nhận check-in cho booking này?")) return;
    setIsActionLoading(true);
    try {
      await checkInBooking(selectedBooking.publicId);
      await loadBookings();
      await openBookingDetail(selectedBooking.publicId);
    } catch (error) {
      console.error("Failed to check-in:", error);
      alert("Không thể check-in. Vui lòng thử lại.");
    } finally {
      setIsActionLoading(false);
    }
  };

  // Check-out
  const handleCheckOut = async () => {
    if (!selectedBooking) return;
    if (!confirm("Xác nhận check-out cho booking này?")) return;
    setIsActionLoading(true);
    try {
      await checkOutBooking(selectedBooking.publicId);
      await loadBookings();
      await openBookingDetail(selectedBooking.publicId);
    } catch (error) {
      console.error("Failed to check-out:", error);
      alert("Không thể check-out. Vui lòng thử lại.");
    } finally {
      setIsActionLoading(false);
    }
  };

  // Cancel booking
  const handleCancel = async () => {
    if (!selectedBooking || !cancelReason.trim()) return;
    setIsActionLoading(true);
    try {
      await cancelBooking(selectedBooking.publicId, { reason: cancelReason });
      await loadBookings();
      await openBookingDetail(selectedBooking.publicId);
      setIsCancelModalOpen(false);
      setCancelReason("");
    } catch (error) {
      console.error("Failed to cancel booking:", error);
      alert("Không thể hủy booking. Vui lòng thử lại.");
    } finally {
      setIsActionLoading(false);
    }
  };

  // Open room assignment modal
  const openAssignModal = (bookingRoomId: number, roomType: string) => {
    if (!selectedBooking) return;
    setAssigningRoomId(bookingRoomId);
    setAssigningRoomType(roomType);
    setIsAssignModalOpen(true);
  };

  // Room assignment success
  const handleAssignSuccess = async () => {
    if (!selectedBooking) return;
    await loadBookings();
    await openBookingDetail(selectedBooking.publicId);
  };

  // Table columns
  const columns = [
    {
      key: "bookingCode",
      header: "Mã đặt phòng",
      render: (row: BookingListItem) => (
        <span className="font-mono text-sm">{row.bookingCode}</span>
      ),
    },
    {
      key: "guest",
      header: "Khách hàng",
      render: (row: BookingListItem) => (
        <div>
          <p className="font-medium">{row.contactName}</p>
          <p className="text-xs text-[var(--muted-foreground)]">
            {row.contactEmail || row.contactPhone || "-"}
          </p>
        </div>
      ),
    },
    {
      key: "rooms",
      header: "Phòng",
      render: (row: BookingListItem) => (
        <div>
          {row.rooms.map((room, idx) => (
            <div key={idx} className="text-sm">
              <span className="font-medium">
                {room.roomNumber || <span className="text-muted-foreground italic">Chưa gán</span>}
              </span>
              <span className="text-muted-foreground ml-1">({room.roomTypeName})</span>
            </div>
          ))}
        </div>
      ),
    },
    {
      key: "dates",
      header: "Ngày",
      render: (row: BookingListItem) => (
        <div>
          <p className="text-sm">
            {formatDate(row.dates.earliestCheckIn)} → {formatDate(row.dates.latestCheckOut)}
          </p>
          <p className="text-xs text-[var(--muted-foreground)]">
            {row.dates.totalNights} đêm
          </p>
        </div>
      ),
    },
    {
      key: "status",
      header: "Trạng thái",
      render: (row: BookingListItem) => (
        <Badge variant={getBookingStatusVariant(row.status)}>
          {STATUS_LABELS[row.status] || row.status}
        </Badge>
      ),
    },
    {
      key: "payment",
      header: "Thanh toán",
      render: (row: BookingListItem) => (
        <Badge variant={getPaymentStatusVariant(row.paymentStatus)}>
          {PAYMENT_LABELS[row.paymentStatus] || row.paymentStatus}
        </Badge>
      ),
    },
    {
      key: "total",
      header: "Tổng tiền",
      className: "text-right font-medium",
      render: (row: BookingListItem) => formatCurrency(row.totalAmount, row.currency),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Quản lý đặt phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Theo dõi và quản lý tất cả đơn đặt phòng
          </p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Tạo đơn mới
        </Button>
      </div>

      {/* Stats */}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-lg">
          <p className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4" />
            {loadError}
          </p>
        </div>
      )}

      {isAuthLoading || !isAuthenticated ? (
        <div className="flex items-center justify-center h-64">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : (
      <>
      <BookingStatsCards stats={stats} isLoading={isLoading} />

      {/* Filters */}
      <BookingFilters
        filters={filters}
        onFiltersChange={setFilters}
        onSearch={(nextFilters = filters) => {
          setAppliedFilters(nextFilters);
          setPagination((p) => ({ ...p, page: 0 }));
        }}
      />

      {/* Bookings Table */}
      <DataTable
        columns={columns}
        data={bookings}
        keyExtractor={(row) => row.publicId}
        isLoading={isLoading}
        emptyMessage="Không tìm thấy đơn đặt phòng nào"
        onRowClick={(row) => openBookingDetail(row.publicId)}
        pagination={{
          page: pagination.page,
          size: pagination.size,
          totalItems: pagination.totalItems,
          totalPages: pagination.totalPages,
          onPageChange: (page) => setPagination((p) => ({ ...p, page })),
        }}
      />

      {/* Booking Detail Drawer */}
      <Sheet open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <SheetContent className="w-full overflow-y-auto p-6 sm:max-w-4xl">
          {isLoadingDetail ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : selectedBooking ? (
            <>
              <SheetHeader className="gap-3 border-b pb-4">
                <div className="flex flex-wrap items-start justify-between gap-3 pr-8">
                  <div>
                    <SheetTitle className="text-xl font-bold">
                      Booking {selectedBooking.bookingCode}
                    </SheetTitle>
                    <SheetDescription className="mt-1">
                      Tạo lúc {formatDateTime(selectedBooking.createdAt)} · {selectedBooking.sourceName}
                    </SheetDescription>
                  </div>
                  <Badge variant={getBookingStatusVariant(selectedBooking.status)}>
                    {STATUS_LABELS[selectedBooking.status]}
                  </Badge>
                </div>
              </SheetHeader>

              {/* Action Buttons */}
              <div className="flex flex-wrap gap-2 border-b py-4">
                {selectedBooking.status === "PENDING" && (
                  <Button
                    size="sm"
                    onClick={handleConfirm}
                    disabled={isActionLoading}
                  >
                    {isActionLoading ? (
                      <Loader2 data-icon="inline-start" className="animate-spin" />
                    ) : (
                      <CheckCircle2 data-icon="inline-start" />
                    )}
                    Xác nhận
                  </Button>
                )}
                {selectedBooking.status === "CONFIRMED" && (
                  <>
                    <Button
                      size="sm"
                      onClick={handleCheckIn}
                      disabled={isActionLoading}
                    >
                      {isActionLoading ? (
                        <Loader2 data-icon="inline-start" className="animate-spin" />
                      ) : (
                        <LogIn data-icon="inline-start" />
                      )}
                      Check-in
                    </Button>
                  </>
                )}
                {selectedBooking.status === "CHECKED_IN" && (
                  <Button
                    size="sm"
                    onClick={handleCheckOut}
                    disabled={isActionLoading}
                  >
                    {isActionLoading ? (
                      <Loader2 data-icon="inline-start" className="animate-spin" />
                    ) : (
                      <LogOut data-icon="inline-start" />
                    )}
                    Check-out
                  </Button>
                )}
                {(selectedBooking.status === "PENDING" ||
                  selectedBooking.status === "CONFIRMED") && (
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => setIsCancelModalOpen(true)}
                    disabled={isActionLoading}
                  >
                    <Ban data-icon="inline-start" />
                    Hủy
                  </Button>
                )}
              </div>

              {/* Tabs */}
              <Tabs defaultValue="info" className="mt-4">
                <TabsList className="w-full justify-start overflow-x-auto">
                  <TabsTrigger value="info">Thông tin</TabsTrigger>
                  <TabsTrigger value="rooms">Phòng</TabsTrigger>
                  <TabsTrigger value="guests">Khách</TabsTrigger>
                  <TabsTrigger value="folio">Folio</TabsTrigger>
                  <TabsTrigger value="invoice">Hóa đơn</TabsTrigger>
                  <TabsTrigger value="payments">Thanh toán</TabsTrigger>
                  <TabsTrigger value="history">Lịch sử</TabsTrigger>
                </TabsList>

                {/* Info Tab */}
                <TabsContent value="info" className="mt-5 flex flex-col gap-5">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <Label className="text-muted-foreground">Khách hàng</Label>
                      <p className="font-medium">{selectedBooking.contactName}</p>
                    </div>
                    <div>
                      <Label className="text-muted-foreground">Email</Label>
                      <p>{selectedBooking.contactEmail || "-"}</p>
                    </div>
                    <div>
                      <Label className="text-muted-foreground">SĐT</Label>
                      <p>{selectedBooking.contactPhone || "-"}</p>
                    </div>
                    <div>
                      <Label className="text-muted-foreground">Nguồn</Label>
                      <p>{selectedBooking.sourceName}</p>
                    </div>
                    <div>
                      <Label className="text-muted-foreground">Số khách</Label>
                      <p>{selectedBooking.adults} người lớn{selectedBooking.children > 0 ? `, ${selectedBooking.children} trẻ em` : ""}</p>
                    </div>
                    <div>
                      <Label className="text-muted-foreground">Ngày tạo</Label>
                      <p>{formatDateTime(selectedBooking.createdAt)}</p>
                    </div>
                  </div>

                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base">Tóm tắt thanh toán</CardTitle>
                    </CardHeader>
                    <CardContent>
                    <Label className="text-muted-foreground">Tổng tiền</Label>
                    <p className="mt-1 text-2xl font-bold">
                      {formatCurrency(selectedBooking.totalAmount, selectedBooking.currency)}
                    </p>
                    <div className="text-sm text-muted-foreground mt-1">
                      <span>Phòng: {formatCurrency(selectedBooking.roomsTotal)}</span>
                      {selectedBooking.servicesTotal > 0 && (
                        <span className="ml-2">| Dịch vụ: {formatCurrency(selectedBooking.servicesTotal)}</span>
                      )}
                      {selectedBooking.discountTotal > 0 && (
                        <span className="ml-2">| Giảm giá: -{formatCurrency(selectedBooking.discountTotal)}</span>
                      )}
                      <span className="ml-2">| Thuế: {formatCurrency(selectedBooking.taxTotal)}</span>
                    </div>
                    <div className="text-sm mt-2">
                      <span>Đã thanh toán: </span>
                      <span className="text-green-600 font-medium">
                        {formatCurrency(selectedBooking.paidAmount)}
                      </span>
                      {selectedBooking.refundedAmount > 0 && (
                        <>
                          {" | "}
                          <span>Đã hoàn: </span>
                          <span className="text-orange-600 font-medium">
                            {formatCurrency(selectedBooking.refundedAmount)}
                          </span>
                        </>
                      )}
                    </div>
                    </CardContent>
                  </Card>

                  {selectedBooking.specialRequests && (
                    <div className="rounded-lg border bg-muted/30 p-4">
                      <Label className="text-muted-foreground">Yêu cầu đặc biệt</Label>
                      <p className="mt-1 whitespace-pre-wrap">{selectedBooking.specialRequests}</p>
                    </div>
                  )}
                </TabsContent>

                {/* Rooms Tab */}
                <TabsContent value="rooms" className="mt-5 flex flex-col gap-4">
                  {selectedBooking.rooms.map((room) => (
                    <Card key={room.id}>
                      <CardHeader className="pb-2">
                        <div className="flex items-center justify-between">
                          <CardTitle className="text-base">
                            {room.roomNumber ? (
                              <>Phòng {room.roomNumber}</>
                            ) : (
                              <span className="text-muted-foreground italic">Chưa gán phòng</span>
                            )}
                          </CardTitle>
                          <div className="flex items-center gap-2">
                            <Badge variant={room.bookingRoomStatus === "RESERVED" ? "pending" : "success"}>
                              {room.bookingRoomStatus === "RESERVED" ? "Đã đặt" : "Đang ở"}
                            </Badge>
                            {!room.roomId && selectedBooking.status === "CONFIRMED" && (
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => openAssignModal(room.id, room.roomTypeName)}
                              >
                                Gán phòng
                              </Button>
                            )}
                          </div>
                        </div>
                      </CardHeader>
                      <CardContent className="space-y-2">
                        <div className="text-sm">
                          <span className="text-muted-foreground">Loại phòng: </span>
                          <span>{room.roomTypeName}</span>
                        </div>
                        <div className="text-sm">
                          <span className="text-muted-foreground">Ngày: </span>
                          <span>
                            {formatDate(room.checkInDate)} → {formatDate(room.checkOutDate)} ({room.nights} đêm)
                          </span>
                        </div>
                        <div className="text-sm">
                          <span className="text-muted-foreground">Giá: </span>
                          <span>{formatCurrency(room.roomSubtotal)}</span>
                        </div>
                        {room.nightlyRates.length > 0 && (
                          <div className="border-t pt-2 mt-2">
                            <p className="text-sm text-muted-foreground mb-1">Giá theo đêm:</p>
                            <div className="grid grid-cols-3 gap-1">
                              {room.nightlyRates.map((rate, i) => (
                                <div key={i} className="text-xs bg-muted/50 rounded px-2 py-1">
                                  {formatDate(rate.stayDate)}: {formatCurrency(rate.price)}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  ))}
                </TabsContent>

                {/* Guests Tab */}
                <TabsContent value="guests" className="mt-5 flex flex-col gap-4">
                  {selectedBooking.guests.length === 0 ? (
                    <p className="text-muted-foreground">Chưa có thông tin khách</p>
                  ) : (
                    selectedBooking.guests.map((guest) => (
                      <Card key={guest.id}>
                        <CardContent className="pt-4">
                          <div className="flex items-center justify-between">
                            <div>
                              <p className="font-medium">{guest.fullName}</p>
                              <p className="text-sm text-muted-foreground">
                                {guest.nationality || "Không rõ quốc tịch"}
                              </p>
                            </div>
                            {guest.hasIdDocument && (
                              <Badge variant="success">Có CCCD</Badge>
                            )}
                          </div>
                        </CardContent>
                      </Card>
                    ))
                  )}
                </TabsContent>

                {/* Folio Tab */}
                <TabsContent value="folio" className="mt-5">
                  <FolioPanel
                    booking={selectedBooking}
                    serviceItems={serviceItems}
                    canManage={canManageFolio}
                    isLoadingServiceItems={isLoadingServiceItems}
                    serviceItemsError={serviceItemsError}
                    onRetryServiceItems={loadServiceItems}
                    onChargeChanged={handleFolioChargeChanged}
                  />
                </TabsContent>

                {/* Invoice Tab */}
                <TabsContent value="invoice" className="mt-5">
                  <InvoicePanel
                    booking={selectedBooking}
                    canIssue={canIssueInvoice}
                    canVoid={canVoidInvoice}
                    onChanged={handleInvoiceChanged}
                  />
                </TabsContent>

                {/* Payments Tab */}
                <TabsContent value="payments" className="mt-5 flex flex-col gap-4">
                  {selectedBooking.payments.length === 0 ? (
                    <p className="text-muted-foreground">Chưa có thanh toán</p>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {selectedBooking.payments.map((payment) => (
                        <Card key={payment.paymentCode}>
                          <CardContent className="pt-4">
                            <div className="flex items-center justify-between">
                              <div>
                                <div className="font-medium">
                                  {payment.paymentCode}
                                  <Badge
                                    variant={payment.status === "COMPLETED" ? "success" : "pending"}
                                    className="ml-2"
                                  >
                                    {payment.status === "COMPLETED" ? "Hoàn thành" : payment.status}
                                  </Badge>
                                </div>
                                <p className="text-sm text-muted-foreground">
                                  {payment.method} | {formatDateTime(payment.createdAt)}
                                </p>
                              </div>
                              <p className="font-medium">
                                {formatCurrency(payment.amount, payment.currency)}
                              </p>
                            </div>
                          </CardContent>
                        </Card>
                      ))}
                    </div>
                  )}
                </TabsContent>

                {/* History Tab */}
                <TabsContent value="history" className="mt-5 flex flex-col gap-4">
                  {selectedBooking.statusHistory.length === 0 ? (
                    <p className="text-muted-foreground">Không có lịch sử</p>
                  ) : (
                    <div className="relative">
                      <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-border" />
                      <div className="flex flex-col gap-4">
                        {selectedBooking.statusHistory.map((history, idx) => (
                          <div key={idx} className="relative pl-10">
                            <div className="absolute left-2.5 top-1 w-3 h-3 rounded-full bg-primary border-2 border-background" />
                            <div>
                              <p className="text-sm">
                                {history.fromStatus ? (
                                  <>
                                    <span className="font-medium">{STATUS_LABELS[history.fromStatus]}</span>
                                    <span className="text-muted-foreground mx-2">→</span>
                                    <span className="font-medium">{STATUS_LABELS[history.toStatus]}</span>
                                  </>
                                ) : (
                                  <span className="font-medium">Tạo booking: {STATUS_LABELS[history.toStatus]}</span>
                                )}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {formatDateTime(history.createdAt)}
                                {history.actorType && ` | ${history.actorType}`}
                              </p>
                              {history.reason && (
                                <p className="text-sm mt-1 bg-muted/50 rounded px-2 py-1">
                                  {history.reason}
                                </p>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </TabsContent>
              </Tabs>
            </>
          ) : null}
        </SheetContent>
      </Sheet>

      {/* Room Assignment Modal */}
      {selectedBooking && assigningRoomId && (
        <RoomAssignmentModal
          isOpen={isAssignModalOpen}
          onClose={() => setIsAssignModalOpen(false)}
          onSuccess={handleAssignSuccess}
          bookingPublicId={selectedBooking.publicId}
          bookingRoomId={assigningRoomId}
          bookingRoomType={assigningRoomType}
        />
      )}

      {/* Cancel Modal */}
      <Sheet open={isCancelModalOpen} onOpenChange={setIsCancelModalOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Hủy booking</SheetTitle>
          </SheetHeader>
          <div className="space-y-4 mt-4">
            <div>
              <Label htmlFor="cancel-reason">Lý do hủy *</Label>
              <Input
                id="cancel-reason"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="Nhập lý do hủy booking"
                className="mt-1"
              />
            </div>
            <div className="flex gap-2 justify-end">
              <Button
                variant="outline"
                onClick={() => setIsCancelModalOpen(false)}
              >
                Hủy bỏ
              </Button>
              <Button
                variant="destructive"
                onClick={handleCancel}
                disabled={!cancelReason.trim() || isActionLoading}
              >
                {isActionLoading ? (
                  <Loader2 className="w-4 h-4 mr-1 animate-spin" />
                ) : null}
                Xác nhận hủy
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>
      </>
      )}
    </div>
  );
}
