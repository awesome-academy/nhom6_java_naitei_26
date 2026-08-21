import { apiClient } from "@/lib/api/client"
import type {
  RoomStatusBlock,
  RoomStatusBlockCreateRequest,
} from "@/types/room-status-block"

export function getRoomStatusBlocks(
  startDate: string,
  endDate: string
): Promise<RoomStatusBlock[]> {
  const query = new URLSearchParams({ startDate, endDate })
  return apiClient.get<RoomStatusBlock[]>(`/api/room-status-blocks?${query.toString()}`)
}

export function createRoomStatusBlock(
  request: RoomStatusBlockCreateRequest
): Promise<RoomStatusBlock> {
  return apiClient.post<RoomStatusBlock>("/api/room-status-blocks", request)
}
