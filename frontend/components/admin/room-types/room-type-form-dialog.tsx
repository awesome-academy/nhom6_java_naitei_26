"use client"

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useFieldArray, useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { ImagePlus, Loader2, Plus, Trash2, Upload } from "lucide-react"
import { z } from "zod"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import {
  confirmRoomTypeImageUpload,
  createRoomType,
  createRoomTypeImageUploadUrl,
  replaceRoomTypeAmenities,
  replaceRoomTypeBeds,
  updateRoomType,
  uploadRoomTypeImageObject,
} from "@/lib/api/room-types"
import type {
  Amenity,
  AmenityCategory,
  BedType,
  RoomType,
  RoomTypeCreateRequest,
  RoomTypeImage,
} from "@/types/room-type"
import type { CancellationPolicy } from "@/types/cancellation-policy"

const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const MAX_IMAGES = 20
const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"]

const bedTypeLabels: Record<BedType, string> = {
  SINGLE: "Giường đơn",
  DOUBLE: "Giường đôi",
  QUEEN: "Queen",
  KING: "King",
  SOFA_BED: "Sofa bed",
  BUNK: "Giường tầng",
}

const categoryLabels: Record<AmenityCategory, string> = {
  ROOM: "Phòng",
  BATHROOM: "Phòng tắm",
  TECH: "Công nghệ",
  SERVICE: "Dịch vụ",
}

const nullableNonNegativeNumber = z
  .number({ error: "Giá trị phải là số" })
  .min(0, "Giá trị không được âm")
  .nullable()

const roomTypeSchema = z
  .object({
    code: z
      .string()
      .trim()
      .min(1, "Mã loại phòng là bắt buộc")
      .max(30, "Mã tối đa 30 ký tự")
      .regex(/^[A-Za-z0-9_]+$/, "Chỉ dùng chữ, số và dấu gạch dưới"),
    name: z.string().trim().min(1, "Tên loại phòng là bắt buộc").max(120),
    description: z.string().trim().max(10000).nullable(),
    maxOccupancy: z.number().int().min(1).max(100),
    maxAdults: z.number().int().min(1).max(100),
    maxChildren: z.number().int().min(0).max(100),
    basePrice: z.number().min(0, "Giá không được âm"),
    currency: z.string().trim().regex(/^[A-Za-z]{3}$/, "Currency phải có 3 chữ cái"),
    extraBedPrice: nullableNonNegativeNumber,
    sizeSqm: z.number().positive("Diện tích phải lớn hơn 0").nullable(),
    isActive: z.boolean(),
    sortOrder: z.number().int().min(0),
    onlineCancellationPolicyCodes: z.array(z.string().trim().max(30)).max(20),
    beds: z
      .array(
        z.object({
          bedType: z.enum(["SINGLE", "DOUBLE", "QUEEN", "KING", "SOFA_BED", "BUNK"]),
          quantity: z.number().int().min(1).max(10),
        })
      )
      .min(1, "Cần ít nhất một cấu hình giường")
      .max(6),
    amenityCodes: z.array(z.string()).max(100),
  })
  .superRefine((value, context) => {
    if (value.maxAdults > value.maxOccupancy) {
      context.addIssue({
        code: "custom",
        path: ["maxAdults"],
        message: "Số người lớn không được vượt quá sức chứa",
      })
    }
    if (value.maxChildren > value.maxOccupancy) {
      context.addIssue({
        code: "custom",
        path: ["maxChildren"],
        message: "Số trẻ em không được vượt quá sức chứa",
      })
    }
    if (value.isActive && value.onlineCancellationPolicyCodes.length === 0) {
      context.addIssue({
        code: "custom",
        path: ["onlineCancellationPolicyCodes"],
        message: "Loại phòng hoạt động cần ít nhất một option bán",
      })
    }
    const bedTypes = value.beds.map((bed) => bed.bedType)
    if (new Set(bedTypes).size !== bedTypes.length) {
      context.addIssue({
        code: "custom",
        path: ["beds"],
        message: "Mỗi loại giường chỉ được chọn một lần",
      })
    }
    if (value.beds.reduce((total, bed) => total + bed.quantity, 0) > 10) {
      context.addIssue({
        code: "custom",
        path: ["beds"],
        message: "Tổng số giường không được vượt quá 10",
      })
    }
  })

