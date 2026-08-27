"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { LoaderCircle, Save } from "lucide-react"
import { toast } from "sonner"

import {
  Avatar,
  AvatarFallback,
  AvatarImage,
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Skeleton,
} from "@/components/ui"
import { isAdminUser, isStaffUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"
import { uploadOwnStaffAvatar } from "@/lib/api/avatar"
import { getOwnStaffProfile, updateOwnStaffProfile } from "@/lib/api/staff"
import type { EmploymentStatus, StaffOwnProfile } from "@/types/staff"

const phoneSchema = z.object({
  phone: z.string().max(20, "Số điện thoại không được quá 20 ký tự").regex(
    /^[0-9+() .-]*$/,
    "Số điện thoại chứa ký tự không hợp lệ",
  ),
})

type PhoneFormData = z.infer<typeof phoneSchema>

const employmentStatusLabels: Record<EmploymentStatus, string> = {
  ACTIVE: "Đang làm việc",
  ON_LEAVE: "Đang nghỉ phép",
  TERMINATED: "Đã nghỉ việc",
}

function getInitials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "long" }).format(new Date(`${value}T00:00:00`))
}

function ReadOnlyItem({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-border bg-muted/30 p-4">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="text-sm font-medium text-foreground">{value || "Chưa cập nhật"}</p>
    </div>
  )
}

function StaffProfileSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="h-5 w-80" />
      </div>
      <Skeleton className="h-44 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}

