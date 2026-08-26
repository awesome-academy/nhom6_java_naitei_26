"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { addDays, format, parseISO, startOfWeek } from "date-fns"
import { vi } from "date-fns/locale"
import { CalendarDays, CheckCircle2, Loader2, RefreshCw, UserX } from "lucide-react"
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Label,
  Skeleton,
  Textarea,
} from "@/components/ui"
import {
  completeOwnShift,
  getOwnShiftAssignments,
  reportOwnAbsence,
} from "@/lib/api/shifts"
import { useAuth } from "@/lib/auth-context"
import type { AssignmentStatus, ShiftAssignment } from "@/types/shift"

const STATUS_LABELS: Record<AssignmentStatus, string> = {
  SCHEDULED: "Đã xếp",
  COMPLETED: "Hoàn thành",
  ABSENT: "Vắng",
  CANCELLED: "Đã hủy",
}

const STATUS_VARIANTS: Record<AssignmentStatus, "confirmed" | "success" | "destructive" | "secondary"> = {
  SCHEDULED: "confirmed",
  COMPLETED: "success",
  ABSENT: "destructive",
  CANCELLED: "secondary",
}

const HOTEL_TIME_ZONE = "Asia/Ho_Chi_Minh"

function getErrorMessage(error: unknown, fallback: string): string {
  const apiError = error as { message?: string }
  return apiError.message || fallback
}

function formatWorkDate(value: string): string {
  return format(parseISO(value), "EEEE, dd/MM", { locale: vi })
}

function formatTime(value: string): string {
  return value.slice(11, 16)
}

function isShiftFinished(assignment: ShiftAssignment): boolean {
  return Date.parse(assignment.shiftEndAt) <= Date.now()
}

function getHotelToday(): Date {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: HOTEL_TIME_ZONE,
    year: "numeric",
    month: "numeric",
    day: "numeric",
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return new Date(Number(values.year), Number(values.month) - 1, Number(values.day))
}

