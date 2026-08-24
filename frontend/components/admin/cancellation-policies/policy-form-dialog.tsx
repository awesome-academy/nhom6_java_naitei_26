"use client"

import { useEffect, useMemo, useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2, Plus, Trash2 } from "lucide-react"
import { useFieldArray, useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { Button } from "@/components/ui/button"
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
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import {
  createCancellationPolicy,
  updateCancellationPolicy,
} from "@/lib/api/cancellation-policies"
import type {
  CancellationPolicy,
  CancellationPolicyRule,
} from "@/types/cancellation-policy"

const percentSchema = z
  .string()
  .trim()
  .min(1, "Phần trăm là bắt buộc")
  .regex(/^\d{1,3}(\.\d{1,2})?$/, "Nhập phần trăm từ 0 đến 100")
  .refine((value) => Number(value) <= 100, "Phần trăm không được lớn hơn 100")

const policyFormSchema = z
  .object({
    code: z
      .string()
      .trim()
      .min(1, "Mã policy là bắt buộc")
      .max(30, "Mã policy tối đa 30 ký tự")
      .regex(/^[A-Za-z0-9_-]+$/, "Mã chỉ gồm chữ, số, gạch dưới hoặc gạch ngang"),
    name: z
      .string()
      .trim()
      .min(1, "Tên policy là bắt buộc")
      .max(120, "Tên policy tối đa 120 ký tự"),
    description: z.string().trim().max(10_000, "Mô tả tối đa 10.000 ký tự"),
    noShowChargePercent: percentSchema,
    isDefault: z.boolean(),
    isActive: z.boolean(),
    rules: z
      .array(z.object({
        minHoursBefore: z
          .string()
          .trim()
          .min(1, "Số giờ là bắt buộc")
          .regex(/^\d+$/, "Số giờ phải là số nguyên không âm"),
        refundPercent: percentSchema,
      }))
      .min(1, "Policy phải có ít nhất một bậc hoàn tiền"),
  })
  .superRefine((values, context) => {
    const firstIndexByHours = new Map<number, number>()
    let hasFallbackRule = false
    values.rules.forEach((rule, ruleIndex) => {
      if (!/^\d+$/.test(rule.minHoursBefore.trim())) return
      const hours = Number(rule.minHoursBefore)
      hasFallbackRule ||= hours === 0
      if (!firstIndexByHours.has(hours)) {
        firstIndexByHours.set(hours, ruleIndex)
        return
      }
      context.addIssue({
        code: "custom",
        path: ["rules", ruleIndex, "minHoursBefore"],
        message: `Mốc ${hours} giờ đang bị trùng`,
      })
    })

    if (!hasFallbackRule) {
      context.addIssue({
        code: "custom",
        path: ["rules"],
        message: "Policy phải luôn có bậc 0 giờ",
      })
    }
  })

type PolicyFormValues = z.infer<typeof policyFormSchema>

interface PolicyFormDialogProps {
  open: boolean
  policy: CancellationPolicy | null
  onOpenChange: (open: boolean) => void
  onSaved: (policy: CancellationPolicy) => Promise<void>
}

function getDefaultValues(policy: CancellationPolicy | null): PolicyFormValues {
  if (!policy) {
    return {
      code: "",
      name: "",
      description: "",
      noShowChargePercent: "100",
      isDefault: false,
      isActive: true,
      rules: [{ minHoursBefore: "0", refundPercent: "0" }],
    }
  }

  return {
    code: policy.code,
    name: policy.name,
    description: policy.description ?? "",
    noShowChargePercent: String(policy.noShowChargePercent),
    isDefault: policy.isDefault,
    isActive: policy.isActive,
    rules: [...policy.rules]
      .sort((left, right) => right.minHoursBefore - left.minHoursBefore)
      .map((rule) => ({
        minHoursBefore: String(rule.minHoursBefore),
        refundPercent: String(rule.refundPercent),
      })),
  }
}

function getErrorMessage(error: unknown): string {
  if ((error as { status?: number })?.status === 409) {
    return "Mã cancellation policy đã tồn tại."
  }
  if (error instanceof Error && error.message) return error.message
  return "Không thể lưu cancellation policy. Vui lòng thử lại."
}

function mapRules(rules: PolicyFormValues["rules"]): CancellationPolicyRule[] {
  return rules
    .map((rule) => ({
      minHoursBefore: Number(rule.minHoursBefore),
      refundPercent: Number(rule.refundPercent),
    }))
    .sort((left, right) => right.minHoursBefore - left.minHoursBefore)
}

export function PolicyFormDialog({
  open,
  policy,
  onOpenChange,
  onSaved,
}: PolicyFormDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const isEditMode = policy !== null
  const form = useForm<PolicyFormValues>({
    resolver: zodResolver(policyFormSchema),
    defaultValues: getDefaultValues(policy),
  })
  const rules = useFieldArray({ control: form.control, name: "rules" })
  const watchedRules = useWatch({ control: form.control, name: "rules" })
  const isDefault = useWatch({ control: form.control, name: "isDefault" })
  const isActive = useWatch({ control: form.control, name: "isActive" })

  const ruleListError = useMemo(() => {
    const hours = (watchedRules ?? [])
      .map((rule) => rule.minHoursBefore.trim())
      .filter((value) => /^\d+$/.test(value))
      .map(Number)
    if (!hours.includes(0)) return "Policy phải luôn có bậc 0 giờ"
    if (new Set(hours).size !== hours.length) return "Các mốc giờ không được trùng nhau"
    return null
  }, [watchedRules])

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => form.reset(getDefaultValues(policy)), 0)
    return () => window.clearTimeout(timer)
  }, [form, open, policy])

  async function submit(values: PolicyFormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const commonRequest = {
        name: values.name.trim(),
        description: values.description.trim() || null,
        noShowChargePercent: Number(values.noShowChargePercent),
        isDefault: values.isDefault,
        isActive: values.isActive,
        rules: mapRules(values.rules),
      }
      const savedPolicy = policy
        ? await updateCancellationPolicy(policy.code, commonRequest)
        : await createCancellationPolicy({
            code: values.code.trim().toUpperCase(),
            ...commonRequest,
          })

      await onSaved(savedPolicy)
      toast.success(policy ? "Đã cập nhật chính sách hủy" : "Đã tạo chính sách hủy")
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] max-w-3xl flex-col gap-0 overflow-hidden p-0">
        <DialogHeader className="shrink-0 border-b px-6 py-5">
          <DialogTitle>{isEditMode ? "Chỉnh sửa chính sách hủy" : "Tạo chính sách hủy"}</DialogTitle>
          <DialogDescription>
            Mỗi policy phải có một bậc 0 giờ để hệ thống luôn xác định được mức hoàn tiền.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(submit)} className="flex min-h-0 flex-1 flex-col overflow-hidden">
          <div className="min-h-0 flex-1 space-y-7 overflow-y-auto px-6 py-5">
            <section className="grid gap-5 sm:grid-cols-2">
              <Field label="Mã policy" error={form.formState.errors.code?.message}>
                <Input
                  maxLength={30}
                  disabled={isEditMode}
                  className="uppercase"
                  placeholder="Ví dụ: FLEXIBLE"
                  {...form.register("code")}
                />
              </Field>

              <Field label="Tên policy" error={form.formState.errors.name?.message}>
                <Input maxLength={120} placeholder="Ví dụ: Linh hoạt" {...form.register("name")} />
              </Field>

              <Field
                label="Phí no-show (%)"
                error={form.formState.errors.noShowChargePercent?.message}
              >
                <Input
                  inputMode="decimal"
                  placeholder="100"
                  {...form.register("noShowChargePercent")}
                />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <ToggleField
                  label="Mặc định"
                  description="Ưu tiên khi đặt phòng"
                  checked={isDefault}
                  onCheckedChange={(checked) => form.setValue("isDefault", checked, { shouldDirty: true })}
                />
                <ToggleField
                  label="Hoạt động"
                  description="Cho phép áp dụng policy"
                  checked={isActive}
                  onCheckedChange={(checked) => form.setValue("isActive", checked, { shouldDirty: true })}
                />
              </div>

              <div className="sm:col-span-2">
                <Field label="Mô tả" error={form.formState.errors.description?.message}>
                  <Textarea
                    maxLength={10_000}
                    rows={3}
                    placeholder="Mô tả ngắn về điều kiện hủy..."
                    {...form.register("description")}
                  />
                </Field>
              </div>
            </section>

            <section className="space-y-4 border-t pt-6">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 className="font-semibold">Các bậc hoàn tiền</h3>
                  <p className="text-sm text-[var(--muted-foreground)]">
                    Hệ thống chọn mốc giờ lớn nhất phù hợp với thời điểm khách hủy.
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => rules.append({ minHoursBefore: "", refundPercent: "" })}
                >
                  <Plus className="h-4 w-4" /> Thêm bậc
                </Button>
              </div>

              <div className="space-y-3">
                {rules.fields.map((field, index) => (
                  <div
                    key={field.id}
                    className="grid gap-3 rounded-lg border bg-[var(--muted)]/20 p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-start"
                  >
                    <Field
                      label="Trước ít nhất (giờ)"
                      error={form.formState.errors.rules?.[index]?.minHoursBefore?.message}
                    >
                      <Input
                        inputMode="numeric"
                        placeholder="Ví dụ: 72"
                        {...form.register(`rules.${index}.minHoursBefore`)}
                      />
                    </Field>
                    <Field
                      label="Hoàn lại (%)"
                      error={form.formState.errors.rules?.[index]?.refundPercent?.message}
                    >
                      <Input
                        inputMode="decimal"
                        placeholder="Ví dụ: 100"
                        {...form.register(`rules.${index}.refundPercent`)}
                      />
                    </Field>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="mt-7 text-[var(--destructive)]"
                      aria-label={`Xóa bậc hoàn tiền ${index + 1}`}
                      disabled={rules.fields.length === 1}
                      onClick={() => rules.remove(index)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>

              {ruleListError && (
                <p className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">
                  {ruleListError}
                </p>
              )}
            </section>

            {form.formState.errors.root?.message && (
              <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {form.formState.errors.root.message}
              </p>
            )}
          </div>

          <DialogFooter className="shrink-0 border-t bg-[var(--card)] px-6 py-4">
            <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
              {isEditMode ? "Lưu thay đổi" : "Tạo policy"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-[var(--destructive)]">{error}</p>}
    </div>
  )
}

function ToggleField({
  label,
  description,
  checked,
  onCheckedChange,
}: {
  label: string
  description: string
  checked: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <div className="flex min-h-20 items-center justify-between gap-3 rounded-lg border px-3 py-2">
      <div>
        <Label>{label}</Label>
        <p className="text-xs text-[var(--muted-foreground)]">{description}</p>
      </div>
      <Switch checked={checked} onCheckedChange={onCheckedChange} />
    </div>
  )
}