type RoomTypeFormValues = z.infer<typeof roomTypeSchema>

interface PendingImage {
  id: string
  file: File
  previewUrl: string
  uploadError?: string
}

interface RoomTypeFormDialogProps {
  open: boolean
  roomType: RoomType | null
  amenities: Amenity[]
  cancellationPolicies: CancellationPolicy[]
  policyLoadError: string | null
  canUploadImages: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}

function defaultValues(roomType: RoomType | null): RoomTypeFormValues {
  return {
    code: roomType?.code ?? "",
    name: roomType?.name ?? "",
    description: roomType?.description ?? null,
    maxOccupancy: roomType?.maxOccupancy ?? 2,
    maxAdults: roomType?.maxAdults ?? 2,
    maxChildren: roomType?.maxChildren ?? 0,
    basePrice: Number(roomType?.basePrice ?? 0),
    currency: roomType?.currency ?? "VND",
    extraBedPrice: roomType?.extraBedPrice == null ? null : Number(roomType.extraBedPrice),
    sizeSqm: roomType?.sizeSqm == null ? null : Number(roomType.sizeSqm),
    isActive: roomType?.isActive ?? true,
    sortOrder: roomType?.sortOrder ?? 0,
    onlineCancellationPolicyCodes:
      roomType?.onlineCancellationPolicyOptions.map((option) => option.cancellationPolicy.code) ?? ["NON_REFUND"],
    beds: roomType?.beds.length
      ? roomType.beds.map((bed) => ({ ...bed }))
      : [{ bedType: "QUEEN", quantity: 1 }],
    amenityCodes: roomType?.amenities.map((amenity) => amenity.code) ?? [],
  }
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Không thể lưu loại phòng. Vui lòng thử lại."
}