export function ManagerProfilePage() {
  const { user } = useAuth()
  const isAdmin = isAdminUser(user)
  const isStaff = isStaffUser(user) && !isAdmin
  const [profile, setProfile] = useState<StaffOwnProfile | null>(null)
  const [isFetching, setIsFetching] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false)
  const avatarInputRef = useRef<HTMLInputElement>(null)
  const form = useForm<PhoneFormData>({
    resolver: zodResolver(phoneSchema),
    defaultValues: { phone: "" },
  })

  const loadProfile = useCallback(async () => {
    if (!isStaff) return

    setIsFetching(true)
    setLoadError(false)
    try {
      const response = await getOwnStaffProfile()
      setProfile(response)
      form.reset({ phone: response.phone ?? "" })
    } catch (error) {
      console.error("Failed to load own staff profile", error)
      setLoadError(true)
      toast.error("Không thể tải hồ sơ nhân viên")
    } finally {
      setIsFetching(false)
    }
  }, [form, isStaff])

  useEffect(() => {
    if (!isStaff) return

    const timer = window.setTimeout(() => {
      void loadProfile()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [isStaff, loadProfile])

  async function onSubmit(values: PhoneFormData) {
    setIsSaving(true)
    try {
      const response = await updateOwnStaffProfile({ phone: values.phone })
      setProfile(response)
      form.reset({ phone: response.phone ?? "" })
      toast.success("Cập nhật số điện thoại thành công")
    } catch (error) {
      console.error("Failed to update own staff phone", error)
      toast.error("Không thể cập nhật số điện thoại")
    } finally {
      setIsSaving(false)
    }
  }

  async function onAvatarSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ""
    if (!file) return
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Avatar chỉ hỗ trợ JPG, PNG hoặc WebP")
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error("Avatar không được vượt quá 10 MB")
      return
    }
    setIsUploadingAvatar(true)
    try {
      const response = await uploadOwnStaffAvatar(file)
      setProfile((current) => current ? { ...current, avatarUrl: response.avatarUrl } : current)
      toast.success("Đã cập nhật ảnh đại diện")
    } catch (error) {
      console.error("Failed to upload staff avatar", error)
      toast.error("Không thể cập nhật ảnh đại diện")
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  if (isStaff && isFetching && !profile) return <StaffProfileSkeleton />

  if (isStaff && loadError && !profile) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Không thể tải hồ sơ</CardTitle>
          <CardDescription>Vui lòng thử lại sau.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" onClick={() => void loadProfile()}>Thử lại</Button>
        </CardContent>
      </Card>
    )
  }

  const fullName = profile?.fullName ?? user?.fullName ?? "Quản lý"
  const email = profile?.email ?? user?.email ?? ""

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold">Hồ sơ cá nhân</h1>
        <p className="text-sm text-[var(--muted-foreground)]">
          {isStaff ? "Xem thông tin công việc và cập nhật số điện thoại của bạn." : "Thông tin tài khoản quản trị của bạn."}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Thông tin tài khoản</CardTitle>
          <CardDescription>Thông tin xác định tài khoản đăng nhập.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="flex items-center gap-4">
            <Avatar className="size-16">
              {profile?.avatarUrl && <AvatarImage src={profile.avatarUrl} alt={fullName} />}
              <AvatarFallback className="bg-[var(--accent)] text-lg text-white">{getInitials(fullName)}</AvatarFallback>
            </Avatar>
            <div className="flex flex-col gap-1">
              <p className="text-lg font-semibold text-foreground">{fullName}</p>
              <p className="text-sm text-muted-foreground">{email}</p>
              <Badge variant="secondary" className="w-fit">{isStaff ? "Nhân viên" : "Quản trị viên"}</Badge>
              {isStaff && (
                <>
                  <Input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                    onChange={onAvatarSelected}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="mt-2 w-fit"
                    disabled={isUploadingAvatar}
                    onClick={() => avatarInputRef.current?.click()}
                  >
                    {isUploadingAvatar ? "Đang tải lên..." : "Đổi ảnh đại diện"}
                  </Button>
                </>
              )}
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <ReadOnlyItem label="Họ và tên" value={fullName} />
            <ReadOnlyItem label="Email" value={email} />
          </div>
        </CardContent>
      </Card>

      {isStaff && profile && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Thông tin liên hệ</CardTitle>
              <CardDescription>Bạn có thể tự cập nhật số điện thoại liên hệ.</CardDescription>
            </CardHeader>
            <CardContent>
              <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="flex max-w-xl flex-col gap-5">
                  <FormField
                    control={form.control}
                    name="phone"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Số điện thoại</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode="tel" placeholder="Ví dụ: 0901234567" />
                        </FormControl>
                        <FormDescription>Để trống nếu bạn không muốn lưu số điện thoại.</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <Button type="submit" className="w-fit" disabled={isSaving || !form.formState.isDirty}>
                    {isSaving ? <LoaderCircle className="animate-spin" data-icon="inline-start" /> : <Save data-icon="inline-start" />}
                    Lưu thay đổi
                  </Button>
                </form>
              </Form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Thông tin công việc</CardTitle>
              <CardDescription>Thông tin này do quản trị viên quản lý.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <ReadOnlyItem label="Mã nhân viên" value={profile.employeeCode} />
              <ReadOnlyItem label="Chức danh" value={profile.position} />
              <ReadOnlyItem label="Phòng ban" value={profile.department} />
              <ReadOnlyItem label="Ngày vào làm" value={formatDate(profile.hiredAt)} />
              <div className="flex flex-col gap-1 rounded-lg border border-border bg-muted/30 p-4">
                <p className="text-xs font-medium text-muted-foreground">Trạng thái làm việc</p>
                <Badge variant={profile.employmentStatus === "ACTIVE" ? "success" : "secondary"} className="w-fit">
                  {employmentStatusLabels[profile.employmentStatus]}
                </Badge>
              </div>
            </CardContent>
          </Card>
        </>
      )}

      {!isStaff && isAdmin && (
        <Card>
          <CardHeader>
            <CardTitle>Quyền truy cập</CardTitle>
            <CardDescription>Vai trò quản trị được hệ thống cấp và không thể tự thay đổi tại đây.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-3">
            <ReadOnlyItem label="Vai trò" value="Quản trị viên" />
            <ReadOnlyItem label="Trạng thái tài khoản" value={user?.status ?? null} />
            <ReadOnlyItem label="Quyền hệ thống" value={`${user?.permissions.length ?? 0} quyền`} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
