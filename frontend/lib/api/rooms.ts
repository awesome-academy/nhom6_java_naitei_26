import { apiClient } from "@/lib/api/client"
import type { Room, RoomCreateRequest, RoomUpdateRequest } from "@/types/room"

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