export default function StaffShiftSchedulePage() {
  const { user } = useAuth()
  const currentUser = user
  const [assignments, setAssignments] = useState<ShiftAssignment[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [mutationId, setMutationId] = useState<string | null>(null)
  const [absenceAssignment, setAbsenceAssignment] = useState<ShiftAssignment | null>(null)
  const [absenceNote, setAbsenceNote] = useState("")
  const [absenceError, setAbsenceError] = useState<string | null>(null)

  const range = useMemo(() => {
    const currentWeek = startOfWeek(getHotelToday(), { weekStartsOn: 1 })
    return {
      from: format(currentWeek, "yyyy-MM-dd"),
      to: format(addDays(currentWeek, 13), "yyyy-MM-dd"),
      label: `${format(currentWeek, "dd/MM", { locale: vi })} – ${format(addDays(currentWeek, 13), "dd/MM/yyyy", { locale: vi })}`,
    }
  }, [])

  const loadAssignments = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      setAssignments(await getOwnShiftAssignments(range.from, range.to))
    } catch (error) {
      setLoadError(getErrorMessage(error, "Không thể tải lịch ca. Vui lòng thử lại."))
    } finally {
      setIsLoading(false)
    }
  }, [range.from, range.to])

  useEffect(() => {
    const timer = window.setTimeout(() => void loadAssignments(), 0)
    return () => window.clearTimeout(timer)
  }, [loadAssignments])

  const groupedAssignments = useMemo(() => {
    const groups = new Map<string, ShiftAssignment[]>()
    assignments.forEach((assignment) => {
      const current = groups.get(assignment.workDate) ?? []
      groups.set(assignment.workDate, [...current, assignment])
    })
    return [...groups.entries()].sort(([first], [second]) => first.localeCompare(second))
  }, [assignments])

  async function handleComplete(assignment: ShiftAssignment) {
    setMutationId(assignment.publicId)
    try {
      const updated = await completeOwnShift(assignment.publicId)
      setAssignments((current) => current.map((item) => item.publicId === updated.publicId ? updated : item))
      toast.success("Đã đánh dấu hoàn thành ca")
    } catch (error) {
      toast.error(getErrorMessage(error, "Không thể cập nhật trạng thái ca."))
    } finally {
      setMutationId(null)
    }
  }

  function openAbsenceDialog(assignment: ShiftAssignment) {
    setAbsenceAssignment(assignment)
    setAbsenceNote("")
    setAbsenceError(null)
  }

  async function handleAbsence() {
    if (!absenceAssignment) return
    const note = absenceNote.trim()
    if (!note) {
      setAbsenceError("Vui lòng nhập lý do báo vắng.")
      return
    }

    setMutationId(absenceAssignment.publicId)
    setAbsenceError(null)
    try {
      const updated = await reportOwnAbsence(absenceAssignment.publicId, note)
      setAssignments((current) => current.map((item) => item.publicId === updated.publicId ? updated : item))
      setAbsenceAssignment(null)
      toast.success("Đã báo vắng ca")
    } catch (error) {
      setAbsenceError(getErrorMessage(error, "Không thể báo vắng ca."))
    } finally {
      setMutationId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
        <div>
          <p className="text-sm text-muted-foreground">Xin chào, {currentUser?.fullName}</p>
          <h2 className="mt-1 text-2xl font-semibold tracking-tight text-foreground">Lịch ca của tôi</h2>
          <p className="mt-1 text-sm text-muted-foreground">Lịch làm việc trong tuần hiện tại và tuần kế tiếp</p>
        </div>
        <Button type="button" variant="outline" onClick={() => void loadAssignments()} disabled={isLoading}>
          {isLoading ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <RefreshCw data-icon="inline-start" />}
          Làm mới
        </Button>
      </div>

      {loadError && (
        <Alert variant="destructive">
          <AlertTitle>Không thể tải lịch ca</AlertTitle>
          <AlertDescription className="flex flex-col gap-3">
            <span>{loadError}</span>
            <Button type="button" variant="outline" size="sm" onClick={() => void loadAssignments()} className="w-fit">
              Thử lại
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!loadError && isLoading ? (
        <div className="flex flex-col gap-4">
          {[1, 2, 3].map((item) => (
            <Card key={item}>
              <CardHeader className="gap-2">
                <Skeleton className="h-5 w-44" />
                <Skeleton className="h-4 w-64" />
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                <Skeleton className="h-24 w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : !loadError && groupedAssignments.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
            <CalendarDays className="size-10 text-muted-foreground" />
            <div>
              <p className="font-medium text-foreground">Chưa có ca được phân công</p>
              <p className="mt-1 text-sm text-muted-foreground">Không có lịch ca từ {range.label}.</p>
            </div>
          </CardContent>
        </Card>
      ) : !loadError ? (
        <div className="flex flex-col gap-4">
          {groupedAssignments.map(([workDate, dayAssignments]) => (
            <Card key={workDate}>
              <CardHeader className="gap-1">
                <CardTitle className="text-lg capitalize">{formatWorkDate(workDate)}</CardTitle>
                <CardDescription>{dayAssignments.length} ca được phân công</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 lg:grid-cols-2">
                {dayAssignments.map((assignment) => (
                  <ShiftAssignmentCard
                    key={assignment.publicId}
                    assignment={assignment}
                    isMutating={mutationId === assignment.publicId}
                    canComplete={assignment.status === "SCHEDULED" && isShiftFinished(assignment)}
                    onComplete={() => void handleComplete(assignment)}
                    onReportAbsence={() => openAbsenceDialog(assignment)}
                  />
                ))}
              </CardContent>
            </Card>
          ))}
        </div>
      ) : null}

      <Dialog open={absenceAssignment !== null} onOpenChange={(open) => !open && setAbsenceAssignment(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Báo vắng ca</DialogTitle>
            <DialogDescription>
              {absenceAssignment && `${absenceAssignment.shiftName} · ${formatWorkDate(absenceAssignment.workDate)}`}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="absence-note">Lý do báo vắng</Label>
            <Textarea
              id="absence-note"
              value={absenceNote}
              onChange={(event) => setAbsenceNote(event.target.value)}
              placeholder="Nhập lý do báo vắng..."
              maxLength={10_000}
              showCount
              aria-invalid={absenceError ? true : undefined}
            />
            {absenceError && <p className="text-sm text-destructive">{absenceError}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setAbsenceAssignment(null)} disabled={mutationId !== null}>
              Hủy
            </Button>
            <Button type="button" variant="destructive" onClick={() => void handleAbsence()} disabled={mutationId !== null}>
              {mutationId ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <UserX data-icon="inline-start" />}
              Xác nhận báo vắng
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function ShiftAssignmentCard({
  assignment,
  isMutating,
  canComplete,
  onComplete,
  onReportAbsence,
}: {
  assignment: ShiftAssignment
  isMutating: boolean
  canComplete: boolean
  onComplete: () => void
  onReportAbsence: () => void
}) {
  const isScheduled = assignment.status === "SCHEDULED"

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-border p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold text-foreground">{assignment.shiftName}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {assignment.shiftCode} · {formatTime(assignment.shiftStartAt)} – {formatTime(assignment.shiftEndAt)}
          </p>
        </div>
        <Badge variant={STATUS_VARIANTS[assignment.status]}>{STATUS_LABELS[assignment.status]}</Badge>
      </div>
      {assignment.note && (
        <p className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">Ghi chú: {assignment.note}</p>
      )}
      {isScheduled && (
        <div className="flex flex-wrap gap-2">
          <Button type="button" size="sm" onClick={onComplete} disabled={!canComplete || isMutating} title={!canComplete ? "Chỉ hoàn thành sau khi ca kết thúc" : undefined}>
            {isMutating ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <CheckCircle2 data-icon="inline-start" />}
            Hoàn thành ca
          </Button>
          <Button type="button" size="sm" variant="outline" onClick={onReportAbsence} disabled={isMutating}>
            <UserX data-icon="inline-start" />
            Báo vắng
          </Button>
        </div>
      )}
    </div>
  )
}
