"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { KeyRound, Loader2, Pencil, RefreshCw, Search, UserPlus, UserRound, UserRoundX } from "lucide-react"
import { toast } from "sonner"

import {
  Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle,
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
  Input, Label, Select, SelectContent, SelectGroup, SelectItem, SelectTrigger,
  SelectValue, Skeleton,
} from "@/components/ui"
import { DataTable } from "@/components/ui/dataTable"
import {
  createStaff, getStaffManagementProfiles, resendStaffInvitation, resetStaffPassword,
  updateStaffEmploymentStatus, updateStaffProfile,
} from "@/lib/api/staff"
import { useAuth } from "@/lib/auth-context"
import type { EmploymentStatus, StaffManagementListItem } from "@/types/staff"

const statusLabels: Record<EmploymentStatus, string> = {
  ACTIVE: "Đang làm việc", ON_LEAVE: "Nghỉ phép", TERMINATED: "Đã nghỉ việc",
}
const statusVariants: Record<EmploymentStatus, "active" | "warning" | "deactivated"> = {
  ACTIVE: "active", ON_LEAVE: "warning", TERMINATED: "deactivated",
}
const accountLabels: Record<string, string> = {
  PENDING_VERIFICATION: "Chờ xác thực", ACTIVE: "Đã xác thực", SUSPENDED: "Bị tạm khóa", DEACTIVATED: "Đã vô hiệu hóa",
}

interface CreateForm {
  email: string
  fullName: string
  phone: string
  temporaryPassword: string
  position: string
  department: string
  hiredAt: string
  baseSalary: string
}
interface EditForm { position: string; department: string; baseSalary: string }
interface PasswordForm { newPassword: string; confirmPassword: string }

const emptyCreateForm = (): CreateForm => ({
  email: "", fullName: "", phone: "", temporaryPassword: "", position: "", department: "",
  hiredAt: new Date().toISOString().slice(0, 10), baseSalary: "",
})

function formatDate(value: string | null | undefined) {
  if (!value) return "—"
  return new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(`${value}T00:00:00`))
}
function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    const fieldErrors = (error as Error & { fieldErrors?: Record<string, string> }).fieldErrors
    const details = fieldErrors ? Object.values(fieldErrors).filter(Boolean).join(" ") : ""
    return details ? `${error.message} ${details}` : error.message
  }
  return fallback
}
const vndFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 })
function formatVndInput(value: string) {
  const digits = value.replace(/\D/g, "")
  return digits ? vndFormatter.format(Number(digits)) : ""
}
function parseVndInput(value: string) {
  const digits = value.replace(/\D/g, "")
  return digits ? Number(digits) : null
}
function formatSalary(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") return "Chưa cập nhật"
  const amount = typeof value === "number" ? value : Number(value.replace(/,/g, ""))
  return Number.isFinite(amount) ? `${vndFormatter.format(amount)} VND` : "Chưa cập nhật"
}
function formatSalaryInput(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") return ""
  const amount = typeof value === "number" ? value : Number(value.replace(/,/g, ""))
  return Number.isFinite(amount) ? formatVndInput(String(Math.trunc(amount))) : ""
}