export function RoomTypeFormDialog({
  open,
  roomType,
  amenities,
  cancellationPolicies,
  policyLoadError,
  canUploadImages,
  onOpenChange,
  onSaved,
}: RoomTypeFormDialogProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const pendingImagesRef = useRef<PendingImage[]>([])
  const [pendingImages, setPendingImages] = useState<PendingImage[]>([])
  const [existingImages, setExistingImages] = useState<RoomTypeImage[]>([])
  const [persistedCode, setPersistedCode] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const form = useForm<RoomTypeFormValues>({
    resolver: zodResolver(roomTypeSchema),
    defaultValues: defaultValues(roomType),
  })
  const beds = useFieldArray({ control: form.control, name: "beds" })
  const watchedBeds = useWatch({ control: form.control, name: "beds" })
  const selectedAmenities = useWatch({ control: form.control, name: "amenityCodes" })
  const selectedPolicyCodes = useWatch({ control: form.control, name: "onlineCancellationPolicyCodes" }) ?? []
  const selectedName = useWatch({ control: form.control, name: "name" })
  const isActive = useWatch({ control: form.control, name: "isActive" })

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      form.reset(defaultValues(roomType))
      setExistingImages(roomType?.images ?? [])
      setPersistedCode(roomType?.code ?? null)
      setPendingImages((current) => {
        current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
        return []
      })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [form, open, roomType])

  useEffect(() => {
    pendingImagesRef.current = pendingImages
  }, [pendingImages])

  useEffect(() => {
    return () => pendingImagesRef.current.forEach(
      (image) => URL.revokeObjectURL(image.previewUrl)
    )
  }, [])

  const amenitiesByCategory = useMemo(() => {
    return amenities.reduce<Record<AmenityCategory, Amenity[]>>(
      (groups, amenity) => {
        groups[amenity.category].push(amenity)
        return groups
      },
      { ROOM: [], BATHROOM: [], TECH: [], SERVICE: [] }
    )
  }, [amenities])

  const isEditMode = roomType !== null || persistedCode !== null

  function addImages(files: FileList | File[]) {
    const candidates = Array.from(files)
    const remainingSlots = MAX_IMAGES - existingImages.length - pendingImages.length
    if (candidates.length > remainingSlots) {
      toast.error(`Mỗi loại phòng có tối đa ${MAX_IMAGES} ảnh`)
      return
    }
    const invalid = candidates.find(
      (file) => !ACCEPTED_IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_SIZE
    )
    if (invalid) {
      toast.error("Chỉ nhận JPEG, PNG, WebP và tối đa 10 MB mỗi ảnh")
      return
    }
    setPendingImages((current) => [
      ...current,
      ...candidates.map((file) => ({
        id: `${file.name}-${file.size}-${file.lastModified}-${crypto.randomUUID()}`,
        file,
        previewUrl: URL.createObjectURL(file),
      })),
    ])
  }

  function removePendingImage(id: string) {
    setPendingImages((current) => {
      const removed = current.find((image) => image.id === id)
      if (removed) URL.revokeObjectURL(removed.previewUrl)
      return current.filter((image) => image.id !== id)
    })
  }

  async function uploadPendingImages(code: string): Promise<PendingImage[]> {
    const failed: PendingImage[] = []
    for (const pendingImage of pendingImages) {
      try {
        const upload = await createRoomTypeImageUploadUrl(code, pendingImage.file)
        await uploadRoomTypeImageObject(upload, pendingImage.file)
        const confirmed = await confirmRoomTypeImageUpload(
          code,
          upload.uploadId,
          selectedName.trim() || code
        )
        setExistingImages((current) => [...current, confirmed])
        URL.revokeObjectURL(pendingImage.previewUrl)
      } catch (error) {
        failed.push({ ...pendingImage, uploadError: getErrorMessage(error) })
      }
    }
    return failed
  }

  async function submit(values: RoomTypeFormValues) {
    setIsSubmitting(true)
    const normalizedCode = (persistedCode ?? values.code).trim().toUpperCase()
    const commonRequest = {
      name: values.name.trim(),
      description: values.description?.trim() || null,
      maxOccupancy: values.maxOccupancy,
      maxAdults: values.maxAdults,
      maxChildren: values.maxChildren,
      basePrice: values.basePrice,
      currency: values.currency.trim().toUpperCase(),
      extraBedPrice: values.extraBedPrice,
      sizeSqm: values.sizeSqm,
      isActive: values.isActive,
      sortOrder: values.sortOrder,
      onlineCancellationPolicyCodes: values.onlineCancellationPolicyCodes,
    }

    try {
      if (isEditMode) {
        await updateRoomType(normalizedCode, commonRequest)
        await replaceRoomTypeBeds(normalizedCode, values.beds)
        await replaceRoomTypeAmenities(normalizedCode, values.amenityCodes)
      } else {
        const createRequest: RoomTypeCreateRequest = {
          code: normalizedCode,
          ...commonRequest,
          beds: values.beds,
          amenityCodes: values.amenityCodes,
        }
        const created = await createRoomType(createRequest)
        setPersistedCode(created.code)
      }
    } catch (error) {
      await onSaved()
      toast.error(getErrorMessage(error), {
        description: isEditMode
          ? "Một phần dữ liệu có thể đã được cập nhật; danh sách đã được tải lại."
          : undefined,
      })
      setIsSubmitting(false)
      return
    }

    const failedImages = canUploadImages
      ? await uploadPendingImages(normalizedCode)
      : pendingImages
    setPendingImages(failedImages)
    await onSaved()
    setIsSubmitting(false)

    if (failedImages.length > 0) {
      toast.warning("Đã lưu loại phòng nhưng có ảnh tải lên thất bại", {
        description: `${failedImages.length} ảnh đang được giữ lại để bạn thử lại.`,
      })
      return
    }

    toast.success(isEditMode ? "Đã cập nhật loại phòng" : "Đã tạo loại phòng")
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] max-w-5xl flex-col gap-0 overflow-hidden p-0">
        <DialogHeader className="shrink-0 border-b px-6 py-5">
          <DialogTitle>{isEditMode ? "Chỉnh sửa loại phòng" : "Thêm loại phòng"}</DialogTitle>
          <DialogDescription>
            Cấu hình thông tin bán phòng, giường, tiện nghi và ảnh đại diện.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(submit)} className="flex min-h-0 flex-1 flex-col overflow-hidden">
          <div className="min-h-0 flex-1 space-y-8 overflow-y-auto px-6 py-5">
            <section className="space-y-4">
              <h3 className="text-base font-semibold">Thông tin cơ bản</h3>
              <div className="grid gap-4 md:grid-cols-2">
                <Field label="Mã loại phòng" error={form.formState.errors.code?.message}>
                  <Input
                    disabled={isEditMode}
                    placeholder="DELUXE_SEA"
                    {...form.register("code", {
                      onChange: (event) => {
                        event.target.value = event.target.value.toUpperCase()
                      },
                    })}
                  />
                </Field>
                <Field label="Tên loại phòng" error={form.formState.errors.name?.message}>
                  <Input placeholder="Deluxe hướng biển" {...form.register("name")} />
                </Field>
              </div>
              <Field label="Mô tả" error={form.formState.errors.description?.message}>
                <Textarea
                  rows={4}
                  placeholder="Mô tả nổi bật của loại phòng..."
                  {...form.register("description")}
                />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <NumberField form={form} name="basePrice" label="Giá cơ bản" step="0.01" />
                <Field label="Tiền tệ" error={form.formState.errors.currency?.message}>
                  <Input maxLength={3} {...form.register("currency")} />
                </Field>
                <NullableNumberField form={form} name="extraBedPrice" label="Giá giường phụ" step="0.01" />
                <NullableNumberField form={form} name="sizeSqm" label="Diện tích (m²)" step="0.01" />
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <NumberField form={form} name="maxOccupancy" label="Sức chứa" />
                <NumberField form={form} name="maxAdults" label="Người lớn tối đa" />
                <NumberField form={form} name="maxChildren" label="Trẻ em tối đa" />
                <NumberField form={form} name="sortOrder" label="Thứ tự hiển thị" />
              </div>
              <div className="flex items-center gap-3 rounded-lg border p-4">
                <Switch
                  checked={isActive}
                  onCheckedChange={(checked) => form.setValue("isActive", checked)}
                />
                <div>
                  <Label>Đang hoạt động</Label>
                  <p className="text-xs text-[var(--muted-foreground)]">
                    Loại phòng active có thể được gán cho phòng mới.
                  </p>
                </div>
              </div>
              <Field
                label="Option bán"
                error={form.formState.errors.onlineCancellationPolicyCodes?.message}
              >
                <div className="space-y-4 rounded-lg border p-4">
                  <div className="grid gap-3 md:grid-cols-2">
                    {cancellationPolicies.map((policy) => {
                      const checked = selectedPolicyCodes.includes(policy.code)
                      return (
                        <label
                          key={policy.code}
                          className="flex cursor-pointer items-start gap-3 rounded-md border p-3"
                        >
                          <Checkbox
                            checked={checked}
                            onCheckedChange={(nextChecked) => {
                              const nextCodes = nextChecked
                                ? [...selectedPolicyCodes, policy.code]
                                : selectedPolicyCodes.filter((code) => code !== policy.code)
                              form.setValue("onlineCancellationPolicyCodes", nextCodes, {
                                shouldDirty: true,
                                shouldValidate: true,
                              })
                            }}
                          />
                          <span className="min-w-0">
                            <span className="block text-sm font-medium">{policy.name}</span>
                            <span className="block text-xs text-[var(--muted-foreground)]">
                              Online · +{policy.priceAdjustmentPercent}%
                            </span>
                          </span>
                        </label>
                      )
                    })}
                    {cancellationPolicies.length === 0 && (
                      <p className="text-sm text-[var(--muted-foreground)]">
                        Chưa có policy active để chọn.
                      </p>
                    )}
                  </div>
                </div>
                {policyLoadError && (
                  <p className="text-xs text-[var(--destructive)]">
                    Không tải được danh sách chính sách hủy: {policyLoadError}
                  </p>
                )}
              </Field>
            </section>

            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-base font-semibold">Cấu hình giường</h3>
                  <p className="text-sm text-[var(--muted-foreground)]">Tối đa 6 loại và tổng cộng 10 giường.</p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={beds.fields.length >= 6}
                  onClick={() => beds.append({ bedType: "SINGLE", quantity: 1 })}
                >
                  <Plus className="mr-2 h-4 w-4" /> Thêm giường
                </Button>
              </div>
              <div className="space-y-3">
                {beds.fields.map((bed, index) => (
                  <div key={bed.id} className="grid gap-3 rounded-lg border p-3 sm:grid-cols-[1fr_140px_auto]">
                    <Select
                      value={watchedBeds[index]?.bedType}
                      onValueChange={(value: BedType) => form.setValue(`beds.${index}.bedType`, value)}
                    >
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {(Object.keys(bedTypeLabels) as BedType[]).map((type) => (
                          <SelectItem key={type} value={type}>{bedTypeLabels[type]}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Input
                      type="number"
                      min={1}
                      max={10}
                      {...form.register(`beds.${index}.quantity`, { valueAsNumber: true })}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={beds.fields.length === 1}
                      onClick={() => beds.remove(index)}
                      aria-label="Xóa cấu hình giường"
                    >
                      <Trash2 className="h-4 w-4 text-[var(--destructive)]" />
                    </Button>
                  </div>
                ))}
              </div>
              {form.formState.errors.beds?.message && (
                <p className="text-sm text-[var(--destructive)]">{form.formState.errors.beds.message}</p>
              )}
            </section>

            <section className="space-y-4">
              <div>
                <h3 className="text-base font-semibold">Tiện nghi</h3>
                <p className="text-sm text-[var(--muted-foreground)]">Chọn các tiện nghi mặc định của loại phòng.</p>
              </div>
              <Popover>
                <PopoverTrigger asChild>
                  <Button type="button" variant="outline" className="w-full justify-between font-normal">
                    {selectedAmenities.length > 0
                      ? `${selectedAmenities.length} tiện nghi đã chọn`
                      : "Chọn tiện nghi"}
                    <Plus className="h-4 w-4" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent align="start" className="max-h-80 w-[min(560px,calc(100vw-3rem))] overflow-y-auto">
                  <div className="space-y-4">
                    {(Object.keys(categoryLabels) as AmenityCategory[]).map((category) => (
                      amenitiesByCategory[category].length > 0 && (
                        <div key={category} className="space-y-2">
                          <p className="text-xs font-semibold uppercase text-[var(--muted-foreground)]">
                            {categoryLabels[category]}
                          </p>
                          <div className="grid gap-2 sm:grid-cols-2">
                            {amenitiesByCategory[category].map((amenity) => {
                              const checked = selectedAmenities.includes(amenity.code)
                              return (
                                <label key={amenity.code} className="flex cursor-pointer items-center gap-2 rounded-md p-2 hover:bg-[var(--muted)]">
                                  <Checkbox
                                    checked={checked}
                                    onCheckedChange={(nextChecked) => {
                                      form.setValue(
                                        "amenityCodes",
                                        nextChecked
                                          ? [...selectedAmenities, amenity.code]
                                          : selectedAmenities.filter((code) => code !== amenity.code),
                                        { shouldDirty: true }
                                      )
                                    }}
                                  />
                                  <span className="text-sm">{amenity.name}</span>
                                </label>
                              )
                            })}
                          </div>
                        </div>
                      )
                    ))}
                  </div>
                </PopoverContent>
              </Popover>
              <div className="flex flex-wrap gap-2">
                {amenities
                  .filter((amenity) => selectedAmenities.includes(amenity.code))
                  .map((amenity) => <Badge key={amenity.code}>{amenity.name}</Badge>)}
              </div>
            </section>

            <section className="space-y-4">
              <div>
                <h3 className="text-base font-semibold">Ảnh loại phòng</h3>
                <p className="text-sm text-[var(--muted-foreground)]">JPEG, PNG hoặc WebP; tối đa 10 MB mỗi ảnh.</p>
              </div>
              {canUploadImages ? (
                <button
                  type="button"
                  className="flex w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed p-8 text-[var(--muted-foreground)] transition-colors hover:border-[var(--accent)] hover:bg-blue-50/50"
                  onClick={() => fileInputRef.current?.click()}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.preventDefault()
                    addImages(event.dataTransfer.files)
                  }}
                >
                  <Upload className="h-7 w-7" />
                  <span className="font-medium text-[var(--foreground)]">Chọn hoặc kéo ảnh vào đây</span>
                  <span className="text-xs">Còn {MAX_IMAGES - existingImages.length - pendingImages.length} vị trí</span>
                </button>
              ) : (
                <p className="rounded-lg border bg-[var(--muted)] p-4 text-sm text-[var(--muted-foreground)]">
                  Bạn không có quyền tải ảnh loại phòng.
                </p>
              )}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                className="hidden"
                onChange={(event) => {
                  if (event.target.files) addImages(event.target.files)
                  event.target.value = ""
                }}
              />
              {(existingImages.length > 0 || pendingImages.length > 0) && (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
                  {existingImages.map((image) => (
                    <div key={image.imageId} className="relative aspect-video overflow-hidden rounded-lg border bg-[var(--muted)]">
                      <div
                        role="img"
                        aria-label={image.altText}
                        className="h-full w-full bg-cover bg-center"
                        style={{ backgroundImage: `url("${image.downloadUrl}")` }}
                      />
                      {image.isPrimary && <Badge className="absolute left-2 top-2" variant="success">Ảnh chính</Badge>}
                    </div>
                  ))}
                  {pendingImages.map((image) => (
                    <div key={image.id} className="space-y-1">
                      <div className="relative aspect-video overflow-hidden rounded-lg border bg-[var(--muted)]">
                        <div
                          role="img"
                          aria-label={image.file.name}
                          className="h-full w-full bg-cover bg-center opacity-80"
                          style={{ backgroundImage: `url("${image.previewUrl}")` }}
                        />
                        <Badge className="absolute left-2 top-2" variant={image.uploadError ? "destructive" : "warning"}>
                          {image.uploadError ? "Tải lỗi" : "Chờ tải"}
                        </Badge>
                        <Button
                          type="button"
                          variant="destructive"
                          size="icon"
                          className="absolute right-2 top-2 h-7 w-7"
                          onClick={() => removePendingImage(image.id)}
                          aria-label={`Bỏ ảnh ${image.file.name}`}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                      {image.uploadError && (
                        <p className="truncate text-xs text-[var(--destructive)]" title={image.uploadError}>
                          {image.file.name}: {image.uploadError}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
              {existingImages.length === 0 && pendingImages.length === 0 && (
                <div className="flex items-center gap-3 rounded-lg bg-[var(--muted)] p-4 text-sm text-[var(--muted-foreground)]">
                  <ImagePlus className="h-5 w-5" /> Chưa có ảnh nào.
                </div>
              )}
            </section>
          </div>

          <DialogFooter className="relative z-10 shrink-0 border-t bg-[var(--card)] px-6 py-4">
            <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEditMode ? "Lưu thay đổi" : "Tạo loại phòng"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface FieldProps {
  label: string
  error?: string
  children: ReactNode
}

function Field({ label, error, children }: FieldProps) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-[var(--destructive)]">{error}</p>}
    </div>
  )
}

interface NumberFieldProps {
  form: ReturnType<typeof useForm<RoomTypeFormValues>>
  name:
    | "basePrice"
    | "maxOccupancy"
    | "maxAdults"
    | "maxChildren"
    | "sortOrder"
  label: string
  step?: string
}

function NumberField({ form, name, label, step = "1" }: NumberFieldProps) {
  return (
    <Field label={label} error={form.formState.errors[name]?.message}>
      <Input type="number" min={0} step={step} {...form.register(name, { valueAsNumber: true })} />
    </Field>
  )
}

interface NullableNumberFieldProps {
  form: ReturnType<typeof useForm<RoomTypeFormValues>>
  name: "extraBedPrice" | "sizeSqm"
  label: string
  step: string
}

function NullableNumberField({ form, name, label, step }: NullableNumberFieldProps) {
  return (
    <Field label={label} error={form.formState.errors[name]?.message}>
      <Input
        type="number"
        min={0}
        step={step}
        {...form.register(name, {
          setValueAs: (value) => value === "" ? null : Number(value),
        })}
      />
    </Field>
  )
}
