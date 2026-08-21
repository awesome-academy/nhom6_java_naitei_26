import type { RoomBlockType } from "@/types/room-status-block"

export const blockTypeLabels: Record<RoomBlockType, string> = {
  MAINTENANCE: "Bảo trì",
  RENOVATION: "Cải tạo",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  INTERNAL_USE: "Sử dụng nội bộ",
  DEEP_CLEANING: "Vệ sinh chuyên sâu",
}

export const blockTypeStyles: Record<RoomBlockType, string> = {
  MAINTENANCE: "border-amber-300 bg-amber-100 text-amber-950",
  RENOVATION: "border-purple-300 bg-purple-100 text-purple-950",
  OUT_OF_SERVICE: "border-red-300 bg-red-100 text-red-950",
  INTERNAL_USE: "border-blue-300 bg-blue-100 text-blue-950",
  DEEP_CLEANING: "border-teal-300 bg-teal-100 text-teal-950",
}

export const blockTypeDotStyles: Record<RoomBlockType, string> = {
  MAINTENANCE: "bg-amber-500",
  RENOVATION: "bg-purple-500",
  OUT_OF_SERVICE: "bg-red-500",
  INTERNAL_USE: "bg-blue-500",
  DEEP_CLEANING: "bg-teal-500",
}
