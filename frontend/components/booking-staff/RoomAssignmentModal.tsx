"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  getAvailableRoomsForAssignment,
  getAvailableFloors,
  assignRoom,
} from "@/lib/api/booking-staff-api";
import {
  AvailableRoom,
  HousekeepingStatusType,
  RoomView,
} from "@/types/booking-staff";
import { Loader2, Check, X } from "lucide-react";

interface RoomAssignmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  bookingPublicId: string;
  bookingRoomId: number;
  bookingRoomType: string;
}

const HK_STATUS_CONFIG: Record<
  HousekeepingStatusType,
  { label: string; bgColor: string; borderColor: string; textColor: string }
> = {
  CLEAN: {
    label: "Sạch",
    bgColor: "bg-green-50",
    borderColor: "border-green-500",
    textColor: "text-green-800",
  },
  DIRTY: {
    label: "Bẩn",
    bgColor: "bg-red-50",
    borderColor: "border-red-500",
    textColor: "text-red-800",
  },
  CLEANING: {
    label: "Đang dọn",
    bgColor: "bg-orange-50",
    borderColor: "border-orange-500",
    textColor: "text-orange-800",
  },
  INSPECTED: {
    label: "Đã kiểm tra",
    bgColor: "bg-blue-50",
    borderColor: "border-blue-500",
    textColor: "text-blue-800",
  },
};

const VIEW_LABELS: Record<RoomView, string> = {
  SEA: "Hướng biển",
  CITY: "Hướng thành phố",
  GARDEN: "Hướng vườn",
  POOL: "Hướng hồ bơi",
  MOUNTAIN: "Hướng núi",
  NONE: "Không có view",
};

