import { apiClient } from "@/lib/api/client"

export interface AvatarUploadUrlResponse {
  uploadId: string
  uploadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}

export interface AvatarResponse {
  userPublicId: string
  avatarUrl: string
  avatarUrlExpiresAt: string
}

async function uploadToPresignedUrl(file: File, upload: AvatarUploadUrlResponse) {
  const response = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.requiredHeaders,
    body: file,
  })
  if (!response.ok) {
    throw new Error("Không thể tải ảnh avatar lên MinIO")
  }
}

async function uploadAvatar(
  uploadUrlEndpoint: string,
  confirmEndpoint: string,
  file: File,
): Promise<AvatarResponse> {
  const upload = await apiClient.post<AvatarUploadUrlResponse>(uploadUrlEndpoint, {
    fileName: file.name,
    contentType: file.type,
    fileSize: file.size,
  })
  await uploadToPresignedUrl(file, upload)
  return apiClient.post<AvatarResponse>(confirmEndpoint, { uploadId: upload.uploadId })
}

export function uploadCustomerAvatar(publicId: string, file: File): Promise<AvatarResponse> {
  const encodedId = encodeURIComponent(publicId)
  return uploadAvatar(
    `/api/users/${encodedId}/avatar/upload-url`,
    `/api/users/${encodedId}/avatar/confirm`,
    file,
  )
}

export function uploadOwnStaffAvatar(file: File): Promise<AvatarResponse> {
  return uploadAvatar(
    "/api/staff-profiles/me/avatar/upload-url",
    "/api/staff-profiles/me/avatar/confirm",
    file,
  )
}
