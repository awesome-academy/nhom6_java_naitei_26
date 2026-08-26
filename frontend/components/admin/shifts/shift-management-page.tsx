"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import {
  closestCenter,
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core"
import {
  addDays,
  format,
  isToday,
  startOfWeek,
} from "date-fns"
import { vi } from "date-fns/locale"
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  GripVertical,
  Loader2,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react"
import { toast } from "sonner"

import {
  Alert,
  AlertDescription,
  AlertTitle,
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  Textarea,
} from "@/components/ui"
import {
  cancelShiftAssignment,
  createShift,
  createShiftAssignment,
  getShiftAssignments,
  getShifts,
  updateShiftAssignment,
} from "@/lib/api/shifts"
import { getStaffProfiles } from "@/lib/api/staff"
import { useAuth } from "@/lib/auth-context"
import type { StaffListItem } from "@/types/staff"
import type {
  AssignmentStatus,
  CreateShiftRequest,
  Shift,
  ShiftAssignment,
} from "@/types/shift"
import { cn } from "@/lib/utils"

const STATUS_LABELS: Record<AssignmentStatus, string> = {
  SCHEDULED: "Đã xếp",
  COMPLETED: "Hoàn thành",
  ABSENT: "Vắng",
  CANCELLED: "Đã hủy",
}

const STATUS_CLASSES: Record<AssignmentStatus, string> = {
  SCHEDULED: "border-blue-200 bg-blue-50 text-blue-800 hover:bg-blue-100",
  COMPLETED: "border-orange-200 bg-orange-50 text-orange-800 hover:bg-orange-100",
  ABSENT: "border-red-200 bg-red-50 text-red-800 hover:bg-red-100",
  CANCELLED: "border-slate-200 bg-slate-100 text-slate-600 hover:bg-slate-200",
}

const PRESETS: Record<string, CreateShiftRequest> = {
  MORNING: {
    code: "MORNING",
    name: "Ca sáng",
    startTime: "06:00",
    endTime: "14:00",
    crossesMidnight: false,
    isActive: true,
  },
  AFTERNOON: {
    code: "AFTERNOON",
    name: "Ca chiều",
    startTime: "14:00",
    endTime: "22:00",
    crossesMidnight: false,
    isActive: true,
  },
  NIGHT: {
    code: "NIGHT",
    name: "Ca đêm",
    startTime: "22:00",
    endTime: "06:00",
    crossesMidnight: true,
    isActive: true,
  },
}

interface AssignmentFormState {
  employeeCode: string
  shiftCode: string
  workDate: string
  status: AssignmentStatus
  note: string
}

