import { apiClient } from "@/lib/api/client"
import type {
  HousekeepingStatus,
  Room,
  RoomCreateRequest,
  RoomOccupancy,
  RoomUpdateRequest,
} from "@/types/room"

function roomPath(roomNumber: string): string {
  return `/api/rooms/${encodeURIComponent(roomNumber)}`
}

export function getRooms(): Promise<Room[]> {
  return apiClient.get<Room[]>("/api/rooms")
}

export function createRoom(request: RoomCreateRequest): Promise<Room> {
  return apiClient.post<Room>("/api/rooms", request)
}

export function updateRoom(
  roomNumber: string,
  request: RoomUpdateRequest
): Promise<Room> {
  return apiClient.put<Room>(roomPath(roomNumber), request)
}

export function updateHousekeepingStatus(
  roomNumber: string,
  status: HousekeepingStatus
): Promise<Room> {
  return apiClient.patch<Room>(`${roomPath(roomNumber)}/housekeeping-status`, { status })
}

export function getRoomOccupancy(date?: string): Promise<RoomOccupancy[]> {
  const query = date ? `?date=${encodeURIComponent(date)}` : ""
  return apiClient.get<RoomOccupancy[]>(`/api/rooms/occupancy${query}`)
}
