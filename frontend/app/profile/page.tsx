"use client"

import { useState, useEffect } from "react"
import { useForm, Controller } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "sonner"
import { Camera, Save, ShieldCheck, Globe } from "lucide-react"
import { DatePickerDob } from "@/components/ui/date-picker-dob"
import {
  getCustomerProfile,
  updateCustomerProfile,
  getProvinces,
  type CustomerProfile,
  type Province,
} from "@/lib/api/customer-profile"

const profileSchema = z.object({
  fullName: z.string().min(2, "Họ tên phải có ít nhất 2 ký tự"),
  email: z.string().email("Email không hợp lệ"),
  phone: z.string().optional(),
  dateOfBirth: z.string().optional(),
  gender: z.string().optional(),
  nationality: z.string().optional(),
  addressLine: z.string().optional(),
  province: z.string().optional(),
  country: z.string().optional(),
})

type ProfileFormData = z.infer<typeof profileSchema>

export default function ProfilePage() {
  const [isLoading, setIsLoading] = useState(false)
  const [isFetching, setIsFetching] = useState(true)
  const [notifications, setNotifications] = useState({
    email: true,
    sms: false,
  })
  const [profile, setProfile] = useState<CustomerProfile | null>(null)
  const [provinces, setProvinces] = useState<Province[]>([])

  const {
    register,
    control,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: "",
      email: "",
      phone: "",
      dateOfBirth: "",
      gender: "",
      nationality: "",
      addressLine: "",
      province: "",
      country: "VN",
    },
  })

  const dobValue = watch("dateOfBirth")
  const provinceValue = watch("province")

  // Fetch provinces list
  useEffect(() => {
    async function fetchProvinces() {
      try {
        const data = await getProvinces()
        setProvinces(data)
      } catch {
        console.error("Failed to load provinces")
      }
    }
    fetchProvinces()
  }, [])

  // Fetch profile data
  useEffect(() => {
    async function fetchProfile() {
      try {
        const data = await getCustomerProfile()
        setProfile(data)
        reset({
          fullName: data.fullName || "",
          email: data.email || "",
          phone: data.phone || "",
          dateOfBirth: data.dateOfBirth || "",
          gender: data.gender || "",
          nationality: data.nationality || "",
          addressLine: data.addressLine || "",
          province: data.province || "",
          country: data.country || "VN",
        })
      } catch (err) {
        const error = err as { status?: number }
        if (error.status === 401) {
          if (typeof window !== "undefined") {
            const refreshToken = localStorage.getItem("refresh_token")
            if (!refreshToken) {
              window.location.href = "/login?redirect=/profile"
            }
          }
          toast.error("Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.")
        } else if (error.status === 403) {
          toast.error("Bạn không có quyền truy cập trang này.")
        } else {
          toast.error("Không thể tải thông tin hồ sơ")
        }
      } finally {
        setIsFetching(false)
      }
    }
    fetchProfile()
  }, [reset])

  const onSubmit = async (data: ProfileFormData) => {
    setIsLoading(true)
    try {
      const updated = await updateCustomerProfile({
        dateOfBirth: data.dateOfBirth || null,
        gender: data.gender || null,
        nationality: data.nationality || null,
        addressLine: data.addressLine || null,
        province: data.province || null,
        country: data.country || null,
      })
      setProfile(updated)
      reset({
        fullName: updated.fullName || "",
        email: updated.email || "",
        phone: updated.phone || "",
        dateOfBirth: updated.dateOfBirth || "",
        gender: updated.gender || "",
        nationality: updated.nationality || "",
        addressLine: updated.addressLine || "",
        province: updated.province || "",
        country: updated.country || "VN",
      })
      toast.success("Cập nhật hồ sơ thành công")
    } catch {
      toast.error("Cập nhật hồ sơ thất bại")
    } finally {
      setIsLoading(false)
    }
  }

  const getInitials = (name: string): string => {
    return name
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((n) => n[0])
      .join("")
      .toUpperCase()
  }

  if (isFetching) {
    return (
      <div className="space-y-8">
        <div>
          <Skeleton className="h-10 w-48" />
          <Skeleton className="mt-2 h-5 w-64" />
        </div>
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-80 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <h1 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Hồ sơ cá nhân</h1>
        <p className="mt-2 text-base text-[var(--muted-foreground)]">
          Quản lý thông tin cá nhân và cài đặt tài khoản
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Avatar Section */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-medium text-[var(--accent)]">Ảnh đại diện</CardTitle>
            <CardDescription>Cập nhật ảnh đại diện của bạn</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-6">
              <div className="relative">
                <Avatar className="h-24 w-24">
                  <AvatarFallback className="bg-[var(--accent)] text-white text-3xl font-medium">
                    {profile?.fullName ? getInitials(profile.fullName) : "U"}
                  </AvatarFallback>
                </Avatar>
                <button
                  type="button"
                  className="absolute -bottom-2 -right-2 flex h-8 w-8 items-center justify-center rounded-full bg-[var(--accent)] text-white shadow-lg hover:bg-[var(--accent)]/90 transition-colors"
                >
                  <Camera className="h-4 w-4" />
                </button>
              </div>
              <div>
                <p className="text-sm font-medium text-[var(--foreground)]">Ảnh đại diện</p>
                <p className="text-xs text-[var(--muted-foreground)]">
                  JPG, PNG hoặc GIF. Kích thước tối đa 2MB.
                </p>
                <Button variant="outline" size="sm" className="mt-2 h-9 border-[var(--accent)] text-[var(--accent)] hover:bg-[var(--accent)] hover:text-white">
                  Tải ảnh lên
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Basic Info */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-medium text-[var(--accent)]">Thông tin cơ bản</CardTitle>
            <CardDescription>Cập nhật thông tin cá nhân của bạn</CardDescription>
            <Separator className="mt-3" />
          </CardHeader>
          <CardContent className="space-y-5 pt-4">
            <div className="grid gap-5 sm:grid-cols-2">
              <div className="space-y-3">
                <Label htmlFor="fullName" className="text-sm font-medium">
                  Họ và tên <span className="text-[var(--destructive)]">*</span>
                </Label>
                <Input
                  id="fullName"
                  {...register("fullName")}
                  className={`h-12 ${errors.fullName ? "border-[var(--destructive)]" : ""}`}
                />
                {errors.fullName && (
                  <p className="text-xs text-[var(--destructive)]">{errors.fullName.message}</p>
                )}
              </div>

              <div className="space-y-3">
                <Label htmlFor="email" className="text-sm font-medium">
                  Email <span className="text-[var(--destructive)]">*</span>
                </Label>
                <div className="flex gap-2">
                  <Input
                    id="email"
                    type="email"
                    disabled
                    {...register("email")}
                    className={`h-12 flex-1 ${errors.email ? "border-[var(--destructive)]" : ""}`}
                  />
                  {profile?.emailVerified && (
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-green-100">
                      <ShieldCheck className="h-5 w-5 text-green-600" />
                    </div>
                  )}
                </div>
              </div>

              <div className="space-y-3">
                <Label htmlFor="phone" className="text-sm font-medium">
                  Số điện thoại
                </Label>
                <Input
                  id="phone"
                  type="tel"
                  {...register("phone")}
                  placeholder="090 123 4567"
                  className="h-12"
                />
              </div>

              <div className="space-y-3">
                <Label htmlFor="dateOfBirth" className="text-sm font-medium">
                  Ngày sinh
                </Label>
                <DatePickerDob
                  value={dobValue || ""}
                  onChange={(val) => setValue("dateOfBirth", val, { shouldDirty: true })}
                  maxDate={new Date()}
                />
              </div>

              <div className="space-y-3">
                <Label htmlFor="gender" className="text-sm font-medium">
                  Giới tính
                </Label>
                <Controller
                  name="gender"
                  control={control}
                  render={({ field }) => (
                    <Select onValueChange={field.onChange} value={field.value || ""}>
                      <SelectTrigger className="h-12">
                        <SelectValue placeholder="Chọn giới tính" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="MALE">Nam</SelectItem>
                        <SelectItem value="FEMALE">Nữ</SelectItem>
                        <SelectItem value="OTHER">Khác</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="space-y-3">
                <Label htmlFor="nationality" className="text-sm font-medium">
                  Quốc tịch
                </Label>
                <Input
                  id="nationality"
                  {...register("nationality")}
                  placeholder="Việt Nam"
                  className="h-12"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Address Section */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-medium text-[var(--accent)]">Địa chỉ</CardTitle>
            <CardDescription>Thông tin địa chỉ liên lạc của bạn</CardDescription>
            <Separator className="mt-3" />
          </CardHeader>
          <CardContent className="space-y-5 pt-4">
            {/* Country */}
            <div className="space-y-3">
              <Label htmlFor="country" className="text-sm font-medium flex items-center gap-2">
                <Globe className="h-4 w-4" />
                Quốc gia
              </Label>
              <Controller
                name="country"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} value={field.value || "VN"}>
                    <SelectTrigger className="h-12">
                      <SelectValue placeholder="Chọn quốc gia" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="VN">Việt Nam</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            {/* Province/City */}
            <div className="space-y-3">
              <Label htmlFor="province" className="text-sm font-medium">
                Tỉnh/Thành phố <span className="text-[var(--destructive)]">*</span>
              </Label>
              <Controller
                name="province"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} value={field.value || ""}>
                    <SelectTrigger className="h-12">
                      <SelectValue placeholder="Chọn tỉnh/thành phố" />
                    </SelectTrigger>
                    <SelectContent>
                      {provinces.map((province) => (
                        <SelectItem key={province.id} value={province.name}>
                          {province.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.province && (
                <p className="text-xs text-[var(--destructive)]">{errors.province.message}</p>
              )}
            </div>

            {/* Address Line */}
            <div className="space-y-3">
              <Label htmlFor="addressLine" className="text-sm font-medium">
                Địa chỉ chi tiết
              </Label>
              <Input
                id="addressLine"
                {...register("addressLine")}
                placeholder="Số nhà, tên đường, phường/xã, quận/huyện"
                className="h-12"
              />
            </div>
          </CardContent>
        </Card>

        {/* Save Button */}
        <div className="flex items-center justify-end gap-4">
          <p className="text-sm text-[var(--muted-foreground)]">
            {isDirty ? "Bạn có thay đổi chưa lưu" : "Đã lưu tất cả thay đổi"}
          </p>
          <Button type="submit" disabled={!isDirty || isLoading} className="h-11 px-6 font-medium bg-[var(--accent)] hover:bg-[var(--accent)]/90">
            {isLoading ? (
              "Đang lưu..."
            ) : (
              <>
                <Save className="mr-2 h-4 w-4" />
                Lưu thay đổi
              </>
            )}
          </Button>
        </div>
      </form>

      {/* Notification Settings */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base font-medium text-[var(--accent)]">Thông báo</CardTitle>
          <CardDescription>Quản lý cách bạn nhận thông báo</CardDescription>
          <Separator className="mt-3" />
        </CardHeader>
        <CardContent className="space-y-4 pt-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">Thông báo qua email</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Nhận email về đơn đặt phòng và khuyến mãi
              </p>
            </div>
            <Switch
              checked={notifications.email}
              onCheckedChange={(checked) =>
                setNotifications({ ...notifications, email: checked })
              }
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">Thông báo SMS</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Nhận tin nhắn về trạng thái đơn đặt
              </p>
            </div>
            <Switch
              checked={notifications.sms}
              onCheckedChange={(checked) =>
                setNotifications({ ...notifications, sms: checked })
              }
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
