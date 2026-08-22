"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { format } from "date-fns"
import { vi } from "date-fns/locale"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Badge } from "@/components/ui/status-badge"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Calendar } from "@/components/ui/calendar"
import { toast } from "sonner"
import { Camera, Save, ShieldCheck, Bell, Mail, CalendarDays } from "lucide-react"

const profileSchema = z.object({
  fullName: z.string().min(2, "Họ tên phải có ít nhất 2 ký tự"),
  email: z.string().email("Email không hợp lệ"),
  phone: z.string().optional(),
  dateOfBirth: z.string().optional(),
  gender: z.string().optional(),
  nationality: z.string().optional(),
  addressLine: z.string().optional(),
  city: z.string().optional(),
  country: z.string().optional(),
  bio: z.string().optional(),
})

type ProfileFormData = z.infer<typeof profileSchema>

export default function ProfilePage() {
  const [isLoading, setIsLoading] = useState(false)
  const [dobOpen, setDobOpen] = useState(false)
  const [notifications, setNotifications] = useState({
    email: true,
    sms: false,
  })

  // Mock user data
  const user = {
    fullName: "Nguyễn Văn A",
    email: "nguyen.vana@email.com",
    phone: "090 123 4567",
    dateOfBirth: "1995-06-15",
    gender: "MALE",
    nationality: "VN",
    addressLine: "123 Nguyễn Trãi",
    city: "TP. Hồ Chí Minh",
    country: "Việt Nam",
    bio: "Thích du lịch và khám phá những nơi mới",
    avatar: null,
    emailVerified: true,
    joinedAt: "2024-01-15",
    loyaltyPoints: 0,
  }

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isDirty },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    defaultValues: user,
  })

  const dobValue = watch("dateOfBirth")

  const onSubmit = async (data: ProfileFormData) => {
    setIsLoading(true)
    await new Promise((resolve) => setTimeout(resolve, 1000))
    toast.success("Cập nhật hồ sơ thành công")
    setIsLoading(false)
  }

  const handleDateSelect = (date: Date) => {
    setValue("dateOfBirth", format(date, "yyyy-MM-dd"), { shouldDirty: true })
    setDobOpen(false)
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
            <CardTitle className="text-base font-medium">Ảnh đại diện</CardTitle>
            <CardDescription>Cập nhật ảnh đại diện của bạn</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-6">
              <div className="relative">
                <Avatar className="h-24 w-24">
                  <AvatarImage src={user.avatar || undefined} />
                  <AvatarFallback className="bg-[var(--accent)] text-white text-3xl font-medium">
                    {user.fullName.split(" ").filter(Boolean).slice(0, 2).map(n => n[0]).join("").toUpperCase()}
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
                <Button variant="outline" size="sm" className="mt-2 h-9">
                  Tải ảnh lên
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Basic Info */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-medium">Thông tin cơ bản</CardTitle>
            <CardDescription>Cập nhật thông tin cá nhân của bạn</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
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
                  {user.emailVerified && (
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
                <Popover open={dobOpen} onOpenChange={setDobOpen}>
                  <PopoverTrigger asChild>
                    <div className="relative">
                      <Input
                        id="dateOfBirth"
                        placeholder="Chọn ngày sinh"
                        value={dobValue ? format(new Date(dobValue), "dd/MM/yyyy", { locale: vi }) : ""}
                        readOnly
                        className="h-12 cursor-pointer pr-12"
                      />
                      <button
                        type="button"
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] hover:text-[var(--foreground)] transition-colors"
                      >
                        <CalendarDays className="h-5 w-5" />
                      </button>
                    </div>
                  </PopoverTrigger>
                  <PopoverContent className="w-auto p-0" align="start">
                    <Calendar
                      selected={dobValue ? new Date(dobValue) : undefined}
                      onSelect={handleDateSelect}
                      maxDate={new Date()}
                    />
                  </PopoverContent>
                </Popover>
              </div>

              <div className="space-y-3">
                <Label htmlFor="gender" className="text-sm font-medium">
                  Giới tính
                </Label>
                <select
                  id="gender"
                  {...register("gender")}
                  className="flex h-12 w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-3 text-sm transition-all focus-visible:outline-none focus-visible:border-[var(--foreground)] focus-visible:bg-[var(--background)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <option value="">Chọn giới tính</option>
                  <option value="MALE">Nam</option>
                  <option value="FEMALE">Nữ</option>
                  <option value="OTHER">Khác</option>
                </select>
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

            <Separator />

            <div className="space-y-4">
              <h4 className="text-sm font-medium text-[var(--foreground)]">Địa chỉ</h4>
              <div className="space-y-4">
                <div className="space-y-3">
                  <Input
                    id="addressLine"
                    {...register("addressLine")}
                    placeholder="Số nhà, đường"
                    className="h-12"
                  />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-3">
                    <Input
                      id="city"
                      {...register("city")}
                      placeholder="Thành phố"
                      className="h-12"
                    />
                  </div>
                  <div className="space-y-3">
                    <Input
                      id="country"
                      {...register("country")}
                      placeholder="Quốc gia"
                      className="h-12"
                    />
                  </div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Bio */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-medium">Giới thiệu bản thân</CardTitle>
            <CardDescription>Một vài dòng giới thiệu về bạn</CardDescription>
          </CardHeader>
          <CardContent>
            <Textarea
              {...register("bio")}
              placeholder="Chia sẻ đôi điều về bản thân..."
              maxLength={500}
              showCount
              className="min-h-[100px] text-sm"
            />
          </CardContent>
        </Card>

        {/* Save Button */}
        <div className="flex items-center justify-end gap-4">
          <p className="text-sm text-[var(--muted-foreground)]">
            {isDirty ? "Bạn có thay đổi chưa lưu" : "Đã lưu tất cả thay đổi"}
          </p>
          <Button type="submit" disabled={!isDirty || isLoading} className="h-11 px-6 font-medium">
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
          <div className="flex items-center gap-2">
            <Bell className="h-5 w-5" />
            <CardTitle className="text-base font-medium">Thông báo</CardTitle>
          </div>
          <CardDescription>Quản lý cách bạn nhận thông báo</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
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