interface DragData {
  type: "shift" | "assignment"
  shiftCode?: string
  assignment?: ShiftAssignment
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function getTimeLabel(value: string): string {
  return value.slice(0, 5)
}

function getCellId(employeeCode: string, workDate: string): string {
  return `cell:${employeeCode}:${workDate}`
}

function getEmptyAssignmentForm(date: string, staff?: StaffListItem, shift?: Shift): AssignmentFormState {
  return {
    employeeCode: staff?.employeeCode ?? "",
    shiftCode: shift?.code ?? "",
    workDate: date,
    status: "SCHEDULED",
    note: "",
  }
}

export default function ShiftManagementPage() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date(), { weekStartsOn: 1 }))
  const [staff, setStaff] = useState<StaffListItem[]>([])
  const [shifts, setShifts] = useState<Shift[]>([])
  const [assignments, setAssignments] = useState<ShiftAssignment[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [assignmentDialogOpen, setAssignmentDialogOpen] = useState(false)
  const [editingAssignment, setEditingAssignment] = useState<ShiftAssignment | null>(null)
  const [assignmentForm, setAssignmentForm] = useState<AssignmentFormState>(() =>
    getEmptyAssignmentForm(format(new Date(), "yyyy-MM-dd"))
  )
  const [isSavingAssignment, setIsSavingAssignment] = useState(false)
  const [shiftDialogOpen, setShiftDialogOpen] = useState(false)
  const [shiftForm, setShiftForm] = useState<CreateShiftRequest>(PRESETS.MORNING)
  const [isSavingShift, setIsSavingShift] = useState(false)
  const [activeDrag, setActiveDrag] = useState<DragData | null>(null)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  const canManage = user?.permissions.includes("shift:manage") ?? false
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => addDays(weekStart, index)), [weekStart])
  const weekEnd = days[days.length - 1]

  const loadCalendar = useCallback(async () => {
    if (!canManage) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const [staffData, shiftData, assignmentData] = await Promise.all([
        getStaffProfiles(true),
        getShifts(),
        getShiftAssignments(format(weekStart, "yyyy-MM-dd"), format(weekEnd, "yyyy-MM-dd")),
      ])
      setStaff(staffData)
      setShifts(shiftData)
      setAssignments(assignmentData)
    } catch (loadError) {
      setError(getErrorMessage(loadError, "Không thể tải lịch ca. Vui lòng thử lại."))
    } finally {
      setIsLoading(false)
    }
  }, [canManage, weekEnd, weekStart])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadCalendar(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthenticated, isAuthLoading, loadCalendar])

  const activeShifts = useMemo(() => shifts.filter((shift) => shift.isActive), [shifts])
  const assignmentsByCell = useMemo(() => {
    const grouped = new Map<string, ShiftAssignment[]>()
    assignments.forEach((assignment) => {
      const key = getCellId(assignment.employeeCode, assignment.workDate)
      grouped.set(key, [...(grouped.get(key) ?? []), assignment])
    })
    return grouped
  }, [assignments])

  function openCreateAssignment(date = format(new Date(), "yyyy-MM-dd"), employeeCode = "", shiftCode = "") {
    const selectedStaff = staff.find((item) => item.employeeCode === employeeCode)
    const selectedShift = shifts.find((item) => item.code === shiftCode)
    setEditingAssignment(null)
    setAssignmentForm(getEmptyAssignmentForm(date, selectedStaff, selectedShift))
    setAssignmentDialogOpen(true)
  }

  function openEditAssignment(assignment: ShiftAssignment) {
    setEditingAssignment(assignment)
    setAssignmentForm({
      employeeCode: assignment.employeeCode,
      shiftCode: assignment.shiftCode,
      workDate: assignment.workDate,
      status: assignment.status,
      note: assignment.note ?? "",
    })
    setAssignmentDialogOpen(true)
  }

  async function handleSaveAssignment() {
    if (!assignmentForm.employeeCode || !assignmentForm.shiftCode || !assignmentForm.workDate) {
      toast.error("Vui lòng chọn Staff, ca và ngày làm việc.")
      return
    }
    setIsSavingAssignment(true)
    try {
      if (editingAssignment) {
        await updateShiftAssignment(editingAssignment.publicId, {
          employeeCode: assignmentForm.employeeCode,
          shiftCode: assignmentForm.shiftCode,
          workDate: assignmentForm.workDate,
          status: assignmentForm.status,
          note: assignmentForm.note.trim() || null,
        })
        toast.success("Đã cập nhật phân công.")
      } else {
        await createShiftAssignment({
          employeeCode: assignmentForm.employeeCode,
          shiftCode: assignmentForm.shiftCode,
          workDate: assignmentForm.workDate,
          note: assignmentForm.note.trim() || null,
        })
        toast.success("Đã tạo phân công.")
      }
      setAssignmentDialogOpen(false)
      await loadCalendar()
    } catch (saveError) {
      toast.error(getErrorMessage(saveError, "Không thể lưu phân công."))
    } finally {
      setIsSavingAssignment(false)
    }
  }

  async function handleCancelAssignment() {
    if (!editingAssignment) return
    setIsSavingAssignment(true)
    try {
      await cancelShiftAssignment(editingAssignment.publicId)
      toast.success("Đã chuyển phân công sang trạng thái đã hủy.")
      setAssignmentDialogOpen(false)
      await loadCalendar()
    } catch (cancelError) {
      toast.error(getErrorMessage(cancelError, "Không thể hủy phân công."))
    } finally {
      setIsSavingAssignment(false)
    }
  }

  async function handleCreateShift() {
    const start = shiftForm.startTime
    const end = shiftForm.endTime
    if (!/^[A-Za-z0-9_]+$/.test(shiftForm.code) || !shiftForm.name.trim()) {
      toast.error("Mã và tên ca là bắt buộc; mã chỉ gồm chữ, số và dấu gạch dưới.")
      return
    }
    if (start === end || (shiftForm.crossesMidnight ? end > start : end <= start)) {
      toast.error(
        shiftForm.crossesMidnight
          ? "Ca qua đêm phải có giờ kết thúc nhỏ hơn hoặc bằng giờ bắt đầu."
          : "Ca thường phải có giờ kết thúc sau giờ bắt đầu."
      )
      return
    }
    setIsSavingShift(true)
    try {
      await createShift({ ...shiftForm, code: shiftForm.code.trim().toUpperCase(), name: shiftForm.name.trim() })
      toast.success("Đã tạo ca mới.")
      setShiftDialogOpen(false)
      await loadCalendar()
    } catch (createError) {
      toast.error(getErrorMessage(createError, "Không thể tạo ca."))
    } finally {
      setIsSavingShift(false)
    }
  }

  async function handleDragEnd(event: DragEndEvent) {
    setActiveDrag(null)
    const dragData = event.active.data.current as DragData | undefined
    const targetId = event.over?.id
    if (!dragData || typeof targetId !== "string" || !targetId.startsWith("cell:")) return
    const [employeeCode, workDate] = targetId.slice("cell:".length).split(":")
    if (!employeeCode || !workDate) return

    if (dragData.type === "shift") {
      openCreateAssignment(workDate, employeeCode, dragData.shiftCode)
      return
    }

    const assignment = dragData.assignment
    if (!assignment || (assignment.employeeCode === employeeCode && assignment.workDate === workDate)) return
    try {
      await updateShiftAssignment(assignment.publicId, {
        employeeCode,
        shiftCode: assignment.shiftCode,
        workDate,
        status: assignment.status,
        note: assignment.note,
      })
      toast.success("Đã chuyển phân công.")
      await loadCalendar()
    } catch (moveError) {
      toast.error(getErrorMessage(moveError, "Không thể chuyển phân công. Lịch cũ được giữ nguyên."))
      await loadCalendar()
    }
  }

  if (isAuthLoading || !isAuthenticated) return <ShiftPageSkeleton />

  if (!canManage) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <CalendarDays className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>shift:manage</code> để quản lý ca trực.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={(event) => setActiveDrag(event.active.data.current as DragData)}
      onDragCancel={() => setActiveDrag(null)}
      onDragEnd={(event) => void handleDragEnd(event)}
    >
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold">Quản lý ca trực</h1>
            <p className="text-sm text-[var(--muted-foreground)]">
              Lịch Staff theo tuần · kéo ca vào ô để tạo phân công, kéo phân công để đổi ngày hoặc Staff.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setWeekStart(startOfWeek(new Date(), { weekStartsOn: 1 }))}>
              Hôm nay
            </Button>
            <Button variant="outline" onClick={() => setShiftDialogOpen(true)}>
              <Plus /> Tạo ca
            </Button>
            <Button onClick={() => openCreateAssignment(format(new Date(), "yyyy-MM-dd"))} disabled={staff.length === 0 || activeShifts.length === 0}>
              <Plus /> Phân công
            </Button>
          </div>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertTitle>Không thể tải dữ liệu</AlertTitle>
            <AlertDescription className="flex items-center justify-between gap-4">
              <span>{error}</span>
              <Button variant="outline" size="sm" onClick={() => void loadCalendar()}>
                <RefreshCw /> Thử lại
              </Button>
            </AlertDescription>
          </Alert>
        )}

        <Card>
          <CardHeader className="gap-4 pb-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="flex items-center gap-2"><CalendarDays className="h-5 w-5" /> Lịch tuần</CardTitle>
              <CardDescription>
                {format(weekStart, "dd/MM/yyyy", { locale: vi })} — {format(weekEnd, "dd/MM/yyyy", { locale: vi })}
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon" aria-label="Tuần trước" onClick={() => setWeekStart((date) => addDays(date, -7))}>
                <ChevronLeft />
              </Button>
              <Button variant="outline" size="icon" aria-label="Tuần sau" onClick={() => setWeekStart((date) => addDays(date, 7))}>
                <ChevronRight />
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-[var(--muted)]/30 p-3">
              <span className="mr-2 text-sm font-medium">Ca đang hoạt động:</span>
              {activeShifts.length === 0 ? (
                <span className="text-sm text-[var(--muted-foreground)]">Chưa có ca hoạt động.</span>
              ) : activeShifts.map((shift) => (
                <DraggableShift key={shift.code} shift={shift} onClick={() => openCreateAssignment(format(new Date(), "yyyy-MM-dd"), staff[0]?.employeeCode, shift.code)} />
              ))}
            </div>

            {isLoading ? (
              <CalendarSkeleton />
            ) : staff.length === 0 ? (
              <div className="rounded-lg border border-dashed p-10 text-center">
                <p className="font-medium">Chưa có Staff active</p>
                <p className="mt-1 text-sm text-[var(--muted-foreground)]">Tạo Staff active trước khi lập lịch.</p>
              </div>
            ) : (
              <div className="overflow-x-auto rounded-lg border">
                <table className="w-full min-w-[980px] border-collapse text-sm">
                  <thead>
                    <tr className="bg-[var(--muted)]/40">
                      <th className="sticky left-0 z-10 min-w-52 border-b border-r bg-[var(--muted)]/95 px-4 py-3 text-left font-semibold">Staff</th>
                      {days.map((day) => (
                        <th key={day.toISOString()} className={cn("min-w-28 border-b border-r px-2 py-3 text-center", isToday(day) && "bg-blue-50 text-blue-700")}>
                          <div className="font-semibold">{format(day, "EEE", { locale: vi })}</div>
                          <div className="text-xs font-normal text-[var(--muted-foreground)]">{format(day, "dd/MM")}</div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {staff.map((staffMember) => (
                      <tr key={staffMember.employeeCode}>
                        <th className="sticky left-0 z-10 border-b border-r bg-[var(--card)] px-4 py-3 text-left align-top">
                          <div className="font-semibold">{staffMember.fullName}</div>
                          <div className="mt-1 text-xs text-[var(--muted-foreground)]">{staffMember.employeeCode} · {staffMember.position}</div>
                          {staffMember.department && <div className="text-xs text-[var(--muted-foreground)]">{staffMember.department}</div>}
                        </th>
                        {days.map((day) => {
                          const workDate = format(day, "yyyy-MM-dd")
                          const cellId = getCellId(staffMember.employeeCode, workDate)
                          return (
                            <CalendarCell
                              key={cellId}
                              id={cellId}
                              assignments={assignmentsByCell.get(cellId) ?? []}
                              onEmptyClick={() => openCreateAssignment(workDate, staffMember.employeeCode)}
                              onAssignmentClick={openEditAssignment}
                            />
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="flex flex-wrap gap-4 text-xs text-[var(--muted-foreground)]">
              {(Object.keys(STATUS_LABELS) as AssignmentStatus[]).map((status) => (
                <div key={status} className="flex items-center gap-2"><Badge className={STATUS_CLASSES[status]}>{STATUS_LABELS[status]}</Badge></div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <AssignmentDialog
        open={assignmentDialogOpen}
        onOpenChange={setAssignmentDialogOpen}
        editingAssignment={editingAssignment}
        form={assignmentForm}
        setForm={setAssignmentForm}
        staff={staff}
        shifts={shifts}
        isSaving={isSavingAssignment}
        onSave={() => void handleSaveAssignment()}
        onCancel={() => void handleCancelAssignment()}
      />
      <CreateShiftDialog
        open={shiftDialogOpen}
        onOpenChange={setShiftDialogOpen}
        form={shiftForm}
        setForm={setShiftForm}
        isSaving={isSavingShift}
        onSave={() => void handleCreateShift()}
      />
      <DragOverlay>{activeDrag?.type === "shift" ? <div className="rounded-md bg-[var(--accent)] px-3 py-2 text-sm text-white shadow-lg">{activeDrag.shiftCode}</div> : activeDrag?.assignment ? <div className="rounded-md bg-white px-3 py-2 text-sm shadow-lg">{activeDrag.assignment.shiftName}</div> : null}</DragOverlay>
    </DndContext>
  )
}

function DraggableShift({ shift, onClick }: { shift: Shift; onClick: () => void }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `shift:${shift.code}`,
    data: { type: "shift", shiftCode: shift.code } satisfies DragData,
  })
  return (
    <button ref={setNodeRef} type="button" onClick={onClick} className={cn("flex items-center gap-2 rounded-md border bg-[var(--card)] px-3 py-2 text-left text-xs transition-shadow hover:shadow-sm", isDragging && "opacity-40")} {...listeners} {...attributes} title="Kéo vào ô Staff/ngày để phân công">
      <GripVertical className="h-3.5 w-3.5 text-[var(--muted-foreground)]" />
      <span><strong>{shift.name}</strong><span className="ml-1 text-[var(--muted-foreground)]">{getTimeLabel(shift.startTime)}–{getTimeLabel(shift.endTime)}{shift.crossesMidnight ? " (+1 ngày)" : ""}</span></span>
    </button>
  )
}

function CalendarCell({ id, assignments, onEmptyClick, onAssignmentClick }: { id: string; assignments: ShiftAssignment[]; onEmptyClick: () => void; onAssignmentClick: (assignment: ShiftAssignment) => void }) {
  const { isOver, setNodeRef } = useDroppable({ id })
  return (
    <td ref={setNodeRef} className={cn("border-b border-r p-1 align-top", isOver && "bg-blue-50")}>
      <button type="button" onClick={onEmptyClick} className="mb-1 flex h-5 w-full items-center justify-center rounded text-[var(--muted-foreground)] opacity-0 transition-opacity hover:bg-[var(--muted)]/60 hover:opacity-100 focus:opacity-100" aria-label="Tạo phân công trong ô này"><Plus className="h-3 w-3" /></button>
      <div className="space-y-1">
        {assignments.map((assignment) => <DraggableAssignment key={assignment.publicId} assignment={assignment} onClick={() => onAssignmentClick(assignment)} />)}
      </div>
    </td>
  )
}

function DraggableAssignment({ assignment, onClick }: { assignment: ShiftAssignment; onClick: () => void }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `assignment:${assignment.publicId}`,
    data: { type: "assignment", assignment } satisfies DragData,
  })
  const style = transform ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` } : undefined
  return (
    <button ref={setNodeRef} style={style} type="button" onClick={onClick} className={cn("w-full rounded border px-2 py-1 text-left text-xs shadow-sm transition-opacity", STATUS_CLASSES[assignment.status], isDragging && "opacity-40")} {...listeners} {...attributes} title="Kéo để đổi Staff hoặc ngày">
      <span className="flex items-center justify-between gap-1"><span className="truncate font-semibold">{assignment.shiftName}</span><GripVertical className="h-3 w-3 shrink-0 opacity-60" /></span>
      <span className="mt-0.5 block truncate">{getTimeLabel(assignment.shiftStartAt.slice(11))}–{getTimeLabel(assignment.shiftEndAt.slice(11))}</span>
      <span className="sr-only">{STATUS_LABELS[assignment.status]}</span>
    </button>
  )
}

function AssignmentDialog({ open, onOpenChange, editingAssignment, form, setForm, staff, shifts, isSaving, onSave, onCancel }: { open: boolean; onOpenChange: (open: boolean) => void; editingAssignment: ShiftAssignment | null; form: AssignmentFormState; setForm: React.Dispatch<React.SetStateAction<AssignmentFormState>>; staff: StaffListItem[]; shifts: Shift[]; isSaving: boolean; onSave: () => void; onCancel: () => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>{editingAssignment ? "Chỉnh sửa phân công" : "Phân công Staff"}</DialogTitle><DialogDescription>Chỉ Staff active và ca active mới được chọn. Backend sẽ chặn ca trùng hoặc overlap.</DialogDescription></DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid gap-2"><Label htmlFor="assignment-date">Ngày làm việc</Label><Input id="assignment-date" type="date" value={form.workDate} onChange={(event) => setForm((current) => ({ ...current, workDate: event.target.value }))} /></div>
          <div className="grid gap-2"><Label>Staff active</Label><Select value={form.employeeCode} onValueChange={(value) => setForm((current) => ({ ...current, employeeCode: value }))}><SelectTrigger><SelectValue placeholder="Chọn Staff" /></SelectTrigger><SelectContent>{staff.map((member) => <SelectItem key={member.employeeCode} value={member.employeeCode}>{member.fullName} · {member.employeeCode}</SelectItem>)}</SelectContent></Select></div>
          <div className="grid gap-2"><Label>Ca active</Label><Select value={form.shiftCode} onValueChange={(value) => setForm((current) => ({ ...current, shiftCode: value }))}><SelectTrigger><SelectValue placeholder="Chọn ca" /></SelectTrigger><SelectContent>{shifts.filter((shift) => shift.isActive).map((shift) => <SelectItem key={shift.code} value={shift.code}>{shift.name} · {getTimeLabel(shift.startTime)}–{getTimeLabel(shift.endTime)}</SelectItem>)}</SelectContent></Select></div>
          {editingAssignment && <div className="grid gap-2"><Label>Trạng thái</Label><Select value={form.status} onValueChange={(value: AssignmentStatus) => setForm((current) => ({ ...current, status: value }))}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{(Object.keys(STATUS_LABELS) as AssignmentStatus[]).map((status) => <SelectItem key={status} value={status}>{STATUS_LABELS[status]}</SelectItem>)}</SelectContent></Select></div>}
          <div className="grid gap-2"><Label htmlFor="assignment-note">Ghi chú</Label><Textarea id="assignment-note" value={form.note} maxLength={10000} showCount onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))} placeholder="Ghi chú cho ca trực (không bắt buộc)" /></div>
        </div>
        <DialogFooter>
          {editingAssignment && <Button type="button" variant="destructive" onClick={onCancel} disabled={isSaving}><Trash2 /> Hủy phân công</Button>}
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSaving}>Đóng</Button>
          <Button type="button" onClick={onSave} disabled={isSaving}>{isSaving && <Loader2 className="animate-spin" />} {editingAssignment ? "Lưu thay đổi" : "Tạo phân công"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function CreateShiftDialog({ open, onOpenChange, form, setForm, isSaving, onSave }: { open: boolean; onOpenChange: (open: boolean) => void; form: CreateShiftRequest; setForm: React.Dispatch<React.SetStateAction<CreateShiftRequest>>; isSaving: boolean; onSave: () => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>Tạo ca mới</DialogTitle><DialogDescription>Preset giúp tạo nhanh ca sáng, chiều hoặc ca đêm. Backend vẫn kiểm tra lại giờ ca.</DialogDescription></DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="flex flex-wrap gap-2">{Object.entries(PRESETS).map(([key, preset]) => <Button key={key} type="button" variant={form.code === preset.code ? "default" : "outline"} size="sm" onClick={() => setForm({ ...preset })}>{key}</Button>)}</div>
          <div className="grid gap-2"><Label htmlFor="shift-code">Mã ca</Label><Input id="shift-code" value={form.code} onChange={(event) => setForm((current) => ({ ...current, code: event.target.value.toUpperCase() }))} placeholder="MORNING" /></div>
          <div className="grid gap-2"><Label htmlFor="shift-name">Tên ca</Label><Input id="shift-name" value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} placeholder="Ca sáng" /></div>
          <div className="grid grid-cols-2 gap-3"><div className="grid gap-2"><Label htmlFor="shift-start">Bắt đầu</Label><Input id="shift-start" type="time" value={form.startTime} onChange={(event) => setForm((current) => ({ ...current, startTime: event.target.value }))} /></div><div className="grid gap-2"><Label htmlFor="shift-end">Kết thúc</Label><Input id="shift-end" type="time" value={form.endTime} onChange={(event) => setForm((current) => ({ ...current, endTime: event.target.value }))} /></div></div>
          <label className="flex items-center gap-2 text-sm"><Checkbox checked={form.crossesMidnight} onCheckedChange={(checked) => setForm((current) => ({ ...current, crossesMidnight: checked === true }))} /> Ca qua đêm (kết thúc vào ngày kế tiếp)</label>
        </div>
        <DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSaving}>Đóng</Button><Button type="button" onClick={onSave} disabled={isSaving}>{isSaving && <Loader2 className="animate-spin" />} Tạo ca</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ShiftPageSkeleton() {
  return <div className="space-y-6"><Skeleton className="h-10 w-64" /><Card><CardContent className="p-6"><Skeleton className="h-[28rem] w-full" /></CardContent></Card></div>
}

function CalendarSkeleton() {
  return <div className="space-y-3"><Skeleton className="h-12 w-full" /><Skeleton className="h-20 w-full" /><Skeleton className="h-20 w-full" /><Skeleton className="h-20 w-full" /></div>
}