export function RoomAssignmentModal({
  isOpen,
  onClose,
  onSuccess,
  bookingPublicId,
  bookingRoomId,
  bookingRoomType,
}: RoomAssignmentModalProps) {
  const [rooms, setRooms] = useState<AvailableRoom[]>([]);
  const [floors, setFloors] = useState<number[]>([]);
  const [selectedRoom, setSelectedRoom] = useState<AvailableRoom | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isAssigning, setIsAssigning] = useState(false);

  // Filters
  const [selectedFloor, setSelectedFloor] = useState<string>("all");
  const [selectedHKStatus, setSelectedHKStatus] = useState<string>("all");
  const [selectedView, setSelectedView] = useState<string>("all");

  const loadFloors = useCallback(async () => {
    try {
      const data = await getAvailableFloors(bookingPublicId, bookingRoomId);
      setFloors(data);
    } catch (error) {
      console.error("Failed to load floors:", error);
    }
  }, [bookingPublicId, bookingRoomId]);

  const loadRooms = useCallback(async () => {
    setIsLoading(true);
    try {
      const filters: {
        floor?: number;
        housekeepingStatus?: HousekeepingStatusType;
        viewType?: RoomView;
      } = {};

      if (selectedFloor !== "all") {
        filters.floor = parseInt(selectedFloor);
      }
      if (selectedHKStatus !== "all") {
        filters.housekeepingStatus = selectedHKStatus as HousekeepingStatusType;
      }
      if (selectedView !== "all") {
        filters.viewType = selectedView as RoomView;
      }

      const data = await getAvailableRoomsForAssignment(
        bookingPublicId,
        bookingRoomId,
        filters
      );
      setRooms(data);
    } catch (error) {
      console.error("Failed to load rooms:", error);
    } finally {
      setIsLoading(false);
    }
  }, [bookingPublicId, bookingRoomId, selectedFloor, selectedHKStatus, selectedView]);

  // Load available floors on mount
  useEffect(() => {
    if (isOpen) {
      const timer = window.setTimeout(() => {
        void loadFloors();
      }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [isOpen, loadFloors]);

  // Load rooms when filters change
  useEffect(() => {
    if (isOpen) {
      const timer = window.setTimeout(() => {
        void loadRooms();
      }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [isOpen, loadRooms]);

  const handleAssign = async () => {
    if (!selectedRoom) return;

    setIsAssigning(true);
    try {
      await assignRoom(bookingPublicId, bookingRoomId, {
        roomId: selectedRoom.id,
      });
      onSuccess();
      onClose();
    } catch (error) {
      console.error("Failed to assign room:", error);
      alert("Không thể gán phòng. Vui lòng thử lại.");
    } finally {
      setIsAssigning(false);
    }
  };

  // Group rooms by floor
  const roomsByFloor = rooms.reduce((acc, room) => {
    const floor = room.floor ?? 0;
    if (!acc[floor]) acc[floor] = [];
    acc[floor].push(room);
    return acc;
  }, {} as Record<number, AvailableRoom[]>);

  // Sort floors
  const sortedFloors = Object.keys(roomsByFloor)
    .map(Number)
    .sort((a, b) => b - a);

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>
            Gán phòng - {bookingRoomType}
          </DialogTitle>
        </DialogHeader>

        {/* Filters */}
        <div className="flex flex-wrap gap-4 pb-4 border-b">
          <div className="space-y-1">
            <Label className="text-xs">Tầng</Label>
            <Select value={selectedFloor} onValueChange={setSelectedFloor}>
              <SelectTrigger className="w-[120px]">
                <SelectValue placeholder="Tất cả" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tất cả</SelectItem>
                {floors.map((floor) => (
                  <SelectItem key={floor} value={String(floor)}>
                    Tầng {floor}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1">
            <Label className="text-xs">Trạng thái dọn phòng</Label>
            <Select
              value={selectedHKStatus}
              onValueChange={setSelectedHKStatus}
            >
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder="Tất cả" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tất cả</SelectItem>
                <SelectItem value="CLEAN">Sạch</SelectItem>
                <SelectItem value="DIRTY">Bẩn</SelectItem>
                <SelectItem value="CLEANING">Đang dọn</SelectItem>
                <SelectItem value="INSPECTED">Đã kiểm tra</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1">
            <Label className="text-xs">View</Label>
            <Select value={selectedView} onValueChange={setSelectedView}>
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder="Tất cả" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tất cả</SelectItem>
                <SelectItem value="SEA">Hướng biển</SelectItem>
                <SelectItem value="CITY">Hướng thành phố</SelectItem>
                <SelectItem value="GARDEN">Hướng vườn</SelectItem>
                <SelectItem value="POOL">Hướng hồ bơi</SelectItem>
                <SelectItem value="MOUNTAIN">Hướng núi</SelectItem>
                <SelectItem value="NONE">Không có view</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Legend */}
          <div className="flex items-center gap-3 ml-auto">
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded border-2 border-green-500 bg-green-50" />
              <span className="text-xs text-muted-foreground">Sạch</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded border-2 border-red-500 bg-red-50" />
              <span className="text-xs text-muted-foreground">Bẩn</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded border-2 border-orange-500 bg-orange-50" />
              <span className="text-xs text-muted-foreground">Đang dọn</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded border-2 border-blue-500 bg-blue-50" />
              <span className="text-xs text-muted-foreground">Đã kiểm tra</span>
            </div>
          </div>
        </div>

        {/* Room Grid */}
        <div className="flex-1 overflow-y-auto py-4">
          {isLoading ? (
            <div className="flex items-center justify-center h-64">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : rooms.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
              <p>Không có phòng trống phù hợp</p>
            </div>
          ) : (
            <div className="space-y-6">
              {sortedFloors.map((floor) => (
                <div key={floor}>
                  <h3 className="text-sm font-medium text-muted-foreground mb-2">
                    Tầng {floor}
                  </h3>
                  <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-2">
                    {roomsByFloor[floor]
                      .sort((a, b) =>
                        a.roomNumber.localeCompare(b.roomNumber, undefined, { numeric: true })
                      )
                      .map((room) => {
                        const config = HK_STATUS_CONFIG[room.housekeepingStatus];
                        const isSelected = selectedRoom?.id === room.id;

                        return (
                          <button
                            key={room.id}
                            onClick={() => setSelectedRoom(room)}
                            className={`
                              relative flex flex-col items-center justify-center
                              p-2 rounded-lg border-2 transition-all
                              ${config.bgColor} ${config.borderColor}
                              ${isSelected ? "ring-2 ring-primary ring-offset-2" : ""}
                              hover:scale-105 cursor-pointer
                            `}
                          >
                            {isSelected && (
                              <div className="absolute -top-2 -right-2 w-5 h-5 bg-primary rounded-full flex items-center justify-center">
                                <Check className="w-3 h-3 text-primary-foreground" />
                              </div>
                            )}
                            <span className={`text-lg font-bold ${config.textColor}`}>
                              {room.roomNumber}
                            </span>
                            <span className="text-xs text-muted-foreground truncate max-w-full">
                              {room.viewType !== "NONE" ? VIEW_LABELS[room.viewType] : ""}
                            </span>
                          </button>
                        );
                      })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between pt-4 border-t">
          {selectedRoom ? (
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-sm">
                Đã chọn: <strong>{selectedRoom.roomNumber}</strong>
              </Badge>
              <span className="text-sm text-muted-foreground">
                ({selectedRoom.roomTypeName} - {VIEW_LABELS[selectedRoom.viewType]})
              </span>
            </div>
          ) : (
            <span className="text-sm text-muted-foreground">
              Chọn một phòng để gán
            </span>
          )}

          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose} disabled={isAssigning}>
              <X className="w-4 h-4 mr-1" />
              Hủy
            </Button>
            <Button onClick={handleAssign} disabled={!selectedRoom || isAssigning}>
              {isAssigning ? (
                <Loader2 className="w-4 h-4 mr-1 animate-spin" />
              ) : (
                <Check className="w-4 h-4 mr-1" />
              )}
              Gán phòng
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
