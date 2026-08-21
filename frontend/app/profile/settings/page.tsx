"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/Button"
import { Input } from "@/components/ui/Input"
import { Label } from "@/components/ui/Label"
import { Switch } from "@/components/ui/Switch"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card"
import { Separator } from "@/components/ui/Separator"
import { Badge } from "@/components/ui/status-badge"
import { toast } from "sonner"
import {
  Lock,
  Shield,
  Smartphone,
  Download,
  Trash2,
  Eye,
  EyeOff,
} from "lucide-react"

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Vui lòng nhập mật khẩu hiện tại"),
    newPassword: z
      .string()
      .min(12, "Mật khẩu phải có ít nhất 12 ký tự")
      .max(64, "Mật khẩu không được quá 64 ký tự"),
    confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Mật khẩu nhập lại chưa khớp",
    path: ["confirmPassword"],
  })

type PasswordFormData = z.infer<typeof passwordSchema>

export default function ProfileSettingsPage() {
  const [isLoading, setIsLoading] = useState(false)
  const [showPasswords, setShowPasswords] = useState(false)

  const [settings, setSettings] = useState({
    emailNotifications: true,
    smsNotifications: false,
    marketingEmails: false,
    twoFactorEnabled: false,
    activityLogging: true,
  })

  const {
    register: registerPassword,
    handleSubmit: handlePasswordSubmit,
    formState: { errors: passwordErrors, isDirty: isPasswordDirty },
    reset: resetPassword,
  } = useForm<PasswordFormData>({
    resolver: zodResolver(passwordSchema),
  })

  const onSavePassword = async (data: PasswordFormData) => {
    setIsLoading(true)
    await new Promise((resolve) => setTimeout(resolve, 1000))
    toast.success("Đổi mật khẩu thành công")
    resetPassword()
    setIsLoading(false)
  }

  const onSaveSettings = () => {
    toast.success("Cài đặt đã được lưu")
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <h1 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Cài đặt</h1>
        <p className="mt-2 text-base text-[var(--muted-foreground)]">
          Quản lý bảo mật và quyền riêng tư tài khoản
        </p>
      </div>

      {/* Change Password */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Lock className="h-5 w-5" />
            <CardTitle className="text-base font-medium">Đổi mật khẩu</CardTitle>
          </div>
          <CardDescription>
            Cập nhật mật khẩu để bảo vệ tài khoản của bạn
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={handlePasswordSubmit(onSavePassword)}
            className="space-y-5"
          >
            <div className="space-y-3">
              <Label htmlFor="currentPassword" className="text-sm font-medium">Mật khẩu hiện tại</Label>
              <Input
                id="currentPassword"
                type={showPasswords ? "text" : "password"}
                placeholder="Nhập mật khẩu hiện tại"
                {...registerPassword("currentPassword")}
                className={`h-12 ${passwordErrors.currentPassword ? "border-[var(--destructive)]" : ""}`}
              />
              {passwordErrors.currentPassword && (
                <p className="text-xs text-[var(--destructive)]">{passwordErrors.currentPassword.message}</p>
              )}
            </div>

            <div className="space-y-3">
              <Label htmlFor="newPassword" className="text-sm font-medium">Mật khẩu mới</Label>
              <Input
                id="newPassword"
                type={showPasswords ? "text" : "password"}
                placeholder="Tối thiểu 12 ký tự"
                {...registerPassword("newPassword")}
                className={`h-12 ${passwordErrors.newPassword ? "border-[var(--destructive)]" : ""}`}
              />
              {passwordErrors.newPassword && (
                <p className="text-xs text-[var(--destructive)]">{passwordErrors.newPassword.message}</p>
              )}
            </div>

            <div className="space-y-3">
              <Label htmlFor="confirmPassword" className="text-sm font-medium">Xác nhận mật khẩu mới</Label>
              <Input
                id="confirmPassword"
                type={showPasswords ? "text" : "password"}
                placeholder="Nhập lại mật khẩu mới"
                {...registerPassword("confirmPassword")}
                className={`h-12 ${passwordErrors.confirmPassword ? "border-[var(--destructive)]" : ""}`}
              />
              {passwordErrors.confirmPassword && (
                <p className="text-xs text-[var(--destructive)]">{passwordErrors.confirmPassword.message}</p>
              )}
            </div>

            <div className="flex items-center justify-between">
              <button
                type="button"
                onClick={() => setShowPasswords(!showPasswords)}
                className="flex items-center gap-2 text-sm font-medium text-[var(--accent)] hover:underline"
              >
                {showPasswords ? (
                  <>
                    <EyeOff className="h-4 w-4" />
                    Ẩn mật khẩu
                  </>
                ) : (
                  <>
                    <Eye className="h-4 w-4" />
                    Hiện mật khẩu
                  </>
                )}
              </button>
              <Button type="submit" disabled={!isPasswordDirty || isLoading} className="h-11 px-6 font-medium">
                {isLoading ? "Đang cập nhật..." : "Đổi mật khẩu"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Two-Factor Authentication */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Shield className="h-5 w-5" />
            <CardTitle className="text-base font-medium">Xác thực hai yếu tố</CardTitle>
          </div>
          <CardDescription>
            Thêm lớp bảo mật bổ sung cho tài khoản của bạn
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-[var(--muted)]">
                <Smartphone className="h-6 w-6 text-[var(--muted-foreground)]" />
              </div>
              <div>
                <p className="font-medium text-[var(--foreground)]">Ứng dụng xác thực</p>
                <p className="text-sm text-[var(--muted-foreground)]">
                  Sử dụng app như Google Authenticator hoặc Authy
                </p>
              </div>
            </div>
            <Switch
              checked={settings.twoFactorEnabled}
              onCheckedChange={(checked) =>
                setSettings({ ...settings, twoFactorEnabled: checked })
              }
            />
          </div>
          {settings.twoFactorEnabled && (
            <div className="mt-4 rounded-lg border border-[var(--border)] bg-[var(--muted)]/50 p-4">
              <p className="text-sm text-[var(--foreground)]">
                <Badge variant="success" className="mr-2">Đã bật</Badge>
                Tài khoản của bạn được bảo vệ bằng xác thực hai yếu tố.
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Notification Settings */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Smartphone className="h-5 w-5" />
            <CardTitle className="text-base font-medium">Thông báo</CardTitle>
          </div>
          <CardDescription>
            Quyết định bạn muốn nhận thông báo như thế nào
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">Email thông báo</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Nhận email về đơn đặt phòng và hoạt động tài khoản
              </p>
            </div>
            <Switch
              checked={settings.emailNotifications}
              onCheckedChange={(checked) =>
                setSettings({ ...settings, emailNotifications: checked })
              }
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">SMS thông báo</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Nhận tin nhắn về trạng thái đơn đặt
              </p>
            </div>
            <Switch
              checked={settings.smsNotifications}
              onCheckedChange={(checked) =>
                setSettings({ ...settings, smsNotifications: checked })
              }
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">Email tiếp thị</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Nhận khuyến mãi và ưu đãi đặc biệt
              </p>
            </div>
            <Switch
              checked={settings.marketingEmails}
              onCheckedChange={(checked) =>
                setSettings({ ...settings, marketingEmails: checked })
              }
            />
          </div>
          <Button onClick={onSaveSettings} className="mt-2 h-11 px-6 font-medium">
            Lưu cài đặt thông báo
          </Button>
        </CardContent>
      </Card>

      {/* Danger Zone */}
      <Card className="border-[var(--destructive)]/50">
        <CardHeader>
          <div className="flex items-center gap-2 text-[var(--destructive)]">
            <Trash2 className="h-5 w-5" />
            <CardTitle className="text-base font-medium">Vùng nguy hiểm</CardTitle>
          </div>
          <CardDescription>
            Các hành động không thể hoàn tác
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-[var(--foreground)]">Xóa tài khoản</p>
              <p className="text-sm text-[var(--muted-foreground)]">
                Xóa vĩnh viễn tài khoản và tất cả dữ liệu của bạn
              </p>
            </div>
            <Button variant="destructive" className="h-11 px-6">
              Xóa tài khoản
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
