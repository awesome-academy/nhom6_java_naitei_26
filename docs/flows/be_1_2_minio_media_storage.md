# BE — MinIO media storage and avatar upload

## 1. Bucket scope

The local stack provisions only the buckets used by the active booking and account flows:

| Bucket | Content |
| --- | --- |
| `room-type-images` | Images shared by every physical room in a RoomType |
| `avatars` | Current Customer and Staff avatars |
| `invoices` | Invoice PDF objects |

Customers book by RoomType, so the stack does not provision `room-images` for physical-room photos.
The existing physical-room image endpoints remain legacy and are outside this flow.

## 2. Startup initialization

`docker compose up -d` starts MinIO and an idempotent `minio-init` job. The job waits until MinIO accepts credentials, then runs `mc mb --ignore-existing` for the three buckets above.

When Spring Boot is started directly from IntelliJ, `MinioBucketInitializer` performs the same check/create operation with retries. Existing buckets and objects are never deleted.

Set `MINIO_INITIALIZE_ON_STARTUP=false` in the test profile when MinIO is mocked.

## 3. RoomType image flow

The existing RoomType endpoints remain:

| Method | Endpoint | Permission |
| --- | --- | --- |
| `POST` | `/api/room-types/{code}/images/upload-url` | `room:update` |
| `POST` | `/api/room-types/{code}/images/confirm` | `room:update` |

The backend generates an object key `room-types/{roomTypeId}/{uploadId}.{extension}`, validates the file and stores metadata in `room_type_images`. The file bytes go directly from the browser to MinIO using the presigned URL. Responses contain a short-lived download URL, never a storage key.

## 4. Avatar flow

Admin uploads a selected User's avatar with:

| Method | Endpoint | Permission |
| --- | --- | --- |
| `POST` | `/api/users/{publicId}/avatar/upload-url` | `ADMIN` |
| `POST` | `/api/users/{publicId}/avatar/confirm` | `ADMIN` |

Staff changes only their own avatar with:

| Method | Endpoint | Permission |
| --- | --- | --- |
| `POST` | `/api/staff-profiles/me/avatar/upload-url` | `STAFF` |
| `POST` | `/api/staff-profiles/me/avatar/confirm` | `STAFF` |

Object keys are server-generated and isolated by internal user ID:

```text
avatars/users/{userId}/{uploadId}.{extension}
avatars/staff/{userId}/{uploadId}.{extension}
```

Only JPEG, PNG and WebP are accepted. The backend verifies the object metadata after the direct PUT, stores `avatar_storage_key` and `avatar_content_type`, then removes the previous storage object best-effort. API profile responses resolve MinIO keys to a short-lived presigned URL; legacy OAuth URLs remain supported through `avatar_url`.