export default function StaffManagementPage() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [staff, setStaff] = useState<StaffManagementListItem[]>([])
  const [search, setSearch] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [resendOpen, setResendOpen] = useState(false)
  const [statusTarget, setStatusTarget] = useState<StaffManagementListItem | null>(null)
  const [editingStaff, setEditingStaff] = useState<StaffManagementListItem | null>(null)
  const [passwordTarget, setPasswordTarget] = useState<StaffManagementListItem | null>(null)
  const [resendTarget, setResendTarget] = useState<StaffManagementListItem | null>(null)
  const [createForm, setCreateForm] = useState<CreateForm>(emptyCreateForm())
  const [editForm, setEditForm] = useState<EditForm>({ position: "", department: "", baseSalary: "" })
  const [passwordForm, setPasswordForm] = useState<PasswordForm>({ newPassword: "", confirmPassword: "" })
  const [resendForm, setResendForm] = useState<PasswordForm>({ newPassword: "", confirmPassword: "" })
  const [statusForm, setStatusForm] = useState<EmploymentStatus>("ACTIVE")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const canManage = user?.permissions.includes("staff:manage") ?? false

  const loadData = useCallback(async () => {
    if (!canManage) { setIsLoading(false); return }
    setIsLoading(true); setLoadError(null)
    try { setStaff(await getStaffManagementProfiles(false)) }
    catch (error) { setLoadError(getErrorMessage(error, "Không thể tải danh sách Staff. Vui lòng thử lại.")) }
    finally { setIsLoading(false) }
  }, [canManage])
  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadData(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthenticated, isAuthLoading, loadData])

  const filteredStaff = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return staff
    return staff.filter((member) =>
      [member.employeeCode, member.fullName, member.email, member.position, member.department]
        .filter(Boolean).some((value) => value!.toLowerCase().includes(term))
    )
  }, [search, staff])

  function openCreate() { setCreateForm(emptyCreateForm()); setCreateOpen(true) }
  function openEdit(member: StaffManagementListItem) {
    setEditingStaff(member)
    setEditForm({
      position: member.position ?? "",
      department: member.department ?? "",
      baseSalary: formatSalaryInput(member.baseSalary),
    })
    setEditOpen(true)
  }
  function openPassword(member: StaffManagementListItem) {
    setPasswordTarget(member); setPasswordForm({ newPassword: "", confirmPassword: "" }); setPasswordOpen(true)
  }
  function openResend(member: StaffManagementListItem) {
    setResendTarget(member); setResendForm({ newPassword: "", confirmPassword: "" }); setResendOpen(true)
  }
  function openStatus(member: StaffManagementListItem) { setStatusTarget(member); setStatusForm(member.employmentStatus) }

  async function submitCreate() {
    if (!createForm.email || !createForm.fullName || createForm.temporaryPassword.length < 12) {
      toast.error("Vui lòng nhập email, họ tên và mật khẩu tạm tối thiểu 12 ký tự."); return
    }
    setIsSubmitting(true)
    try {
      const created = await createStaff({
        email: createForm.email.trim(), fullName: createForm.fullName.trim(),
        phone: createForm.phone.trim() || null, temporaryPassword: createForm.temporaryPassword,
        position: createForm.position.trim() || null, department: createForm.department.trim() || null,
        hiredAt: createForm.hiredAt || null, baseSalary: parseVndInput(createForm.baseSalary),
      })
      toast.success(`Đã tạo lời mời Staff · Mã nhân viên: ${created.employeeCode}`)
      setCreateOpen(false); await loadData()
    } catch (error) { toast.error(getErrorMessage(error, "Không thể tạo tài khoản Staff.")) }
    finally { setIsSubmitting(false) }
  }

  async function submitEdit() {
    if (!editingStaff) return
    if (editForm.baseSalary.replace(/\D/g, "").length > 12) { toast.error("Lương cơ bản không hợp lệ."); return }
    setIsSubmitting(true)
    try {
      await updateStaffProfile(editingStaff.employeeCode, {
        position: editForm.position.trim() || null, department: editForm.department.trim() || null,
        ...(editForm.baseSalary ? { baseSalary: parseVndInput(editForm.baseSalary) } : {}),
      })
      toast.success("Đã cập nhật hồ sơ Staff."); setEditOpen(false); await loadData()
    } catch (error) { toast.error(getErrorMessage(error, "Không thể cập nhật hồ sơ Staff.")) }
    finally { setIsSubmitting(false) }
  }

  async function submitPassword() {
    if (!passwordTarget) return
    if (passwordForm.newPassword.length < 12 || passwordForm.newPassword !== passwordForm.confirmPassword) {
      toast.error("Mật khẩu phải dài ít nhất 12 ký tự và hai ô phải khớp."); return
    }
    setIsSubmitting(true)
    try {
      await resetStaffPassword(passwordTarget.employeeCode, { newPassword: passwordForm.newPassword })
      toast.success("Đã đổi mật khẩu Staff và thu hồi phiên đăng nhập cũ."); setPasswordOpen(false)
    } catch (error) { toast.error(getErrorMessage(error, "Không thể đổi mật khẩu Staff.")) }
    finally { setIsSubmitting(false) }
  }

  async function submitResend() {
    if (!resendTarget) return
    if (resendForm.newPassword.length < 12 || resendForm.newPassword.length > 64
      || resendForm.newPassword !== resendForm.confirmPassword) {
      toast.error("Mật khẩu tạm phải dài 12-64 ký tự và hai ô phải khớp."); return
    }
    setIsSubmitting(true)
    try {
      await resendStaffInvitation(resendTarget.employeeCode, { temporaryPassword: resendForm.newPassword })
      toast.success("Đã gửi lại email invitation với mật khẩu tạm mới."); setResendOpen(false)
    }
    catch (error) { toast.error(getErrorMessage(error, "Không thể gửi lại invitation.")) }
    finally { setIsSubmitting(false) }
  }

  function resendInvitation(member: StaffManagementListItem) {
    openResend(member)
  }

  async function submitStatus() {
    if (!statusTarget) return
    setIsSubmitting(true)
    try {
      await updateStaffEmploymentStatus(statusTarget.employeeCode, { employmentStatus: statusForm })
      toast.success("Đã cập nhật trạng thái Staff."); setStatusTarget(null); await loadData()
    } catch (error) { toast.error(getErrorMessage(error, "Không thể cập nhật trạng thái Staff.")) }
    finally { setIsSubmitting(false) }
  }

  if (isAuthLoading || !isAuthenticated) {
    return <div className="flex flex-col gap-4"><Skeleton className="h-10 w-64" /><Skeleton className="h-96 w-full" /></div>
  }
  if (!canManage) {
    return <Card><CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
      <UserRound className="text-muted-foreground" /><h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
      <p className="text-sm text-muted-foreground">Tài khoản cần permission <code>staff:manage</code>.</p>
    </CardContent></Card>
  }

  const columns = [
    { key: "staff", header: "Nhân viên", render: (member: StaffManagementListItem) => <div><div className="font-medium">{member.fullName}</div><div className="text-xs text-muted-foreground">{member.employeeCode}</div></div> },
    { key: "email", header: "Email", render: (member: StaffManagementListItem) => member.email },
    { key: "phone", header: "Số điện thoại", render: (member: StaffManagementListItem) => member.phone || "Chưa cập nhật" },
    { key: "account", header: "Tài khoản", render: (member: StaffManagementListItem) => <Badge variant={member.accountStatus === "ACTIVE" ? "active" : "warning"}>{accountLabels[member.accountStatus] ?? member.accountStatus}</Badge> },
    { key: "position", header: "Chức danh", render: (member: StaffManagementListItem) => <div><div>{member.position || "Chưa cập nhật"}</div><div className="text-xs text-muted-foreground">{member.department || "Chưa phân phòng ban"}</div></div> },
    { key: "baseSalary", header: "Lương cơ bản", render: (member: StaffManagementListItem) => formatSalary(member.baseSalary) },
    { key: "hiredAt", header: "Ngày vào làm", render: (member: StaffManagementListItem) => formatDate(member.hiredAt) },
    { key: "status", header: "Trạng thái", render: (member: StaffManagementListItem) => <Badge variant={statusVariants[member.employmentStatus]}>{statusLabels[member.employmentStatus]}</Badge> },
    { key: "actions", header: "Thao tác", className: "text-right", render: (member: StaffManagementListItem) => <div className="flex justify-end gap-2"><Button variant="ghost" size="sm" onClick={() => openEdit(member)} disabled={isSubmitting}><Pencil data-icon="inline-start" /> Sửa</Button><Button variant="ghost" size="sm" onClick={() => openPassword(member)} disabled={isSubmitting}><KeyRound data-icon="inline-start" /> Mật khẩu</Button>{member.accountStatus === "PENDING_VERIFICATION" && <Button variant="outline" size="sm" onClick={() => void resendInvitation(member)} disabled={isSubmitting}>Gửi lại</Button>}{member.employmentStatus !== "TERMINATED" && <Button variant="outline" size="sm" onClick={() => openStatus(member)} disabled={isSubmitting}><UserRoundX data-icon="inline-start" /> Trạng thái</Button>}</div> },
  ]

  return <div className="flex flex-col gap-6">
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div><h1 className="text-2xl font-bold">Quản lý nhân viên</h1><p className="text-sm text-[var(--muted-foreground)]">Tạo tài khoản Staff độc lập và quản lý hồ sơ, trạng thái, mật khẩu.</p></div><div className="flex gap-2"><Button variant="outline" onClick={() => void loadData()} disabled={isLoading}><RefreshCw data-icon="inline-start" /> Làm mới</Button><Button onClick={openCreate}><UserPlus data-icon="inline-start" /> Tạo tài khoản Staff</Button></div></div>
    {loadError && <Card><CardContent className="flex items-center justify-between gap-4 p-4 text-sm text-destructive"><span>{loadError}</span><Button variant="outline" size="sm" onClick={() => void loadData()}>Thử lại</Button></CardContent></Card>}
    <Card><CardHeader><CardTitle>Danh sách Staff</CardTitle><CardDescription>{staff.length} hồ sơ · bao gồm lời mời chưa xác thực và Staff đã nghỉ việc</CardDescription></CardHeader><CardContent className="flex flex-col gap-4"><div className="relative max-w-xl"><Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" /><Input className="pl-10" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã, tên, email, chức danh..." /></div>{isLoading ? <Skeleton className="h-96 w-full" /> : <DataTable columns={columns} data={filteredStaff} keyExtractor={(member) => member.employeeCode} emptyMessage="Chưa có Staff phù hợp" tableWrapperClassName="max-h-[32rem] overflow-y-auto" />}</CardContent></Card>

    <Dialog open={createOpen} onOpenChange={(open) => !isSubmitting && setCreateOpen(open)}><DialogContent className="max-w-2xl"><DialogHeader><DialogTitle>Tạo tài khoản Staff</DialogTitle><DialogDescription>Staff sẽ nhận email invitation để xác thực và đặt mật khẩu chính thức. Mật khẩu tạm không được gửi trong email.</DialogDescription></DialogHeader><div className="grid gap-4 md:grid-cols-2"><div className="grid gap-2"><Label htmlFor="create-email">Email Staff *</Label><Input id="create-email" type="email" value={createForm.email} onChange={(event) => setCreateForm((current) => ({ ...current, email: event.target.value }))} /></div><div className="grid gap-2"><Label htmlFor="create-name">Họ và tên *</Label><Input id="create-name" value={createForm.fullName} onChange={(event) => setCreateForm((current) => ({ ...current, fullName: event.target.value }))} /></div><div className="grid gap-2"><Label htmlFor="create-phone">Số điện thoại</Label><Input id="create-phone" value={createForm.phone} onChange={(event) => setCreateForm((current) => ({ ...current, phone: event.target.value }))} /></div><div className="grid gap-2"><Label htmlFor="create-password">Mật khẩu tạm *</Label><Input id="create-password" type="password" autoComplete="new-password" value={createForm.temporaryPassword} onChange={(event) => setCreateForm((current) => ({ ...current, temporaryPassword: event.target.value }))} placeholder="Tối thiểu 12 ký tự" /></div><div className="grid gap-2"><Label htmlFor="create-position">Chức danh</Label><Input id="create-position" value={createForm.position} onChange={(event) => setCreateForm((current) => ({ ...current, position: event.target.value }))} placeholder="Có thể để trống" /></div><div className="grid gap-2"><Label htmlFor="create-department">Phòng ban</Label><Input id="create-department" value={createForm.department} onChange={(event) => setCreateForm((current) => ({ ...current, department: event.target.value }))} placeholder="Có thể để trống" /></div><div className="grid gap-2"><Label htmlFor="create-hired-at">Ngày vào làm</Label><Input id="create-hired-at" type="date" value={createForm.hiredAt} onChange={(event) => setCreateForm((current) => ({ ...current, hiredAt: event.target.value }))} /></div><div className="grid gap-2"><div className="flex items-center justify-between"><Label htmlFor="create-salary">Lương cơ bản</Label><span className="text-xs font-medium text-muted-foreground">VND</span></div><Input id="create-salary" inputMode="numeric" value={createForm.baseSalary} onChange={(event) => setCreateForm((current) => ({ ...current, baseSalary: formatVndInput(event.target.value) }))} placeholder="8.000.000" /></div></div><DialogFooter><Button variant="outline" onClick={() => setCreateOpen(false)} disabled={isSubmitting}>Hủy</Button><Button onClick={() => void submitCreate()} disabled={isSubmitting}>{isSubmitting ? <Loader2 className="animate-spin" /> : <UserPlus data-icon="inline-start" />} Tạo và gửi invitation</Button></DialogFooter></DialogContent></Dialog>

    <Dialog open={editOpen} onOpenChange={(open) => !isSubmitting && setEditOpen(open)}><DialogContent><DialogHeader><DialogTitle>Chỉnh sửa hồ sơ Staff</DialogTitle><DialogDescription>{editingStaff?.fullName} · {editingStaff?.employeeCode}</DialogDescription></DialogHeader><div className="flex flex-col gap-4"><div className="grid gap-2"><Label>Chức danh</Label><Input value={editForm.position} onChange={(event) => setEditForm((current) => ({ ...current, position: event.target.value }))} /></div><div className="grid gap-2"><Label>Phòng ban</Label><Input value={editForm.department} onChange={(event) => setEditForm((current) => ({ ...current, department: event.target.value }))} /></div><div className="grid gap-2"><div className="flex items-center justify-between"><Label>Lương cơ bản</Label><span className="text-xs font-medium text-muted-foreground">VND</span></div><Input inputMode="numeric" value={editForm.baseSalary} onChange={(event) => setEditForm((current) => ({ ...current, baseSalary: formatVndInput(event.target.value) }))} placeholder="8.000.000" /></div></div><DialogFooter><Button variant="outline" onClick={() => setEditOpen(false)} disabled={isSubmitting}>Hủy</Button><Button onClick={() => void submitEdit()} disabled={isSubmitting}>Lưu thay đổi</Button></DialogFooter></DialogContent></Dialog>

    <Dialog open={passwordOpen} onOpenChange={(open) => !isSubmitting && setPasswordOpen(open)}><DialogContent><DialogHeader><DialogTitle>Đổi mật khẩu Staff</DialogTitle><DialogDescription>Admin không cần biết mật khẩu cũ. Các refresh token hiện tại sẽ bị thu hồi.</DialogDescription></DialogHeader><div className="flex flex-col gap-4"><div className="grid gap-2"><Label>Mật khẩu mới</Label><Input type="password" autoComplete="new-password" value={passwordForm.newPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))} placeholder="Tối thiểu 12 ký tự" /></div><div className="grid gap-2"><Label>Nhập lại mật khẩu</Label><Input type="password" autoComplete="new-password" value={passwordForm.confirmPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))} /></div></div><DialogFooter><Button variant="outline" onClick={() => setPasswordOpen(false)} disabled={isSubmitting}>Hủy</Button><Button onClick={() => void submitPassword()} disabled={isSubmitting}>{isSubmitting ? <Loader2 className="animate-spin" /> : <KeyRound data-icon="inline-start" />} Đổi mật khẩu</Button></DialogFooter></DialogContent></Dialog>

    <Dialog open={statusTarget !== null} onOpenChange={(open) => !open && !isSubmitting && setStatusTarget(null)}><DialogContent><DialogHeader><DialogTitle>Cập nhật trạng thái Staff</DialogTitle><DialogDescription>{statusTarget?.fullName} · {statusTarget?.employeeCode}</DialogDescription></DialogHeader><div className="flex flex-col gap-2"><Label>Trạng thái</Label><Select value={statusForm} onValueChange={(value) => setStatusForm(value as EmploymentStatus)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="ACTIVE">Đang làm việc</SelectItem><SelectItem value="ON_LEAVE">Nghỉ phép</SelectItem><SelectItem value="TERMINATED">Đã nghỉ việc</SelectItem></SelectGroup></SelectContent></Select></div><DialogFooter><Button variant="outline" onClick={() => setStatusTarget(null)} disabled={isSubmitting}>Hủy</Button><Button onClick={() => void submitStatus()} disabled={isSubmitting}>Lưu trạng thái</Button></DialogFooter></DialogContent></Dialog>
    <Dialog open={resendOpen} onOpenChange={(open) => !isSubmitting && setResendOpen(open)}><DialogContent><DialogHeader><DialogTitle>Gửi lại invitation</DialogTitle><DialogDescription>Nhập mật khẩu tạm mới. Mật khẩu này sẽ được gửi trong email và dùng để đăng nhập sau khi kích hoạt.</DialogDescription></DialogHeader><div className="flex flex-col gap-4"><div className="grid gap-2"><Label>Mật khẩu tạm mới</Label><Input type="password" autoComplete="new-password" value={resendForm.newPassword} onChange={(event) => setResendForm((current) => ({ ...current, newPassword: event.target.value }))} placeholder="12-64 ký tự" /></div><div className="grid gap-2"><Label>Nhập lại mật khẩu</Label><Input type="password" autoComplete="new-password" value={resendForm.confirmPassword} onChange={(event) => setResendForm((current) => ({ ...current, confirmPassword: event.target.value }))} /></div></div><DialogFooter><Button variant="outline" onClick={() => setResendOpen(false)} disabled={isSubmitting}>Hủy</Button><Button onClick={() => void submitResend()} disabled={isSubmitting}>{isSubmitting ? <Loader2 className="animate-spin" /> : "Gửi lại invitation"}</Button></DialogFooter></DialogContent></Dialog>
  </div>
}
