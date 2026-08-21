import { apiClient } from "@/lib/api/client"
import type {
  Amenity,
  ImageUploadUrlResponse,
  RoomType,
  RoomTypeBed,
  RoomTypeCreateRequest,
  RoomTypeImage,
  RoomTypeStats,
  RoomTypeUpdateRequest,
} from "@/types/room-type"

function roomTypePath(code: string): string {
  return `/api/room-types/${encodeURIComponent(code)}`
}

export function getRoomTypes(): Promise<RoomType[]> {
  return apiClient.get<RoomType[]>("/api/room-types")
}

export function getRoomTypeStats(): Promise<RoomTypeStats> {
  return apiClient.get<RoomTypeStats>("/api/room-types/stats")
}

export function getAmenities(): Promise<Amenity[]> {
  return apiClient.get<Amenity[]>("/api/amenities")
}

export function createRoomType(request: RoomTypeCreateRequest): Promise<RoomType> {
  return apiClient.post<RoomType>("/api/room-types", request)
}

export function updateRoomType(
  code: string,
  request: RoomTypeUpdateRequest
): Promise<RoomType> {
  return apiClient.put<RoomType>(roomTypePath(code), request)
}

export function replaceRoomTypeBeds(
  code: string,
  beds: RoomTypeBed[]
): Promise<RoomType> {
  return apiClient.put<RoomType>(`${roomTypePath(code)}/beds`, { beds })
}

export function replaceRoomTypeAmenities(
  code: string,
  amenityCodes: string[]
): Promise<RoomType> {
  return apiClient.put<RoomType>(`${roomTypePath(code)}/amenities`, { amenityCodes })
}

export function deleteRoomType(code: string): Promise<void> {
  return apiClient.delete<void>(roomTypePath(code))
}

export function createRoomTypeImageUploadUrl(
  code: string,
  file: File
): Promise<ImageUploadUrlResponse> {
  return apiClient.post<ImageUploadUrlResponse>(
    `${roomTypePath(code)}/images/upload-url`,
    {
      fileName: file.name,
      contentType: file.type,
      fileSize: file.size,
    }
  )
}

export async function uploadRoomTypeImageObject(
  upload: ImageUploadUrlResponse,
  file: File
): Promise<void> {
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.requiredHeaders,
    body: file,
  })
  if (!response.ok) {
    throw new Error(`MinIO upload failed with status ${response.status}`)
  }
}

export function confirmRoomTypeImageUpload(
  code: string,
  uploadId: string,
  altText: string
): Promise<RoomTypeImage> {
  return apiClient.post<RoomTypeImage>(`${roomTypePath(code)}/images/confirm`, {
    uploadId,
    altText,
  })
}
